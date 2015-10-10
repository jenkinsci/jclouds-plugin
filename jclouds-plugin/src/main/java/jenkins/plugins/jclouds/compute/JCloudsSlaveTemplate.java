package jenkins.plugins.jclouds.compute;

import static shaded.com.google.common.base.Throwables.propagate;
import static shaded.com.google.common.collect.Iterables.getOnlyElement;
import static shaded.com.google.common.collect.Lists.newArrayList;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;
import static java.util.Collections.sort;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.codec.binary.Base64;

import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Describable;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Hudson;
import hudson.model.Node.Mode;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.jclouds.cloudstack.compute.options.CloudStackTemplateOptions;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Location;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.jclouds.predicates.validators.DnsNameValidator;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import au.com.bytecode.opencsv.CSVReader;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.base.Supplier;
import shaded.com.google.common.collect.ImmutableMap;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;

import com.trilead.ssh2.Connection;

import jenkins.plugins.jclouds.internal.CredentialsHelper;
import jenkins.plugins.jclouds.internal.SSHPublicKeyExtractor;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlaveTemplate implements Describable<JCloudsSlaveTemplate>, Supplier<NodeMetadata> {

    private static final Logger LOGGER = Logger.getLogger(JCloudsSlaveTemplate.class.getName());
    private static final char SEPARATOR_CHAR = ',';

    public final String name;
    public final String imageId;
    public final String imageNameRegex;
    public final String hardwareId;
    public final double cores;
    public final int ram;
    public final String osFamily;
    public final String labelString;
    public final String description;
    public final String osVersion;
    public final String locationId;
    public final String initScript;
    public final String userData;
    public final int numExecutors;
    public final boolean stopOnTerminate;
    private transient String vmUser;  // Not used anymore, but retained for backward compatibility.
    private transient String vmPassword; // Not used anymore, but retained for backward compatibility.
    private final String jvmOptions;
    public final boolean preExistingJenkinsUser;
    private transient String jenkinsUser; // Not used anymore, but retained for backward compatibility.
    private final String fsRoot;
    public final boolean allowSudo;
    public final boolean installPrivateKey;
    public Integer overrideRetentionTime;
    public final int spoolDelayMs;
    private final Object delayLockObject = new Object();
    public final boolean assignFloatingIp;
    public final boolean waitPhoneHome;
    public final int waitPhoneHomeTimeout;
    public final String keyPairName;
    public final boolean assignPublicIp;
    public final String networks;
    public final String securityGroups;
    public final Mode mode;
    private String credentialsId;
    private String adminCredentialsId;

    private transient Set<LabelAtom> labelSet;

    protected transient JCloudsCloud cloud;

    public void setCredentialsId(final String value) {
        credentialsId = value;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setAdminCredentialsId(final String value) {
        adminCredentialsId = value;
    }

    public String getAdminCredentialsId() {
        return adminCredentialsId;
    }

    @DataBoundConstructor
    public JCloudsSlaveTemplate(final String name, final String imageId, final String imageNameRegex, final String hardwareId, final double cores,
                                final int ram, final String osFamily, final String osVersion, final String locationId, final String labelString, final String description,
                                final String initScript, final String userData, final int numExecutors, final boolean stopOnTerminate,
                                final String jvmOptions, final boolean preExistingJenkinsUser,
                                final String fsRoot, final boolean allowSudo, final boolean installPrivateKey, final Integer overrideRetentionTime, final int spoolDelayMs,
                                final boolean assignFloatingIp, final boolean waitPhoneHome, final int waitPhoneHomeTimeout, final String keyPairName,
                                final boolean assignPublicIp, final String networks, final String securityGroups, final String credentialsId,
                                final String adminCredentialsId, final String mode) {

        this.name = Util.fixEmptyAndTrim(name);
        this.imageId = Util.fixEmptyAndTrim(imageId);
        this.imageNameRegex = Util.fixEmptyAndTrim(imageNameRegex);
        this.hardwareId = Util.fixEmptyAndTrim(hardwareId);
        this.cores = cores;
        this.ram = ram;
        this.osFamily = Util.fixNull(osFamily);
        this.osVersion = Util.fixNull(osVersion);
        this.locationId = Util.fixEmptyAndTrim(locationId);
        this.labelString = Util.fixNull(labelString);
        this.description = Util.fixNull(description);
        this.initScript = Util.fixNull(initScript);
        this.userData = Util.fixNull(userData);
        this.numExecutors = numExecutors;
        this.jvmOptions = Util.fixEmptyAndTrim(jvmOptions);
        this.stopOnTerminate = stopOnTerminate;

        this.preExistingJenkinsUser = preExistingJenkinsUser;
        this.fsRoot = Util.fixEmptyAndTrim(fsRoot);
        this.allowSudo = allowSudo;
        this.installPrivateKey = installPrivateKey;
        this.overrideRetentionTime = overrideRetentionTime;
        this.spoolDelayMs = spoolDelayMs;
        this.assignFloatingIp = assignFloatingIp;
        this.waitPhoneHome = waitPhoneHome;
        this.waitPhoneHomeTimeout = waitPhoneHomeTimeout;
        this.keyPairName = keyPairName;
        this.assignPublicIp = assignPublicIp;
        this.networks = networks;
        this.securityGroups = securityGroups;
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
        this.adminCredentialsId = Util.fixEmptyAndTrim(adminCredentialsId);
        this.mode = Mode.valueOf(Util.fixNull(mode));
        readResolve();
        this.vmPassword = null; // Not used anymore, but retained for backward compatibility.
        this.vmUser = null; // Not used anymore, but retained for backward compatibility.
    }

    public JCloudsCloud getCloud() {
        return cloud;
    }

    /**
     * Initializes data structure that we don't persist.
     */
    protected Object readResolve() {
        labelSet = Label.parse(labelString);
        return this;
    }

    public String getJenkinsUser() {
        if (!Strings.isNullOrEmpty(jenkinsUser)) {
            return jenkinsUser;
        }
        final StandardUsernameCredentials u = CredentialsHelper.getCredentialsById(credentialsId);
        if (null == u || null == Util.fixEmptyAndTrim(u.getUsername())) {
            return "jenkins";
        } else {
            return u.getUsername();
        }
    }

    public String getJenkinsPrivateKey() {
        if (Strings.isNullOrEmpty(credentialsId)) {
            return getCloud().getGlobalPrivateKey();
        }
        SSHUserPrivateKey supk = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(SSHUserPrivateKey.class, Hudson.getInstance(), ACL.SYSTEM, null),
                CredentialsMatchers.withId(credentialsId));
        if (null != supk) {
            return supk.getPrivateKey();
        }
        return "";
    }

    public String getJenkinsPublicKey() {
        try {
            return SSHPublicKeyExtractor.extract(getJenkinsPrivateKey(), null);
        } catch (IOException e) {
            LOGGER.warning(String.format("Error while extracting public key: %s", e));
        }
        return "";
    }

    public String getAdminUser() {
        if (!Strings.isNullOrEmpty(vmUser)) {
            return vmUser;
        }
        final StandardUsernameCredentials u = CredentialsHelper.getCredentialsById(credentialsId);
        if (null == u || null == Util.fixEmptyAndTrim(u.getUsername())) {
            return "root";
        } else {
            return u.getUsername();
        }
    }

    public String getJvmOptions() {
        if (jvmOptions == null) {
            return "";
        } else {
            return jvmOptions;
        }
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public String getFsRoot() {
        if (fsRoot == null || fsRoot.equals("")) {
            return "/jenkins";
        } else {
            return fsRoot;
        }
    }

    public Set<LabelAtom> getLabelSet() {
        return labelSet;
    }

    public JCloudsSlave provisionSlave(TaskListener listener) throws IOException {
        NodeMetadata nodeMetadata = get();

        try {
            return new JCloudsSlave(getCloud().getDisplayName(), getFsRoot(), nodeMetadata, labelString, description,
                    Integer.toString(numExecutors), stopOnTerminate, overrideRetentionTime, getJvmOptions(), waitPhoneHome,
                    waitPhoneHomeTimeout, credentialsId, mode);
        } catch (Descriptor.FormException e) {
            throw new AssertionError("Invalid configuration " + e.getMessage());
        }
    }

    @Override
    public NodeMetadata get() {
        boolean brokenImageCacheHasThrown = false;
        NodeMetadata nodeMetadata = null;

        do {
            LOGGER.info("Provisioning new jclouds node");
            ImmutableMap<String, String> userMetadata = ImmutableMap.of("Name", name);
            TemplateBuilder templateBuilder = getCloud().getCompute().templateBuilder();
            if (!Strings.isNullOrEmpty(imageId)) {
                LOGGER.info("Setting image id to " + imageId);
                templateBuilder.imageId(imageId);
            } else if (!Strings.isNullOrEmpty(imageNameRegex)) {
                if (brokenImageCacheHasThrown) {
                    LOGGER.info("Resolving image name regex " + imageNameRegex);
                    // We do NOT use templateBuilder.imageNameMatches(imageNameRegex),
                    // because the corresponding image id gets cached for a LOOONG time
                    // and we do not want that. Therefore we always search for images
                    // ourselves and then use the Id of a found image. To work around
                    // caching, we need to do this using a freshly instantiated
                    // ComputeService.
                    // See: https://issues.apache.org/jira/browse/JCLOUDS-570
                    // and: https://issues.apache.org/jira/browse/JCLOUDS-512
                    // for some insight.
                    boolean foundAny = true;
                    for (Image i : getCloud().newCompute().listImages()) {
                        if (i.getName().matches(imageNameRegex)) {
                            LOGGER.info("Setting image id to " + i.getId());
                            templateBuilder.imageId(i.getId());
                            foundAny = true;
                            break;
                        }
                    }
                    if (!foundAny) {
                        throw new RuntimeException("No matching image available");
                    }
                } else {
                    LOGGER.info("Setting image name regex to " + imageNameRegex);
                    templateBuilder.imageNameMatches(imageNameRegex);
                }
            } else {
                if (!Strings.isNullOrEmpty(osFamily)) {
                    LOGGER.info("Setting osFamily to " + osFamily);
                    templateBuilder.osFamily(OsFamily.fromValue(osFamily));
                }
                if (!Strings.isNullOrEmpty(osVersion)) {
                    LOGGER.info("Setting osVersion to " + osVersion);
                    templateBuilder.osVersionMatches(osVersion);
                }
            }
            if (!Strings.isNullOrEmpty(hardwareId)) {
                LOGGER.info("Setting hardware Id to " + hardwareId);
                templateBuilder.hardwareId(hardwareId);
            } else {
                LOGGER.info("Setting minRam " + ram + " and minCores " + cores);
                templateBuilder.minCores(cores).minRam(ram);
            }
            if (!Strings.isNullOrEmpty(locationId)) {
                LOGGER.info("Setting location Id to " + locationId);
                templateBuilder.locationId(locationId);
            }

            Template template = templateBuilder.build();
            TemplateOptions options = template.getOptions();

            if (!Strings.isNullOrEmpty(networks)) {
                LOGGER.info("Setting networks to " + networks);
                options.networks(csvToArray(networks));
            }

            if (!Strings.isNullOrEmpty(securityGroups)) {
                LOGGER.info("Setting security groups to " + securityGroups);
                String[] securityGroupsArray = csvToArray(securityGroups);
                options.securityGroups(securityGroupsArray);

                if (options instanceof NovaTemplateOptions) {
                    options.as(NovaTemplateOptions.class).securityGroupNames(securityGroupsArray);
                }
            }

            if (assignFloatingIp && options instanceof NovaTemplateOptions) {
                LOGGER.info("Setting autoAssignFloatingIp to true");
                options.as(NovaTemplateOptions.class).autoAssignFloatingIp(true);
                options.as(NovaTemplateOptions.class).shouldAutoAssignFloatingIp();
            }

            if (!Strings.isNullOrEmpty(keyPairName) && options instanceof NovaTemplateOptions) {
                LOGGER.info("Setting keyPairName to " + keyPairName);
                options.as(NovaTemplateOptions.class).keyPairName(keyPairName);
            }

            if (options instanceof CloudStackTemplateOptions) {
                /**
                 * This tells jclouds cloudstack module to assign a public ip, setup staticnat and configure the firewall when true. Only interesting when using
                 * cloudstack advanced networking.
                 */
                LOGGER.info("Setting setupStaticNat to " + assignPublicIp);
                options.as(CloudStackTemplateOptions.class).setupStaticNat(assignPublicIp);
            }

            if (null != adminCredentialsId) {
                String adminUser = getAdminUser();
                StandardUsernameCredentials c = CredentialsHelper.getCredentialsById(adminCredentialsId);
                if (null != c) {
                    if (c instanceof StandardUsernamePasswordCredentials) {
                        String password = ((StandardUsernamePasswordCredentials)c).getPassword().toString();
                        LoginCredentials lc = LoginCredentials.builder().user(adminUser).password(password).build();
                        options.overrideLoginCredentials(lc);
                    } else {
                        String privateKey = ((SSHUserPrivateKey)c).getPrivateKey();
                        LoginCredentials lc = LoginCredentials.builder().user(adminUser).privateKey(privateKey).build();
                        options.overrideLoginCredentials(lc);
                    }
                }
            }

            if (spoolDelayMs > 0) {
                // (JENKINS-15970) Add optional delay before spooling. Author: Adam Rofer
                synchronized (delayLockObject) {
                    LOGGER.info("Delaying " + spoolDelayMs + " milliseconds. Current ms -> " + System.currentTimeMillis());
                    try {
                        delayLockObject.wait(spoolDelayMs);
                    } catch (InterruptedException e) {
                        LOGGER.warning(e.getMessage());
                    }
                }
            }

            Statement initStatement = null;

            if (this.preExistingJenkinsUser) {
                if (this.initScript.length() > 0) {
                    initStatement = Statements.exec(this.initScript);
                }
            } else {
                // provision jenkins user
                AdminAccess adminAccess = AdminAccess.builder().adminUsername(getJenkinsUser())
                    .installAdminPrivateKey(installPrivateKey) // some VCS such as Git use SSH authentication
                    .grantSudoToAdminUser(allowSudo) // no need
                    .adminPrivateKey(getJenkinsPrivateKey()) // temporary due to jclouds bug
                    .authorizeAdminPublicKey(true).adminPublicKey(getJenkinsPublicKey()).adminHome(getFsRoot()).build();
                // Jenkins needs /jenkins dir.
                Statement jenkinsDirStatement = newStatementList(Statements.exec("mkdir -p " + getFsRoot()),
                        Statements.exec("chown " + getJenkinsUser() + " " + getFsRoot()));
                initStatement = newStatementList(adminAccess, jenkinsDirStatement, Statements.exec(this.initScript));
            }
            options.inboundPorts(22).userMetadata(userMetadata);

            if (null != initStatement) {
                options.runScript(initStatement);
            }

            if (userData != null) {
                try {
                    Method userDataMethod = options.getClass().getMethod("userData", new byte[0].getClass());
                    LOGGER.info("Setting userData to " + userData);
                    userDataMethod.invoke(options, userData.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "userData is not supported by provider options class " + options.getClass().getName(), e);
                }
            }


            try {
                nodeMetadata = getOnlyElement(getCloud().getCompute().createNodesInGroup(name, 1, template));
                brokenImageCacheHasThrown = false;
            } catch (RunNodesException e) {
                boolean throwNow = true;
                if (!(Strings.isNullOrEmpty(imageNameRegex) || brokenImageCacheHasThrown)) {
                    Map<?, ? extends Throwable> xmap = e.getExecutionErrors();
                    for (Throwable t : xmap.values()) {
                        if (t.getMessage().contains("Image")) {
                            LOGGER.fine("Exception message MATCHED: '" + t.getMessage() + "'");
                            brokenImageCacheHasThrown = true;
                            throwNow = false;
                            destroyBadNodes(e);
                            break;
                        }
                        LOGGER.fine("Exception message NOT MATCHED: '" + t.getMessage() + "'");
                    }
                }
                if (throwNow) {
                    throw destroyBadNodesAndPropagate(e);
                }
            }
        } while (brokenImageCacheHasThrown);

        // Check if nodeMetadata is null and throw
        return nodeMetadata;
    }

    private void destroyBadNodes(RunNodesException e) {
        for (Map.Entry<? extends NodeMetadata, ? extends Throwable> nodeError : e.getNodeErrors().entrySet()) {
            getCloud().getCompute().destroyNode(nodeError.getKey().getId());
        }
    }

    private RuntimeException destroyBadNodesAndPropagate(RunNodesException e) {
        destroyBadNodes(e);
        throw propagate(e);
    }

    private static String[] csvToArray(final String csv) {
        try {
            final CSVReader reader = new CSVReader(new StringReader(csv), SEPARATOR_CHAR);
            final String[] line = reader.readNext();
            return (line != null) ? line : new String[0];
        } catch (Exception e) {
            return new String[0];
        }
    }

    public boolean hasOverrideRetentionTime() {
        return (null != overrideRetentionTime);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<JCloudsSlaveTemplate> getDescriptor() {
        return Jenkins.getActiveInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<JCloudsSlaveTemplate> {

        @Override
        public String getDisplayName() {
            return null;
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            try {
                new DnsNameValidator(1, 80).validate(value);
                return FormValidation.ok();
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public FormValidation doCheckCores(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckRam(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public AutoCompletionCandidates doAutoCompleteOsFamily(@QueryParameter final String value) {
            OsFamily[] osFamilies = OsFamily.values();

            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            for (OsFamily osFamily : osFamilies) {
                if (StringUtils.containsIgnoreCase(osFamily.toString(), value)) {
                    // note: string form of osFamily is lower-hyphen
                    candidates.add(osFamily.toString());
                }
            }
            return candidates;
        }

        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doValidateImageId(@QueryParameter String providerName, @QueryParameter String cloudCredentialsId,
                @QueryParameter String endPointUrl, @QueryParameter String imageId, @QueryParameter String zones) {

            final FormValidation computeContextValidationResult = validateComputeContextParameters(providerName, cloudCredentialsId, endPointUrl, zones);
            if (computeContextValidationResult != null) {
                return computeContextValidationResult;
            }
            if (Strings.isNullOrEmpty(imageId)) {
                return FormValidation.error("Image Id shouldn't be empty");
            }

            // Remove empty text/whitespace from the fields.
            imageId = Util.fixEmptyAndTrim(imageId);

            try {
                final Set<? extends Image> images = listImages(providerName, cloudCredentialsId, endPointUrl, zones);
                if (images != null) {
                    for (final Image image : images) {
                        if (!image.getId().equals(imageId)) {
                            if (image.getId().contains(imageId)) {
                                return FormValidation.warning("Sorry cannot find the image id, " + "Did you mean: " + image.getId() + "?\n" + image);
                            }
                        } else {
                            return FormValidation.ok("Image Id is valid.");
                        }
                    }
                }
            } catch (Exception ex) {
                return FormValidation.error("Unable to check the image id, " + "please check if the credentials you provided are correct.", ex);
            }
            return FormValidation.error("Invalid Image Id, please check the value and try again.");
        }

        public FormValidation doValidateImageNameRegex(@QueryParameter String providerName, @QueryParameter String cloudCredentialsId,
                @QueryParameter String endPointUrl, @QueryParameter String imageNameRegex, @QueryParameter String zones) {

            final FormValidation computeContextValidationResult = validateComputeContextParameters(providerName, cloudCredentialsId, endPointUrl, zones);
            if (computeContextValidationResult != null) {
                return computeContextValidationResult;
            }
            if (Strings.isNullOrEmpty(imageNameRegex)) {
                return FormValidation.error("Image Name Regex should not be empty.");
            }
            // Remove empty text/whitespace from the fields.
            imageNameRegex = Util.fixEmptyAndTrim(imageNameRegex);

            try {
                int matchcount = 0;
                Pattern p = Pattern.compile(imageNameRegex);
                try {
                    final Set<? extends Image> images = listImages(providerName, cloudCredentialsId, endPointUrl, zones);
                    if (images != null) {
                        for (final Image image : images) {
                            if (p.matcher(image.getName()).matches()) {
                                matchcount++;
                            }
                        }
                    } else {
                        return FormValidation.ok("No images available to check against.");
                    }
                } catch (Exception ex) {
                    return FormValidation.error("Unable to check the image name regex, please check if the credentials you provided are correct.", ex);
                }
                if (1 == matchcount) {
                    return FormValidation.ok("Image name regex matches exactly one image.");
                }
                if (1 < matchcount) {
                    return FormValidation.error("Ambiguous image name regex matches multiple images, please check the value and try again.");
                }
            } catch (PatternSyntaxException ex) {
                return FormValidation.error("Invalid image name regex syntax.");
            }
            return FormValidation.error("Image name regex does not match any image, please check the value and try again.");
        }

        private FormValidation validateComputeContextParameters(@QueryParameter String providerName,
                @QueryParameter String cloudCredentialsId, @QueryParameter String endPointUrl, @QueryParameter String zones) {
            if (Strings.isNullOrEmpty(cloudCredentialsId)) {
                return FormValidation.error("No cloud credentials specified.");
            }
            if (Strings.isNullOrEmpty(providerName)) {
                return FormValidation.error("Provider Name shouldn't be empty");
            }

            return null;
        }

        private Set<? extends Image> listImages(String providerName, String cloudCredentialsId, String endPointUrl, String zones) {
            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
            zones = Util.fixEmptyAndTrim(zones);
            ComputeService computeService = null;

            try {
                computeService = JCloudsCloud.ctx(providerName, cloudCredentialsId, endPointUrl, zones).getComputeService();
                return computeService.listImages();
            } finally {
                if (computeService != null) {
                    computeService.getContext().close();
                }
            }
        }

        private boolean prepareListBoxModel(final String providerName, final String cloudCredentialsId,
                final String endPointUrl, final String zones, final ListBoxModel model) {
            if (Strings.isNullOrEmpty(cloudCredentialsId)) {
                LOGGER.warning("cloudCredentialsId is null or empty");
                return true;
            }
            if (Strings.isNullOrEmpty(providerName)) {
                LOGGER.warning("providerName is null or empty");
                return true;
            }
            model.add("None specified", "");
            if (Boolean.getBoolean("underSurefireTest")) {
                // Don't attempt to fetch during HW-Ids GUI testing
                return true;
            }
            return false;
        }

        public ListBoxModel doFillHardwareIdItems(
                @RelativePath("..") @QueryParameter String providerName, @RelativePath("..") @QueryParameter String cloudCredentialsId,
                @RelativePath("..") @QueryParameter String endPointUrl, @RelativePath("..") @QueryParameter String zones) {

            ListBoxModel m = new ListBoxModel();
            if (prepareListBoxModel(providerName, cloudCredentialsId, endPointUrl, zones, m)) {
                return m;
            }
            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);

            ComputeService computeService = null;
            try {
                // TODO: endpoint is ignored
                computeService = JCloudsCloud.ctx(providerName, cloudCredentialsId, endPointUrl, zones).getComputeService();
                ArrayList<Hardware> hws = newArrayList(computeService.listHardwareProfiles());
                sort(hws);
                for (Hardware hardware : hws) {
                    m.add(String.format("%s (%s)", hardware.getId(), hardware.getName()), hardware.getId());
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            } finally {
                if (computeService != null) {
                    computeService.getContext().close();
                }
            }
            return m;
        }

        public FormValidation doValidateHardwareId(@QueryParameter String providerName, @QueryParameter String cloudCredentialsId,
                @QueryParameter String endPointUrl, @QueryParameter String hardwareId, @QueryParameter String zones) {

            if (Strings.isNullOrEmpty(cloudCredentialsId)) {
                return FormValidation.error("No cloud credentials provided.");
            }
            if (Strings.isNullOrEmpty(providerName)) {
                return FormValidation.error("Provider Name should not be empty");
            }
            if (Strings.isNullOrEmpty(hardwareId)) {
                return FormValidation.error("Hardware Id should not be empty");
            }

            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            hardwareId = Util.fixEmptyAndTrim(hardwareId);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
            zones = Util.fixEmptyAndTrim(zones);

            FormValidation result = FormValidation.error("Invalid Hardware Id, please check the value and try again.");
            ComputeService computeService = null;
            try {
                // TODO: endpoint is ignored
                computeService = JCloudsCloud.ctx(providerName, cloudCredentialsId, endPointUrl, zones).getComputeService();
                Set<? extends Hardware> hardwareProfiles = computeService.listHardwareProfiles();
                for (Hardware hardware : hardwareProfiles) {
                    if (!hardware.getId().equals(hardwareId)) {
                        if (hardware.getId().contains(hardwareId)) {
                            return FormValidation.warning("Sorry cannot find the hardware id, " + "Did you mean: " + hardware.getId() + "?\n" + hardware);
                        }
                    } else {
                        return FormValidation.ok("Hardware Id is valid.");
                    }
                }

            } catch (Exception ex) {
                result = FormValidation.error("Unable to check the hardware id, " + "please check if the credentials you provided are correct.", ex);
            } finally {
                if (computeService != null) {
                    computeService.getContext().close();
                }
            }
            return result;
        }

        public ListBoxModel doFillLocationIdItems(
                @RelativePath("..") @QueryParameter String providerName, @RelativePath("..") @QueryParameter String cloudCredentialsId,
                @RelativePath("..") @QueryParameter String endPointUrl, @RelativePath("..") @QueryParameter String zones) {

            ListBoxModel m = new ListBoxModel();
            if (prepareListBoxModel(providerName, cloudCredentialsId, endPointUrl, zones, m)) {
                return m;
            }
            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);

            ComputeService computeService = null;
            try {
                // TODO: endpoint is ignored
                computeService = JCloudsCloud.ctx(providerName, cloudCredentialsId, endPointUrl, zones).getComputeService();

                ArrayList<Location> locations = newArrayList(computeService.listAssignableLocations());
                sort(locations, new Comparator<Location>() {
                    @Override
                    public int compare(Location o1, Location o2) {
                        return o1.getId().compareTo(o2.getId());
                    }
                });

                for (Location location : locations) {
                    m.add(String.format("%s (%s)", location.getId(), location.getDescription()), location.getId());
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            } finally {
                if (computeService != null) {
                    computeService.getContext().close();
                }
            }

            return m;
                }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getActiveInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                        ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
        }

        public ListBoxModel doFillAdminCredentialsIdItems(@AncestorInPath ItemGroup context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getActiveInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel().withEmptySelection().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                        ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
        }

        public FormValidation doValidateLocationId(@QueryParameter String providerName, @QueryParameter String cloudCredentialsId,
                @QueryParameter String endPointUrl, @QueryParameter String locationId, @QueryParameter String zones) {

            if (Strings.isNullOrEmpty(cloudCredentialsId)) {
                return FormValidation.error("No cloud credentials provided.");
            }
            if (Strings.isNullOrEmpty(providerName)) {
                return FormValidation.error("Provider Name shouldn't be empty");
            }

            if (Strings.isNullOrEmpty(locationId)) {
                return FormValidation.ok("No location configured. jclouds automatically will choose one.");
            }

            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            locationId = Util.fixEmptyAndTrim(locationId);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
            zones = Util.fixEmptyAndTrim(zones);

            FormValidation result = FormValidation.error("Invalid Location Id, please check the value and try again.");
            ComputeService computeService = null;
            try {
                // TODO: endpoint is ignored
                computeService = JCloudsCloud.ctx(providerName, cloudCredentialsId, endPointUrl, zones).getComputeService();
                Set<? extends Location> locations = computeService.listAssignableLocations();
                for (Location location : locations) {
                    if (!location.getId().equals(locationId)) {
                        if (location.getId().contains(locationId)) {
                            return FormValidation.warning("Sorry cannot find the location id, " + "Did you mean: " + location.getId() + "?\n" + location);
                        }
                    } else {
                        return FormValidation.ok("Location Id is valid.");
                    }
                }

            } catch (Exception ex) {
                result = FormValidation.error("Unable to check the location id, " + "please check if the credentials you provided are correct.", ex);
            } finally {
                if (computeService != null) {
                    computeService.getContext().close();
                }
            }
            return result;
        }

        public FormValidation doCheckOverrideRetentionTime(@QueryParameter String value) {
            if (Strings.isNullOrEmpty(value)) {
                return FormValidation.ok();
            }
            try {
                if (Integer.parseInt(value) == -1) {
                    return FormValidation.ok();
                }
            } catch (NumberFormatException e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.validateNonNegativeInteger(value);
        }

        public FormValidation doCheckSpoolDelayMs(@QueryParameter String value) {
            return FormValidation.validateNonNegativeInteger(value);
        }
    }

    void upgrade() {
        try {
            final String description = "JClouds cloud " + getCloud().name + "." + name + " - auto-migrated";
            String ju = getJenkinsUser();
            if (Strings.isNullOrEmpty(getCredentialsId()) && !Strings.isNullOrEmpty(ju)) {
                setCredentialsId(convertJenkinsUser(ju, description, getCloud().getGlobalPrivateKey()));
                jenkinsUser = null; // Not used anymore, but retained for backward compatibility.
            }
            if (Strings.isNullOrEmpty(getAdminCredentialsId())) {
                StandardUsernameCredentials u = null;
                String au = getAdminUser();
                if (Strings.isNullOrEmpty(vmPassword)) {
                    // If the username is "root", we use the global key directly,
                    // otherwise create a separate SSHPrivateKey credential
                    if (!au.equals("root")) {
                        String privateKey = getCloud().getGlobalPrivateKey();
                        u = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, null, au,
                                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey), null, description);
                    }
                } else {
                    // Create a Username/Password credential.
                    u = new UsernamePasswordCredentialsImpl(
                            CredentialsScope.SYSTEM, null, description, au, vmPassword);
                }
                try {
                    setAdminCredentialsId(CredentialsHelper.storeCredentials(u));
                } catch (IOException x) {
                    LOGGER.warning(String.format("Error while saving credentials: %s", x.getMessage()));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Creates a new SSH credential for the jenkins user.
     * If a record with the same username and private key already exists, only the id of the existing record is returned.
     * @param user The username.
     * @param cloud The name of the cloud.
     * @param template The name of the slave template.
     * @param privateKey The privateKey.
     * @return The Id of the ssh-credential-plugin record (either newly created or already existing).
     */
    private String convertJenkinsUser(final String user, final String  description, final String privateKey) {
        StandardUsernameCredentials u = retrieveExistingCredentials(user, privateKey);
        if (null == u) {
            u = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, null, user,
                    new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey), null, description);
            try {
                return CredentialsHelper.storeCredentials(u);
            } catch (IOException x) {
                LOGGER.warning(String.format("Error while saving credentials: %s", x.getMessage()));
                return null;
            }
        }
        return u.getId();
    }

    private StandardUsernameCredentials retrieveExistingCredentials(final String username, final String privkey) {
        return CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(SSHUserPrivateKey.class,
                    Hudson.getInstance(), ACL.SYSTEM, SSHLauncher.SSH_SCHEME), CredentialsMatchers.allOf(
                    CredentialsMatchers.withUsername(username),
                    new CredentialsMatcher() {
                        public boolean matches(Credentials item) {
                            for (String key : SSHUserPrivateKey.class.cast(item).getPrivateKeys()) {
                                if (pemKeyEquals(key, privkey)) {
                                    return true;
                                }
                            }
                            return false;
                        }
                    }));
    }

    /**
     * Compares two SSH private keys.
     * There are two levels of comparison: the first is a simple string comparison with all whitespace
     * removed. If that fails then the Base64 decoded bytes of the first PEM entity will be compared
     * (to allow for comments in the key outside the PEM boundaries).
     *
     * @param key1 the first key
     * @param key2 the second key
     * @return {@code true} if they two keys are the same.
     */
    private boolean pemKeyEquals(String key1, String key2) {
        key1 = StringUtils.trim(key1);
        key2 = StringUtils.trim(key2);
        return key1.replaceAll("\\s+", "").equals(key2.replace("\\s+", ""))
            || Arrays.equals(quickNDirtyExtract(key1), quickNDirtyExtract(key2));
    }

    /**
     * Extract the bytes of the first PEM encoded key in a string. This is a quick and dirty method just to
     * establish if two keys are equal, we do not do any serious decoding of the key and this method could give "issues"
     * but should be very unlikely to result in a false positive match.
     *
     * @param key the key to extract.
     * @return the base64 decoded bytes from the key after discarding the key type and any header information.
     */
    private byte[] quickNDirtyExtract(String key) {
        StringBuilder builder = new StringBuilder(key.length());
        boolean begin = false;
        boolean header = false;
        for (String line : StringUtils.split(key, "\n")) {
            line = line.trim();
            if (line.startsWith("---") && line.endsWith("---")) {
                if (begin && line.contains("---END")) {
                    break;
                }
                if (!begin && line.contains("---BEGIN")) {
                    header = true;
                    begin = true;
                    continue;
                }
            }
            if (StringUtils.isBlank(line)) {
                header = false;
                continue;
            }
            if (!header) {
                builder.append(line);
            }
        }
        return Base64.decodeBase64(builder.toString());
    }

}
