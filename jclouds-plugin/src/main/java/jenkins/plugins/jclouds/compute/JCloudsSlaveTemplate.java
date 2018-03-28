/*
 * Copyright 2010-2016 Adrian Cole, Andrew Bayer, Fritz Elfert, Marat Mavlyutov, Monty Taylor, Vijay Kiran et. al.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jenkins.plugins.jclouds.compute;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Lists.newArrayList;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import edu.umd.cs.findbugs.annotations.NonNull;

import org.apache.commons.codec.binary.Base64;

import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Describable;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
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
import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions;
import org.jclouds.cloudstack.compute.options.CloudStackTemplateOptions;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.digitalocean2.compute.options.DigitalOcean2TemplateOptions;
import org.jclouds.digitalocean2.domain.Key;
import org.jclouds.digitalocean2.DigitalOcean2Api;
import org.jclouds.domain.Location;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.googlecomputeengine.compute.options.GoogleComputeEngineTemplateOptions;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.jclouds.predicates.validators.DnsNameValidator;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import au.com.bytecode.opencsv.CSVReader;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;

import com.trilead.ssh2.Connection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import jenkins.plugins.jclouds.internal.CredentialsHelper;
import jenkins.plugins.jclouds.internal.LocationHelper;
import jenkins.plugins.jclouds.internal.SSHPublicKeyExtractor;
import jenkins.plugins.jclouds.config.ConfigHelper;

import edazdarevic.commons.net.CIDRUtils;

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
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private transient String initScript;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private transient String userData;
    public final int numExecutors;
    public final boolean stopOnTerminate;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private transient String vmUser;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private transient String vmPassword;
    private final String jvmOptions;
    public final boolean preExistingJenkinsUser;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private transient String jenkinsUser;
    private final String fsRoot;
    public final boolean allowSudo;
    public final boolean installPrivateKey;
    public final Integer overrideRetentionTime;
    public final int spoolDelayMs;
    private final Object delayLockObject = new Object();
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private transient Boolean assignFloatingIp;
    public final boolean waitPhoneHome;
    public final int waitPhoneHomeTimeout;
    public final String keyPairName;
    public final boolean assignPublicIp;
    public final String networks;
    public final String securityGroups;
    public final Mode mode;
    public final boolean useConfigDrive;
    public final boolean isPreemptible;
    private final String credentialsId;
    private final String adminCredentialsId;
    private final List<UserData> userDataEntries;
    private final String initScriptId;
    private final String preferredAddress;
    private final boolean useJnlp;

    transient JCloudsCloud cloud;
    private transient Set<LabelAtom> labelSet;

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getAdminCredentialsId() {
        return adminCredentialsId;
    }

    public List<UserData> getUserDataEntries() {
        return userDataEntries;
    }

    public String getInitScriptId() {
        return initScriptId;
    }

    public String getPreferredAddress() {
        return preferredAddress;
    }

    public boolean getUseJnlp() {
        return useJnlp;
    }

    @DataBoundConstructor
    public JCloudsSlaveTemplate(final String name, final String imageId, final String imageNameRegex,
            final String hardwareId, final double cores, final int ram, final String osFamily, final String osVersion,
            final String locationId, final String labelString, final String description, final String initScriptId,
            final int numExecutors, final boolean stopOnTerminate, final String jvmOptions,
            final boolean preExistingJenkinsUser, final String fsRoot, final boolean allowSudo,
            final boolean installPrivateKey, final Integer overrideRetentionTime, final int spoolDelayMs,
            final boolean assignFloatingIp, final boolean waitPhoneHome, final int waitPhoneHomeTimeout,
            final String keyPairName, final boolean assignPublicIp, final String networks,
            final String securityGroups, final String credentialsId, final String adminCredentialsId,
            final String mode, final boolean useConfigDrive, final boolean isPreemptible, final List<UserData> userDataEntries,
            final String preferredAddress, final boolean useJnlp) {

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
        this.initScriptId = Util.fixNull(initScriptId);
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
        this.useConfigDrive = useConfigDrive;
        this.isPreemptible = isPreemptible;
        this.userDataEntries = userDataEntries;
        this.preferredAddress = preferredAddress;
        this.useJnlp = useJnlp;
        readResolve();
        this.userData = null; // Not used anymore, but retained for backward compatibility.
        this.vmPassword = null; // Not used anymore, but retained for backward compatibility.
        this.vmUser = null; // Not used anymore, but retained for backward compatibility.
    }

    public JCloudsCloud getCloud() {
        return cloud;
    }

    /**
     * Initializes data structure that we don't persist.
     * @return The initialized object.
     */
    protected Object readResolve() {
        labelSet = Label.parse(labelString);
        return this;
    }

    public String getJenkinsUser() {
        if (!isNullOrEmpty(jenkinsUser)) {
            return jenkinsUser;
        }
        final StandardUsernameCredentials u = CredentialsHelper.getCredentialsById(credentialsId);
        if (null == u || isNullOrEmpty(u.getUsername())) {
            return "jenkins";
        } else {
            return u.getUsername();
        }
    }

    public String getJenkinsPrivateKey() {
        if (isNullOrEmpty(credentialsId)) {
            return getCloud().getGlobalPrivateKey();
        }
        SSHUserPrivateKey supk = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(SSHUserPrivateKey.class, Jenkins.getInstance(), ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId));
        return CredentialsHelper.getPrivateKey(supk);
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
        if (!isNullOrEmpty(vmUser)) {
            return vmUser;
        }
        final StandardUsernameCredentials u = CredentialsHelper.getCredentialsById(adminCredentialsId);
        if (null == u || isNullOrEmpty(u.getUsername())) {
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

    public JCloudsSlave provisionSlave(TaskListener listener, ProvisioningActivity.Id provisioningId) throws IOException {
        NodeMetadata nodeMetadata = get();

        try {
            return new JCloudsSlave(provisioningId , getCloud().getDisplayName(), getFsRoot(), nodeMetadata, labelString, description,
                    Integer.toString(numExecutors), stopOnTerminate, overrideRetentionTime, getJvmOptions(), waitPhoneHome,
                    waitPhoneHomeTimeout, credentialsId, mode, preferredAddress, useJnlp);
        } catch (Descriptor.FormException e) {
            throw new AssertionError("Invalid configuration " + e.getMessage());
        }
    }

    private List<String> getUserDataIds() {
        List<String> ret = new ArrayList<>();
        for (UserData ud : userDataEntries) {
            ret.add(ud.fileId);
        }
        return ret;
    }

    private void setUserData(@NonNull final TemplateOptions options, @Nullable final byte[] udata) {
        if (null != udata) {
            final String sudata = new String(udata, StandardCharsets.UTF_8);
            if (options instanceof GoogleComputeEngineTemplateOptions) {
                LOGGER.finest("Setting userData to " + sudata);
                options.userMetadata("user-data", sudata);
            } else if (options instanceof DigitalOcean2TemplateOptions) {
                LOGGER.finest("Setting userData to " + sudata);
                options.userMetadata("user_data", sudata);
            } else {
                try {
                    Method userDataMethod = options.getClass().getMethod("userData", new byte[0].getClass());
                    LOGGER.finest("Setting userData to " + sudata);
                    userDataMethod.invoke(options, udata);
                } catch (ReflectiveOperationException e) {
                    LOGGER.log(Level.WARNING,
                            "userData is not supported by provider options class " + options.getClass().getName(), e);
                }
            }
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
            if (!isNullOrEmpty(imageId)) {
                LOGGER.info("Setting image id to " + imageId);
                templateBuilder.imageId(imageId);
            } else if (!isNullOrEmpty(imageNameRegex)) {
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
                if (!isNullOrEmpty(osFamily)) {
                    LOGGER.info("Setting osFamily to " + osFamily);
                    templateBuilder.osFamily(OsFamily.fromValue(osFamily));
                }
                if (!isNullOrEmpty(osVersion)) {
                    LOGGER.info("Setting osVersion to " + osVersion);
                    templateBuilder.osVersionMatches(osVersion);
                }
            }
            if (!isNullOrEmpty(hardwareId)) {
                LOGGER.info("Setting hardware Id to " + hardwareId);
                templateBuilder.hardwareId(hardwareId);
            } else {
                LOGGER.info("Setting minRam " + ram + " and minCores " + cores);
                templateBuilder.minCores(cores).minRam(ram);
            }
            if (!isNullOrEmpty(locationId)) {
                LOGGER.info("Setting location Id to " + locationId);
                templateBuilder.locationId(locationId);
            }

            Template template = templateBuilder.build();
            TemplateOptions options = template.getOptions();

            if (!isNullOrEmpty(networks)) {
                LOGGER.info("Setting networks to " + networks);
                options.networks(csvToArray(networks));
            }

            if (!isNullOrEmpty(securityGroups)) {
                LOGGER.info("Setting security groups to " + securityGroups);
                String[] securityGroupsArray = csvToArray(securityGroups);
                options.securityGroups(securityGroupsArray);
            }

            if (useConfigDrive && options instanceof NovaTemplateOptions) {
                options.as(NovaTemplateOptions.class).configDrive(true);
            }

            if (!isNullOrEmpty(keyPairName)) {
                if (options instanceof NovaTemplateOptions) {
                    LOGGER.info("Setting OpenStack keyPairName to: " + keyPairName);
                    options.as(NovaTemplateOptions.class).keyPairName(keyPairName);
                } else if (options instanceof CloudStackTemplateOptions) {
                    LOGGER.info("Setting CloudStack keyPairName to: " + keyPairName);
                    options.as(CloudStackTemplateOptions.class).keyPair(keyPairName);
                } else if (options instanceof AWSEC2TemplateOptions) {
                    LOGGER.info("Setting AWS EC2 keyPairName to: " + keyPairName);
                    options.as(AWSEC2TemplateOptions.class).keyPair(keyPairName);
                } else if (options instanceof DigitalOcean2TemplateOptions) {
                    // DigitalOcean does it different:
                    // The use key Ids (ints) and provide an api for listing them. So we have
                    // to find the named key in the list and use its numeric id.
                    try (DigitalOcean2Api do2api = getCloud().newApi(DigitalOcean2Api.class)) {
                        Optional<Key> key = do2api.keyApi().list().concat().firstMatch(
                                new Predicate<Key>() {
                                    @Override
                                    public boolean apply(final Key k) {
                                        return keyPairName.equals(k.name());
                                    }
                                });
                        if (key.isPresent()) {
                            Key k = key.get();
                            LOGGER.info(String.format("Setting DigitalOcean keyPairName to %s (%d)",
                                        keyPairName, k.id()));
                            List<Integer> kids = new ArrayList<>();
                            kids.add(Integer.valueOf(k.id()));
                            options.as(DigitalOcean2TemplateOptions.class)
                                .sshKeyIds(kids).autoCreateKeyPair(false);
                        } else {
                            LOGGER.warning(String.format("The specified keyPairName %s does not exist",
                                        keyPairName));
                        }
                    } catch (IOException x) {
                        throw new IllegalArgumentException("Could not fetch list of keys", x);
                    }
                }
            }

            if (options instanceof GoogleComputeEngineTemplateOptions) {
                // Always use our own credentials and let creation fail
                // if no keys are provided.
                options.as(GoogleComputeEngineTemplateOptions.class).autoCreateKeyPair(false);
                options.as(GoogleComputeEngineTemplateOptions.class).preemptible(isPreemptible);
            }

            if (assignPublicIp) {
                if (options instanceof NovaTemplateOptions) {
                    LOGGER.info("Setting autoAssignFloatingIp to true");
                    options.as(NovaTemplateOptions.class).autoAssignFloatingIp(true);
                } else if (options instanceof CloudStackTemplateOptions) {
                    LOGGER.info("Setting setupStaticNat to true");
                    options.as(CloudStackTemplateOptions.class).setupStaticNat(assignPublicIp);
                }
            }

            if (null != adminCredentialsId) {
                LOGGER.info("Setting adminCredentialsId to " + adminCredentialsId);
                String adminUser = getAdminUser();
                StandardUsernameCredentials c = CredentialsHelper.getCredentialsById(adminCredentialsId);
                if (null != c) {
                    if (c instanceof StandardUsernamePasswordCredentials) {
                        LOGGER.info("Using username/password as adminCredentials");
                        String password = CredentialsHelper.getPassword(((StandardUsernamePasswordCredentials)c).getPassword());
                        LoginCredentials lc = LoginCredentials.builder().user(adminUser).password(password).build();
                        options.overrideLoginCredentials(lc);
                    } else {
                        LOGGER.info("Using username/privatekey as adminCredentials");
                        String privateKey = CredentialsHelper.getPrivateKey((SSHUserPrivateKey)c);
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

            final String initscript = ConfigHelper.getConfig(initScriptId);
            if (this.preExistingJenkinsUser) {
                if (!initscript.isEmpty()) {
                    initStatement = Statements.exec(initscript);
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
                initStatement = newStatementList(adminAccess, jenkinsDirStatement, Statements.exec(initscript));
            }
            options.inboundPorts(22).userMetadata(userMetadata);

            if (null != initStatement) {
                if (!options.hasLoginPrivateKey()) {
                    LOGGER.info("Init script without private admin key. Falling back to jenkins user credentials");
                    LoginCredentials lc = LoginCredentials.builder()
                        .user(getJenkinsUser()).privateKey(getJenkinsPrivateKey()).build();
                    options.overrideLoginCredentials(lc);
                }
                options.runScript(initStatement);
            }

            if (null != userDataEntries) {
                try {
                    byte[] udata = ConfigHelper.buildUserData(getUserDataIds(), false);
                    if (null != udata && getCloud().allowGzippedUserData()) {
                        byte[] zipped = ConfigHelper.buildUserData(getUserDataIds(), true);
                        if (null != zipped && zipped.length < udata.length) {
                            udata = zipped;
                        }
                    }
                    setUserData(options, udata);
                } catch (IOException x) {
                    LOGGER.log(Level.SEVERE, "Unable to build userData", x);
                }
            }

            try {
                nodeMetadata = getOnlyElement(getCloud().getCompute()
                        .createNodesInGroup(getCloud().prependGroupPrefix(name), 1, template));
                brokenImageCacheHasThrown = false;
            } catch (RunNodesException e) {
                boolean throwNow = true;
                if (!(isNullOrEmpty(imageNameRegex) || brokenImageCacheHasThrown)) {
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
        return null != overrideRetentionTime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<JCloudsSlaveTemplate> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<JCloudsSlaveTemplate> {

        public String getDisplayName() {
            return "JCloudsSlaveTemplate";
        }

        public FormValidation doCheckPreferredAddress(@QueryParameter String value) {
            try {
                if (!isNullOrEmpty(value)) {
                    new CIDRUtils(value);
                }
                return FormValidation.ok();
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
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

        private FormValidation deprecatedSshProvisioning() {
            return FormValidation.warningWithMarkup(
                    "Using SSH-based provisioning is deprecated and will be removed in a future version.<br/>" +
                    "Please use cloud-init for provisioning a jenkins user");
        }

        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String value, @QueryParameter final String useJnlp) {
            if (Boolean.valueOf(Util.fixEmptyAndTrim(useJnlp)).booleanValue()) {
                return FormValidation.ok();
            }
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckAllowSudo(@QueryParameter final String value) {
            if (Boolean.valueOf(Util.fixEmptyAndTrim(value)).booleanValue()) {
                return deprecatedSshProvisioning();
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckInstallPrivateKey(@QueryParameter final String value) {
            if (Boolean.valueOf(Util.fixEmptyAndTrim(value)).booleanValue()) {
                return deprecatedSshProvisioning();
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckPreExistingJenkinsUser(@QueryParameter final String value, @QueryParameter final String useJnlp) {
            if (!Boolean.valueOf(Util.fixEmptyAndTrim(value)).booleanValue()) {
                if (Boolean.valueOf(Util.fixEmptyAndTrim(useJnlp)).booleanValue()) {
                    return FormValidation.error("Jenkins user provisioning relies on posix system, accessible via SSH.");
                }
                return deprecatedSshProvisioning();
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckUseJnlp(@QueryParameter final String value,
                @QueryParameter final String preExistingJenkinsUser, @QueryParameter final String initScriptId) {
            if (Boolean.valueOf(Util.fixEmptyAndTrim(value)).booleanValue()) {
                if (null == Jenkins.getInstance().getTcpSlaveAgentListener() || -1 == Jenkins.getInstance().getSlaveAgentPort()) {
                    return FormValidation.error("This feature cannot work, because the JNLP port is disabled in global security.");
                }
                final Set<String> aps = Jenkins.getInstance().getAgentProtocols();
                if (!(aps.contains("JNLP-connect") || aps.contains("JNLP2-connect") || aps.contains("JNLP3-connect"))) {
                    return FormValidation.error("This feature cannot work, because all JNLP protocols are disabled in global security.");
                }
                if (!Boolean.valueOf(Util.fixEmptyAndTrim(preExistingJenkinsUser)).booleanValue()) {
                    return FormValidation.error("Jenkins user provisioning relies on posix system, accessible via SSH.");
                }
                if (!ConfigHelper.getConfig(initScriptId).isEmpty()) {
                    return FormValidation.error("Init script functionality relies on a posix system, accessible via SSH.");
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckAdminCredentialsId(@QueryParameter final String value) {
            if (isNullOrEmpty(value)) {
                return FormValidation.ok();
            }
            return deprecatedSshProvisioning();
        }

        public FormValidation doCheckInitScriptId(@QueryParameter String value) {
            if (isNullOrEmpty(value)) {
                return FormValidation.ok();
            }
            return deprecatedSshProvisioning();
        }


        public FormValidation doValidateImageId(@QueryParameter String providerName, @QueryParameter String cloudCredentialsId,
                @QueryParameter String endPointUrl, @QueryParameter String imageId, @QueryParameter String zones) {

            final FormValidation res = validateComputeContextParameters(providerName, cloudCredentialsId);
            if (null != res) {
                return res;
            }
            if (isNullOrEmpty(imageId)) {
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

            final FormValidation res = validateComputeContextParameters(providerName, cloudCredentialsId);
            if (null != res) {
                return res;
            }
            // Remove empty text/whitespace from the fields.
            imageNameRegex = Util.fixEmptyAndTrim(imageNameRegex);
            if (isNullOrEmpty(imageNameRegex)) {
                return FormValidation.error("Image Name Regex should not be empty.");
            }

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

        private ComputeServiceContext getCtx(final String provider, final String credId, final String url, final String zones) {
            return JCloudsCloud.ctx(Util.fixEmptyAndTrim(provider), credId, Util.fixEmptyAndTrim(url),
                    Util.fixEmptyAndTrim(zones));
        }

        private FormValidation validateComputeContextParameters(final String provider, final String credId) {
            if (isNullOrEmpty(credId)) {
                return FormValidation.error("No cloud credentials specified.");
            }
            if (isNullOrEmpty(provider)) {
                return FormValidation.error("Provider Name shouldn't be empty");
            }
            return null;
        }

        private Set<? extends Image> listImages(final String provider, final String credId, final String url, final String zones) {
            try (ComputeServiceContext ctx = getCtx(provider, credId, url, zones)) {
                return ctx.getComputeService().listImages();
            }
        }

        private boolean prepareListBoxModel(final String provider, final String credId, final ListBoxModel m) {
            if (isNullOrEmpty(credId)) {
                LOGGER.warning("cloudCredentialsId is null or empty");
                return true;
            }
            if (isNullOrEmpty(provider)) {
                LOGGER.warning("providerName is null or empty");
                return true;
            }
            m.add("None specified", "");
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
            if (prepareListBoxModel(providerName, cloudCredentialsId, m)) {
                return m;
            }
            try (ComputeServiceContext ctx = getCtx(providerName, cloudCredentialsId, endPointUrl, zones)) {
                ArrayList<Hardware> hws = newArrayList(ctx.getComputeService().listHardwareProfiles());
                Collections.sort(hws);
                for (Hardware hardware : hws) {
                    m.add(String.format("%s (%s)", hardware.getId(), hardware.getName()), hardware.getId());
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
            return m;
        }

        public FormValidation doValidateHardwareId(@QueryParameter String providerName, @QueryParameter String cloudCredentialsId,
                @QueryParameter String endPointUrl, @QueryParameter String hardwareId, @QueryParameter String zones) {

            if (isNullOrEmpty(cloudCredentialsId)) {
                return FormValidation.error("No cloud credentials provided.");
            }
            if (isNullOrEmpty(providerName)) {
                return FormValidation.error("Provider Name should not be empty");
            }
            hardwareId = Util.fixEmptyAndTrim(hardwareId);
            if (null == hardwareId) {
                return FormValidation.error("Hardware Id should not be empty");
            }

            FormValidation result = FormValidation.error("Invalid Hardware Id, please check the value and try again.");
            try (ComputeServiceContext ctx = getCtx(providerName, cloudCredentialsId, endPointUrl, zones)) {
                Set<? extends Hardware> hardwareProfiles = ctx.getComputeService().listHardwareProfiles();
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
            }
            return result;
        }

        public ListBoxModel doFillLocationIdItems(
                @RelativePath("..") @QueryParameter String providerName, @RelativePath("..") @QueryParameter String cloudCredentialsId,
                @RelativePath("..") @QueryParameter String endPointUrl, @RelativePath("..") @QueryParameter String zones) {

            ListBoxModel m = new ListBoxModel();
            if (prepareListBoxModel(providerName, cloudCredentialsId, m)) {
                return m;
            }
            try (ComputeServiceContext ctx = getCtx(providerName, cloudCredentialsId, endPointUrl, zones)) {
                LocationHelper.fillLocations(m, ctx.getComputeService().listAssignableLocations());
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }

            return m;
       }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String currentValue) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance()).hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel().includeCurrentValue(currentValue);
            }
            return new StandardUsernameListBoxModel().includeMatchingAs(
                    ACL.SYSTEM, context, StandardUsernameCredentials.class,
                    Collections.<DomainRequirement>singletonList(SSHLauncher.SSH_SCHEME),
                    SSHAuthenticator.matcher(Connection.class)).includeCurrentValue(currentValue);
        }

        public ListBoxModel doFillAdminCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String currentValue) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance()).hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel().includeCurrentValue(currentValue);
            }
            return new StandardUsernameListBoxModel().includeMatchingAs(
                    ACL.SYSTEM, context, StandardUsernameCredentials.class,
                    Collections.<DomainRequirement>singletonList(SSHLauncher.SSH_SCHEME),
                    SSHAuthenticator.matcher(Connection.class)).includeCurrentValue(currentValue);
        }

        @NonNull
        public ListBoxModel doFillInitScriptIdItems(@QueryParameter @Nullable final String currentValue) {
            return ConfigHelper.doFillFileItems(currentValue);
        }

        public FormValidation doValidateLocationId(@QueryParameter String providerName, @QueryParameter String cloudCredentialsId,
                @QueryParameter String endPointUrl, @QueryParameter String locationId, @QueryParameter String zones) {

            if (isNullOrEmpty(cloudCredentialsId)) {
                return FormValidation.error("No cloud credentials provided.");
            }
            if (isNullOrEmpty(providerName)) {
                return FormValidation.error("Provider Name shouldn't be empty");
            }

            if (isNullOrEmpty(locationId)) {
                return FormValidation.ok("No location configured. jclouds automatically will choose one.");
            }

            // Remove empty text/whitespace from the fields.
            locationId = Util.fixEmptyAndTrim(locationId);

            FormValidation result = FormValidation.error("Invalid Location Id, please check the value and try again.");
            try (ComputeServiceContext ctx = getCtx(providerName, cloudCredentialsId, endPointUrl, zones)) {
                Set<? extends Location> locations = ctx.getComputeService().listAssignableLocations();
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
            }
            return result;
        }

        public FormValidation doCheckOverrideRetentionTime(@QueryParameter String value) {
            if (isNullOrEmpty(value)) {
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

    /* Upgrading requires us to set some normally final variables. */
    private void setFinal(final String fieldName, Object newValue) throws ReflectiveOperationException {
        Field field = getClass().getDeclaredField(fieldName);
        if ((field.getModifiers() & Modifier.FINAL) != 0) {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            field.set(this, newValue);
            modifiers.setInt(field, field.getModifiers() | Modifier.FINAL);
            modifiers.setAccessible(false);
        } else {
            field.set(this, newValue);
        }
    }

    @SuppressFBWarnings(value={"REC_CATCH_EXCEPTION","NP_NULL_ON_SOME_PATH"}, justification="false positives")
    boolean upgrade() {
        boolean any = false;
        try {
            if (getCloud().providerName.equals("openstack-nova") && null != assignFloatingIp) {
                LOGGER.info("Upgrading config data: assignFloatingIp");
                setFinal("assignPublicIp", assignFloatingIp);
                assignFloatingIp = null;
                any = true;
            }
            final String description = "JClouds cloud " + getCloud().name + "." + name + " - auto-migrated";
            String ju = getJenkinsUser();
            if (isNullOrEmpty(getCredentialsId()) && !isNullOrEmpty(ju)) {
                LOGGER.info("Upgrading config data: jenkins credentals -> via credentials plugin");
                setFinal("credentialsId", convertJenkinsUser(ju, description, getCloud().getGlobalPrivateKey()));
                jenkinsUser = null; // Not used anymore, but retained for backward compatibility.
                any = true;
            }
            if (isNullOrEmpty(getAdminCredentialsId()) && !isNullOrEmpty(vmUser)) {
                LOGGER.info("Upgrading config data: admin credentals -> via credentials plugin");
                StandardUsernameCredentials u = null;
                String au = getAdminUser();
                if (isNullOrEmpty(vmPassword)) {
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
                    setFinal("adminCredentialsId", CredentialsHelper.storeCredentials(u));
                    any = true;
                } catch (IOException x) {
                    LOGGER.warning(String.format("Error while saving credentials: %s", x.getMessage()));
                }
            }
            if (!isNullOrEmpty(userData)) {
                LOGGER.info("Upgrading config data: userData -> via config-file-provider");
                UserData ud = UserData.createFromData(userData,
                        getCloud().name + "." + name + ".cfg");
                if (null == userDataEntries) {
                    setFinal("userDataEntries", new ArrayList<>());
                    any = true;
                }
                userDataEntries.add(ud);
                userData = null;
            }
            if (!isNullOrEmpty(initScript)) {
                LOGGER.info("Upgrading config data: initScript -> via config-file-provider");
                setFinal("initScriptId", UserData.createFromData(initScript,
                        getCloud().name + "." + name + ".sh").fileId);
                any = true;
                initScript = null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw new IllegalStateException("Could not upgrade config data", e);
        }
        return any;
    }

    /**
     * Creates a new SSH credential for the jenkins user.
     * If a record with the same username and private key already exists, only the id of the existing record is returned.
     * @param user The username.
     * @param privateKey The privateKey.
     * @return The Id of the ssh-credential-plugin record (either newly created or already existing).
     */
    @CheckForNull
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
                    Jenkins.getInstance(), ACL.SYSTEM, SSHLauncher.SSH_SCHEME), CredentialsMatchers.allOf(
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

    private static final boolean isNullOrEmpty(final String value) {
        return null == Util.fixEmptyAndTrim(value);
    }
}
