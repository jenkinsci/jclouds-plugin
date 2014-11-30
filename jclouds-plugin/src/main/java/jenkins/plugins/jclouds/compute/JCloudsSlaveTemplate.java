package jenkins.plugins.jclouds.compute;

import static shaded.com.google.common.base.Throwables.propagate;
import static shaded.com.google.common.collect.Iterables.getOnlyElement;
import static shaded.com.google.common.collect.Lists.newArrayList;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;
import static java.util.Collections.sort;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
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
import org.jclouds.scriptbuilder.statements.java.InstallJDK;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import au.com.bytecode.opencsv.CSVReader;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.base.Supplier;
import shaded.com.google.common.collect.ImmutableMap;

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
    public final String numExecutors;
    public final boolean stopOnTerminate;
    public final String vmUser;
    public final String vmPassword;
    public final boolean preInstalledJava;
    private final String jvmOptions;
    public final boolean preExistingJenkinsUser;
    private final String jenkinsUser;
    private final String fsRoot;
    public final boolean allowSudo;
    public final boolean installPrivateKey;
    public final int overrideRetentionTime;
    public final int spoolDelayMs;
    private final Object delayLockObject = new Object();
    public final boolean assignFloatingIp;
    public final String keyPairName;
    public final boolean assignPublicIp;
    public final String networks;
    public final String securityGroups;

    private transient Set<LabelAtom> labelSet;

    protected transient JCloudsCloud cloud;

    @DataBoundConstructor
    public JCloudsSlaveTemplate(final String name, final String imageId, final String imageNameRegex, final String hardwareId, final double cores,
                                final int ram, final String osFamily, final String osVersion, final String locationId, final String labelString, final String description,
                                final String initScript, final String userData, final String numExecutors, final boolean stopOnTerminate, final String vmPassword, final String vmUser,
                                final boolean preInstalledJava, final String jvmOptions, final String jenkinsUser, final boolean preExistingJenkinsUser, final String fsRoot,
                                final boolean allowSudo, final boolean installPrivateKey, final int overrideRetentionTime, final int spoolDelayMs, final boolean assignFloatingIp,
                                final String keyPairName, final boolean assignPublicIp, final String networks, final String securityGroups) {

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
        this.numExecutors = Util.fixNull(numExecutors);
        this.vmPassword = Util.fixEmptyAndTrim(vmPassword);
        this.vmUser = Util.fixEmptyAndTrim(vmUser);
        this.preInstalledJava = preInstalledJava;
        this.jvmOptions = Util.fixEmptyAndTrim(jvmOptions);
        this.stopOnTerminate = stopOnTerminate;
        this.jenkinsUser = Util.fixEmptyAndTrim(jenkinsUser);
        this.preExistingJenkinsUser = preExistingJenkinsUser;
        this.fsRoot = Util.fixEmptyAndTrim(fsRoot);
        this.allowSudo = allowSudo;
        this.installPrivateKey = installPrivateKey;
        this.overrideRetentionTime = overrideRetentionTime;
        this.spoolDelayMs = spoolDelayMs;
        this.assignFloatingIp = assignFloatingIp;
        this.keyPairName = keyPairName;
        this.assignPublicIp = assignPublicIp;
        this.networks = networks;
        this.securityGroups = securityGroups;
        readResolve();
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
        if (jenkinsUser == null || jenkinsUser.equals("")) {
            return "jenkins";
        } else {
            return jenkinsUser;
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
        return Util.tryParseNumber(numExecutors, 1).intValue();
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
            return new JCloudsSlave(getCloud().getDisplayName(), getFsRoot(), nodeMetadata, labelString, description, numExecutors, stopOnTerminate,
                    overrideRetentionTime, getJvmOptions());
        } catch (Descriptor.FormException e) {
            throw new AssertionError("Invalid configuration " + e.getMessage());
        }
    }

    @Override
    public NodeMetadata get() {
        LOGGER.info("Provisioning new jclouds node");
        ImmutableMap<String, String> userMetadata = ImmutableMap.of("Name", name);
        TemplateBuilder templateBuilder = getCloud().getCompute().templateBuilder();
        if (!Strings.isNullOrEmpty(imageId)) {
            LOGGER.info("Setting image id to " + imageId);
            templateBuilder.imageId(imageId);
        } else if (!Strings.isNullOrEmpty(imageNameRegex)) {
            LOGGER.info("Setting image name regex to " + imageNameRegex);
            templateBuilder.imageNameMatches(imageNameRegex);
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
        if (!Strings.isNullOrEmpty((hardwareId))) {
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

        if (!Strings.isNullOrEmpty(securityGroups) && options instanceof NovaTemplateOptions) {
            LOGGER.info("Setting security groups to " + securityGroups);
            options.as(NovaTemplateOptions.class).securityGroupNames(csvToArray(securityGroups));
        }

        if (assignFloatingIp && options instanceof NovaTemplateOptions) {
            LOGGER.info("Setting autoAssignFloatingIp to true");
            options.as(NovaTemplateOptions.class).autoAssignFloatingIp(true);
        }

        if (!Strings.isNullOrEmpty((keyPairName)) && options instanceof NovaTemplateOptions) {
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

        if (!Strings.isNullOrEmpty(vmPassword)) {
            LoginCredentials lc = LoginCredentials.builder().user(vmUser).password(vmPassword).build();
            options.overrideLoginCredentials(lc);
        } else if (!Strings.isNullOrEmpty(getCloud().privateKey) && !Strings.isNullOrEmpty(vmUser)) {
            // Skip overriding the credentials if we don't have a VM admin user specified - there are cases where we want the private
            // key but we don't to use it for the admin user creds.
            LoginCredentials lc = LoginCredentials.builder().user(vmUser).privateKey(getCloud().privateKey).build();
            options.overrideLoginCredentials(lc);
        }

        if (spoolDelayMs > 0) {
            // (JENKINS-15970) Add optional delay before spooling. Author: Adam Rofer
            synchronized (delayLockObject) {
                LOGGER.info("Delaying " + spoolDelayMs + " milliseconds. Current ms -> " + System.currentTimeMillis());
                try {
                    Thread.sleep(spoolDelayMs);
                } catch (InterruptedException e) {
                }
            }
        }

        Statement initStatement = null;
        Statement bootstrap = null;

        if (this.preExistingJenkinsUser) {
            if (this.initScript.length() > 0) {
                initStatement = Statements.exec(this.initScript);
            }
        } else {
            // setup the jcloudTemplate to customize the nodeMetadata with jdk, etc. also opening ports
            AdminAccess adminAccess = AdminAccess.builder().adminUsername(getJenkinsUser())
                    .installAdminPrivateKey(installPrivateKey) // some VCS such as Git use SSH authentication
                    .grantSudoToAdminUser(allowSudo) // no need
                    .adminPrivateKey(getCloud().privateKey) // temporary due to jclouds bug
                    .authorizeAdminPublicKey(true).adminPublicKey(getCloud().publicKey).adminHome(getFsRoot()).build();

            // Jenkins needs /jenkins dir.
            Statement jenkinsDirStatement = Statements.newStatementList(Statements.exec("mkdir -p " + getFsRoot()),
                    Statements.exec("chown " + getJenkinsUser() + " " + getFsRoot()));

            initStatement = newStatementList(adminAccess, jenkinsDirStatement, Statements.exec(this.initScript));
        }

        if (preInstalledJava) {
            bootstrap = initStatement;
        } else {
            bootstrap = newStatementList(initStatement, InstallJDK.fromOpenJDK());
        }

        options.inboundPorts(22).userMetadata(userMetadata);

        if (bootstrap != null) {
            options.runScript(bootstrap);
        }

        if (userData != null) {
            try {
                Method userDataMethod = options.getClass().getMethod("userData", new byte[0].getClass());
                LOGGER.info("Setting userData to " + userData);
                userDataMethod.invoke(options, userData.getBytes());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "userData is not supported by provider options class " + options.getClass().getName(), e);
            }
        }

        NodeMetadata nodeMetadata = null;

        try {
            nodeMetadata = getOnlyElement(getCloud().getCompute().createNodesInGroup(name, 1, template));
        } catch (RunNodesException e) {
            throw destroyBadNodesAndPropagate(e);
        }

        // Check if nodeMetadata is null and throw
        return nodeMetadata;
    }

    private RuntimeException destroyBadNodesAndPropagate(RunNodesException e) {
        for (Map.Entry<? extends NodeMetadata, ? extends Throwable> nodeError : e.getNodeErrors().entrySet()) {
            getCloud().getCompute().destroyNode(nodeError.getKey().getId());
        }
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

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<JCloudsSlaveTemplate> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
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

        public FormValidation doValidateImageId(@QueryParameter String providerName, @QueryParameter String identity, @QueryParameter String credential,
                                                @QueryParameter String endPointUrl, @QueryParameter String imageId, @QueryParameter String zones) {

            final FormValidation computeContextValidationResult = validateComputeContextParameters(providerName, identity, credential, endPointUrl, zones);
            if (computeContextValidationResult != null) {
                return computeContextValidationResult;
            }
            if (Strings.isNullOrEmpty(imageId)) {
                return FormValidation.error("Image Id shouldn't be empty");
            }

            // Remove empty text/whitespace from the fields.
            imageId = Util.fixEmptyAndTrim(imageId);

            try {
                final Set<? extends Image> images = listImages(providerName, identity, Secret.fromString(credential).getPlainText(), endPointUrl, zones);
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

        public FormValidation doValidateImageNameRegex(@QueryParameter String providerName, @QueryParameter String identity, @QueryParameter String credential,
                                                       @QueryParameter String endPointUrl, @QueryParameter String imageNameRegex, @QueryParameter String zones) {

            final FormValidation computeContextValidationResult = validateComputeContextParameters(providerName, identity, Secret.fromString(credential).getPlainText(), endPointUrl, zones);
            if (computeContextValidationResult != null) {
                return computeContextValidationResult;
            }
            if (Strings.isNullOrEmpty(imageNameRegex)) {
                return FormValidation.error("Image Name Regex shouldn't be empty");
            }

            // Remove empty text/whitespace from the fields.
            imageNameRegex = Util.fixEmptyAndTrim(imageNameRegex);

            try {
                final Set<? extends Image> images = listImages(providerName, identity, Secret.fromString(credential).getPlainText(), endPointUrl, zones);
                if (images != null) {
                    for (final Image image : images) {
                        if (image.getName().matches(imageNameRegex)) {
                            return FormValidation.ok("Image Name Regex is valid.");
                        }
                    }
                }
            } catch (Exception ex) {
                return FormValidation.error("Unable to check the image name regex, " + "please check if the credentials you provided are correct.", ex);
            }
            return FormValidation.error("Invalid Image Name Regex, please check the value and try again.");
        }

        private FormValidation validateComputeContextParameters(@QueryParameter String providerName, @QueryParameter String identity,
                                                                @QueryParameter String credential, @QueryParameter String endPointUrl, @QueryParameter String zones) {
            if (Strings.isNullOrEmpty(identity)) {
                return FormValidation.error("Invalid identity (AccessId).");
            }
            if (Strings.isNullOrEmpty(credential)) {
                return FormValidation.error("Invalid credential (secret key).");
            }
            if (Strings.isNullOrEmpty(providerName)) {
                return FormValidation.error("Provider Name shouldn't be empty");
            }

            return null;
        }

        private Set<? extends Image> listImages(String providerName, String identity, String credential, String endPointUrl, String zones) {
            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            identity = Util.fixEmptyAndTrim(identity);
            credential = Secret.fromString(credential).getPlainText();
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
            zones = Util.fixEmptyAndTrim(zones);
            ComputeService computeService = null;

            try {
                computeService = JCloudsCloud.ctx(providerName, identity, credential, endPointUrl, zones).getComputeService();
                return computeService.listImages();
            } finally {
                if (computeService != null) {
                    computeService.getContext().close();
                }
            }
        }

        public ListBoxModel doFillHardwareIdItems(@RelativePath("..") @QueryParameter String providerName, @RelativePath("..") @QueryParameter String identity,
                                                  @RelativePath("..") @QueryParameter String credential, @RelativePath("..") @QueryParameter String endPointUrl,
                                                  @RelativePath("..") @QueryParameter String zones) {

            ListBoxModel m = new ListBoxModel();

            if (Strings.isNullOrEmpty(identity)) {
                LOGGER.warning("identity is null or empty");
                return m;
            }
            if (Strings.isNullOrEmpty(credential)) {
                LOGGER.warning("credential is null or empty");
                return m;
            }
            if (Strings.isNullOrEmpty(providerName)) {
                LOGGER.warning("providerName is null or empty");
                return m;
            }

            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            identity = Util.fixEmptyAndTrim(identity);
            credential = Secret.fromString(credential).getPlainText();
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);

            ComputeService computeService = null;
            m.add("None specified", "");
            try {
                // TODO: endpoint is ignored
                computeService = JCloudsCloud.ctx(providerName, identity, credential, endPointUrl, zones).getComputeService();

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

        public FormValidation doValidateHardwareId(@QueryParameter String providerName, @QueryParameter String identity, @QueryParameter String credential,
                                                   @QueryParameter String endPointUrl, @QueryParameter String hardwareId, @QueryParameter String zones) {

            if (Strings.isNullOrEmpty(identity)) {
                return FormValidation.error("Invalid identity (AccessId).");
            }
            if (Strings.isNullOrEmpty(credential)) {
                return FormValidation.error("Invalid credential (secret key).");
            }
            if (Strings.isNullOrEmpty(providerName)) {
                return FormValidation.error("Provider Name shouldn't be empty");
            }
            if (Strings.isNullOrEmpty(hardwareId)) {
                return FormValidation.error("Hardware Id shouldn't be empty");
            }

            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            identity = Util.fixEmptyAndTrim(identity);
            credential = Secret.fromString(credential).getPlainText();
            hardwareId = Util.fixEmptyAndTrim(hardwareId);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
            zones = Util.fixEmptyAndTrim(zones);

            FormValidation result = FormValidation.error("Invalid Hardware Id, please check the value and try again.");
            ComputeService computeService = null;
            try {
                // TODO: endpoint is ignored
                computeService = JCloudsCloud.ctx(providerName, identity, credential, endPointUrl, zones).getComputeService();
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

        public ListBoxModel doFillLocationIdItems(@RelativePath("..") @QueryParameter String providerName, @RelativePath("..") @QueryParameter String identity,
                                                  @RelativePath("..") @QueryParameter String credential, @RelativePath("..") @QueryParameter String endPointUrl,
                                                  @RelativePath("..") @QueryParameter String zones) {

            ListBoxModel m = new ListBoxModel();

            if (Strings.isNullOrEmpty(identity)) {
                LOGGER.warning("identity is null or empty");
                return m;
            }
            if (Strings.isNullOrEmpty(credential)) {
                LOGGER.warning("credential is null or empty");
                return m;
            }
            if (Strings.isNullOrEmpty(providerName)) {
                LOGGER.warning("providerName is null or empty");
                return m;
            }

            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            identity = Util.fixEmptyAndTrim(identity);
            credential = Secret.fromString(credential).getPlainText();
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);

            ComputeService computeService = null;
            m.add("None specified", "");
            try {
                // TODO: endpoint is ignored
                computeService = JCloudsCloud.ctx(providerName, identity, credential, endPointUrl, zones).getComputeService();

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

        public FormValidation doValidateLocationId(@QueryParameter String providerName, @QueryParameter String identity, @QueryParameter String credential,
                                                   @QueryParameter String endPointUrl, @QueryParameter String locationId, @QueryParameter String zones) {

            if (Strings.isNullOrEmpty(identity)) {
                return FormValidation.error("Invalid identity (AccessId).");
            }
            if (Strings.isNullOrEmpty(credential)) {
                return FormValidation.error("Invalid credential (secret key).");
            }
            if (Strings.isNullOrEmpty(providerName)) {
                return FormValidation.error("Provider Name shouldn't be empty");
            }

            if (Strings.isNullOrEmpty(locationId)) {
                return FormValidation.ok("No location configured. jclouds automatically will choose one.");
            }

            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            identity = Util.fixEmptyAndTrim(identity);
            credential = Util.fixEmptyAndTrim(credential);
            locationId = Util.fixEmptyAndTrim(locationId);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
            zones = Util.fixEmptyAndTrim(zones);

            FormValidation result = FormValidation.error("Invalid Location Id, please check the value and try again.");
            ComputeService computeService = null;
            try {
                // TODO: endpoint is ignored
                computeService = JCloudsCloud.ctx(providerName, identity, credential, endPointUrl, zones).getComputeService();
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
            try {
                if (Integer.parseInt(value) == -1) {
                    return FormValidation.ok();
                }
            } catch (NumberFormatException e) {
            }
            return FormValidation.validateNonNegativeInteger(value);
        }

        public FormValidation doCheckSpoolDelayMs(@QueryParameter String value) {
            return FormValidation.validateNonNegativeInteger(value);
        }
    }
}
