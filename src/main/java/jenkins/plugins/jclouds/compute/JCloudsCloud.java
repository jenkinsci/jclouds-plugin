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

import static org.jclouds.reflect.Reflection2.typeToken;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.inject.Module;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.RelativePath;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.ComboBoxModel;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;
import hudson.util.XStream2;
import jakarta.servlet.ServletException;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.cli.CliMessages;
import jenkins.plugins.jclouds.compute.internal.RunningNode;
import jenkins.plugins.jclouds.internal.CredentialsHelper;
import jenkins.plugins.jclouds.internal.LocationHelper;
import jenkins.plugins.jclouds.internal.SSHPublicKeyExtractor;
import jenkins.plugins.jclouds.modules.JenkinsConfigurationModule;
import net.sf.json.JSONObject;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.apis.Apis;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.config.ComputeServiceProperties;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Location;
import org.jclouds.location.reference.LocationConstants;
import org.jclouds.logging.jdk.config.JDKLoggingModule;
import org.jclouds.providers.Providers;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

/**
 * The JClouds version of the Jenkins Cloud.
 *
 * @author Vijay Kiran
 */
public class JCloudsCloud extends Cloud {

    static final Logger LOGGER = Logger.getLogger(JCloudsCloud.class.getName());

    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    @Deprecated
    private final transient String identity;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    @Deprecated
    private final transient Secret credential;

    public final String providerName;

    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    @Deprecated
    private final transient String privateKey;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    @Deprecated
    private final transient String publicKey; // NOPMD - unused private member

    private transient int pendingNodes;

    public final String endPointUrl;
    public final String profile;
    private final int retentionTime;
    private final int errorRetentionTime;
    public int instanceCap;
    private CopyOnWriteArrayList<JCloudsSlaveTemplate> templates;
    public final int scriptTimeout;
    public final int startTimeout;
    private transient ComputeService compute;
    public final String zones;

    private String cloudGlobalKeyId;
    private String cloudCredentialsId;
    private String groupPrefix;
    private final boolean trustAll;
    private transient List<PhoneHomeMonitor> phms;

    static List<String> getCloudNames() {
        List<String> cloudNames = new ArrayList<String>();
        for (Cloud c : Jenkins.get().clouds) {
            if (JCloudsCloud.class.isInstance(c)) {
                cloudNames.add(c.name);
            }
        }
        return cloudNames;
    }

    static JCloudsCloud getByName(String name) {
        return (JCloudsCloud) Jenkins.get().clouds.getByName(name);
    }

    public String getName() {
        return name;
    }

    public String getCloudCredentialsId() {
        return cloudCredentialsId;
    }

    public boolean getTrustAll() {
        return trustAll;
    }

    public void setCloudCredentialsId(final String value) {
        cloudCredentialsId = value;
    }

    public String getCloudGlobalKeyId() {
        return cloudGlobalKeyId;
    }

    public void setCloudGlobalKeyId(final String value) {
        cloudGlobalKeyId = value;
    }

    public String getGlobalPrivateKey() {
        return getPrivateKeyFromCredential(cloudGlobalKeyId);
    }

    public String getGlobalPublicKey() {
        return getPublicKeyFromCredential(cloudGlobalKeyId);
    }

    public String getGroupPrefix() {
        return groupPrefix;
    }

    String prependGroupPrefix(final String name) {
        if (null == name) {
            return null;
        }
        String tmp = Util.fixEmptyAndTrim(groupPrefix);
        return tmp == null ? name : tmp + "-" + name;
    }

    private String removeGroupPrefix(final String name) {
        if (null == name) {
            return null;
        }
        String tmp = Util.fixEmptyAndTrim(groupPrefix);
        if (null == tmp) {
            return name;
        }
        tmp = tmp.concat("-");
        return name.startsWith(tmp) ? name.substring(tmp.length()) : name;
    }

    private String getPrivateKeyFromCredential(final String id) {
        if (!isNullOrEmpty(id)) {
            SSHUserPrivateKey supk = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItemGroup(SSHUserPrivateKey.class, null, null),
                    CredentialsMatchers.withId(id));
            if (null != supk) {
                return CredentialsHelper.getPrivateKey(supk);
            }
        }
        return "";
    }

    private String getPublicKeyFromCredential(final String id) {
        if (!isNullOrEmpty(id)) {
            try {
                return SSHPublicKeyExtractor.extract(getPrivateKeyFromCredential(id), null);
            } catch (IOException e) {
                LOGGER.warning(String.format("Error while extracting public key: %s", e));
            }
        }
        return "";
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

    boolean isUserDataSupported() {
        // GCE uses meta_data['user-data']
        if ("google-compute-engine".equals(providerName)) {
            return true;
        }
        // Temporary hack for digitalocean2
        if ("digitalocean2".equals(providerName)) {
            return true;
        }
        try (ComputeServiceContext ctx = ctx(providerName, cloudCredentialsId, endPointUrl, zones, trustAll)) {
            TemplateOptions o = ctx.getComputeService().templateOptions();
            o.getClass().getMethod("userData", new byte[0].getClass());
        } catch (ReflectiveOperationException x) {
            return false;
        }
        return true;
    }

    // Delegated here from JCloudsSlaveTemplate
    FormValidation validateLocationId(String locationId) {
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
        try (ComputeServiceContext ctx = ctx(providerName, cloudCredentialsId, endPointUrl, zones, trustAll)) {
            Set<? extends Location> locations = ctx.getComputeService().listAssignableLocations();
            for (Location location : locations) {
                if (!location.getId().equals(locationId)) {
                    if (location.getId().contains(locationId)) {
                        return FormValidation.warning("Sorry cannot find the location id, " + "Did you mean: "
                                + location.getId() + "?\n" + location);
                    }
                } else {
                    return FormValidation.ok("Location Id is valid.");
                }
            }
        } catch (Exception ex) {
            result = FormValidation.error(
                    "Unable to check the location id, " + "please check if the credentials you provided are correct.",
                    ex);
        }
        return result;
    }

    // Delegated here from JCloudsSlaveTemplate
    FormValidation validateImageId(String imageId) {
        final FormValidation res = validateComputeContextParameters(providerName, cloudCredentialsId);
        if (null != res) {
            return res;
        }
        if (isNullOrEmpty(imageId)) {
            return FormValidation.error("Image Id shouldn't be empty");
        }
        // Remove empty text/whitespace from the fields.
        imageId = Util.fixEmptyAndTrim(imageId);
        try (ComputeServiceContext ctx = ctx(providerName, cloudCredentialsId, endPointUrl, zones, trustAll)) {
            final Set<? extends Image> images = ctx.getComputeService().listImages();
            if (images != null) {
                for (final Image image : images) {
                    if (!image.getId().equals(imageId)) {
                        if (image.getId().contains(imageId)) {
                            return FormValidation.warning("Sorry cannot find the image id, " + "Did you mean: "
                                    + image.getId() + "?\n" + image);
                        }
                    } else {
                        return FormValidation.ok("Image Id is valid.");
                    }
                }
            }
        } catch (Exception ex) {
            return FormValidation.error(
                    "Unable to check the image id, " + "please check if the clouds credentials.", ex);
        }
        return FormValidation.error("Invalid Image Id, please check the value and try again.");
    }

    // Delegated here from JCloudsSlaveTemplate
    FormValidation validateHardwareId(String hardwareId) {
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
        try (ComputeServiceContext ctx = ctx(providerName, cloudCredentialsId, endPointUrl, zones, trustAll)) {
            Set<? extends Hardware> hardwareProfiles = ctx.getComputeService().listHardwareProfiles();
            for (Hardware hardware : hardwareProfiles) {
                if (!hardware.getId().equals(hardwareId)) {
                    if (hardware.getId().contains(hardwareId)) {
                        return FormValidation.warning("Sorry cannot find the hardware id, " + "Did you mean: "
                                + hardware.getId() + "?\n" + hardware);
                    }
                } else {
                    return FormValidation.ok("Hardware Id is valid.");
                }
            }
        } catch (Exception ex) {
            result = FormValidation.error(
                    "Unable to check the hardware id, " + "please check if the clouds credentials.", ex);
        }
        return result;
    }

    // Delegated here from JCloudsSlaveTemplate
    FormValidation validateImageNameRegex(String imageNameRegex) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        final FormValidation res = validateComputeContextParameters(providerName, cloudCredentialsId);
        if (null != res) {
            return res;
        }
        // Remove empty text/whitespace from the fields.
        imageNameRegex = Util.fixEmptyAndTrim(imageNameRegex);
        if (isNullOrEmpty(imageNameRegex)) {
            return FormValidation.error("Image Name Regex should not be empty.");
        }
        try (ComputeServiceContext ctx = ctx(providerName, cloudCredentialsId, endPointUrl, zones, trustAll)) {
            int matchcount = 0;
            Pattern p = Pattern.compile(imageNameRegex);
            try {
                final Set<? extends Image> images = ctx.getComputeService().listImages();
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
                return FormValidation.error(
                        "Unable to check the image name regex, please check if the credentials you provided are correct.",
                        ex);
            }
            if (1 == matchcount) {
                return FormValidation.ok("Image name regex matches exactly one image.");
            }
            if (1 < matchcount) {
                return FormValidation.error(
                        "Ambiguous image name regex matches multiple images, please check the value and try again.");
            }
        } catch (PatternSyntaxException ex) {
            return FormValidation.error("Invalid image name regex syntax.");
        }
        return FormValidation.error("Image name regex does not match any image, please check the value and try again.");
    }

    // Delegated here from JCloudsSlaveTemplate
    void fillHardwareIdItems(ListBoxModel m) {
        try (ComputeServiceContext ctx = ctx(providerName, cloudCredentialsId, endPointUrl, zones, trustAll)) {
            List<Hardware> hws = new ArrayList<>(ctx.getComputeService().listHardwareProfiles());
            Collections.sort(hws);
            for (Hardware hardware : hws) {
                m.add(String.format("%s (%s)", hardware.getId(), hardware.getName()), hardware.getId());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    // Delegated here from JCloudsSlaveTemplate
    void fillLocationIdItems(ListBoxModel m) {
        try (ComputeServiceContext ctx = ctx(providerName, cloudCredentialsId, endPointUrl, zones, trustAll)) {
            LocationHelper.fillLocations(m, ctx.getComputeService().listAssignableLocations());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @DataBoundConstructor
    public JCloudsCloud(
            final String profile,
            final String providerName,
            final String cloudCredentialsId,
            final String cloudGlobalKeyId,
            final String endPointUrl,
            final int instanceCap,
            final int retentionTime,
            final int errorRetentionTime,
            final int scriptTimeout,
            final int startTimeout,
            final String zones,
            final String groupPrefix,
            final boolean trustAll,
            final List<JCloudsSlaveTemplate> templates) {
        super(Util.fixEmptyAndTrim(profile));
        this.profile = Util.fixEmptyAndTrim(profile);
        this.providerName = Util.fixEmptyAndTrim(providerName);
        this.identity = null; // Not used anymore, but retained for backward compatibility.
        this.credential = null; // Not used anymore, but retained for backward compatibility.
        this.privateKey = null; // Not used anymore, but retained for backward compatibility.
        this.publicKey = null; // Not used anymore, but retained for backward compatibility.
        this.cloudGlobalKeyId = Util.fixEmptyAndTrim(cloudGlobalKeyId);
        this.cloudCredentialsId = Util.fixEmptyAndTrim(cloudCredentialsId);
        this.endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
        this.instanceCap = instanceCap;
        this.retentionTime = retentionTime;
        this.errorRetentionTime = errorRetentionTime;
        this.scriptTimeout = scriptTimeout;
        this.startTimeout = startTimeout;
        this.templates = null != templates ? new CopyOnWriteArrayList<>(templates) : new CopyOnWriteArrayList<>();
        this.zones = Util.fixEmptyAndTrim(zones);
        this.trustAll = trustAll;
        this.groupPrefix = groupPrefix;
        readResolve();
        this.pendingNodes = 0;
    }

    protected Object readResolve() {
        for (JCloudsSlaveTemplate tpl : templates) {
            tpl.setCloud(this);
        }
        return this;
    }

    public void addTemplate(JCloudsSlaveTemplate tpl) {
        tpl.setCloud(this);
        templates.add(tpl);
    }

    public void removeTemplate(JCloudsSlaveTemplate tpl) {
        templates.remove(tpl);
    }

    public void replaceTemplate(JCloudsSlaveTemplate from, JCloudsSlaveTemplate to) {
        to.setCloud(this);
        templates.replaceAll(t -> t.equals(from) ? to : t);
    }

    /**
     * Get the retention time in minutes or default value from CloudInstanceDefaults if it is zero.
     * @return The retention time in minutes.
     * @see CloudInstanceDefaults#DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES
     */
    public int getRetentionTime() {
        return retentionTime == 0 ? CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES : retentionTime;
    }

    /**
     * Get the error retention time in minutes or default value from CloudInstanceDefaults if it is zero.
     * @return The retention time in minutes.
     * @see CloudInstanceDefaults#DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES
     */
    public int getErrorRetentionTime() {
        return errorRetentionTime;
    }

    private static final Iterable<Module> MODULES = ImmutableSet.<Module>of(
            new SshjSshClientModule(),
            new JDKLoggingModule() {
                @Override
                public org.jclouds.logging.Logger.LoggerFactory createLoggerFactory() {
                    return new ComputeLogger.Factory();
                }
            },
            new JenkinsConfigurationModule());

    private static <A extends Closeable> A api(
            Class<A> apitype, final String provider, final String credId, final Properties overrides) {
        // correct the classloader so that extensions can be found
        Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
        CredentialsHelper.setProject(credId, overrides);
        return CredentialsHelper.setCredentials(ContextBuilder.newBuilder(provider), credId)
                .overrides(overrides)
                .modules(MODULES)
                .buildApi(typeToken(apitype));
    }

    private static Properties buildJcloudsOverrides(final String url, final String zones, boolean trustAll) {
        Properties ret = new Properties();
        if (!isNullOrEmpty(url)) {
            ret.setProperty(Constants.PROPERTY_ENDPOINT, url);
        }
        if (trustAll) {
            ret.put(Constants.PROPERTY_TRUST_ALL_CERTS, "true");
            ret.put(Constants.PROPERTY_RELAX_HOSTNAME, "true");
        }
        if (!isNullOrEmpty(zones)) {
            ret.setProperty(LocationConstants.PROPERTY_ZONES, zones);
        }
        return ret;
    }

    static <A extends Closeable> A api(
            Class<A> apitype, final String provider, final String credId, final String url, final String zones) {
        return api(apitype, provider, credId, buildJcloudsOverrides(url, zones, false));
    }

    static <A extends Closeable> A api(
            Class<A> apitype,
            final String provider,
            final String credId,
            final String url,
            final String zones,
            final boolean trustAll) {
        return api(apitype, provider, credId, buildJcloudsOverrides(url, zones, trustAll));
    }

    public <A extends Closeable> A newApi(Class<A> apitype) {
        Properties overrides = buildJcloudsOverrides(endPointUrl, zones, trustAll);
        if (scriptTimeout > 0) {
            overrides.setProperty(ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE, String.valueOf(scriptTimeout));
        }
        if (startTimeout > 0) {
            overrides.setProperty(ComputeServiceProperties.TIMEOUT_NODE_RUNNING, String.valueOf(startTimeout));
        }
        return api(apitype, providerName, cloudCredentialsId, overrides);
    }

    private static ComputeServiceContext ctx(final String provider, final String credId, final Properties overrides) {
        // correct the classloader so that extensions can be found
        Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
        CredentialsHelper.setProject(credId, overrides);
        return CredentialsHelper.setCredentials(ContextBuilder.newBuilder(provider), credId)
                .overrides(overrides)
                .modules(MODULES)
                .buildView(ComputeServiceContext.class);
    }

    static ComputeServiceContext ctx(final String provider, final String credId, final String url, final String zones) {
        return ctx(provider, credId, buildJcloudsOverrides(url, zones, false));
    }

    static ComputeServiceContext ctx(
            final String provider, final String credId, final String url, final String zones, final boolean trustAll) {
        return ctx(provider, credId, buildJcloudsOverrides(url, zones, trustAll));
    }

    public ComputeService newCompute() {
        Properties overrides = buildJcloudsOverrides(endPointUrl, zones, trustAll);
        if (scriptTimeout > 0) {
            overrides.setProperty(ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE, String.valueOf(scriptTimeout));
        }
        if (startTimeout > 0) {
            overrides.setProperty(ComputeServiceProperties.TIMEOUT_NODE_RUNNING, String.valueOf(startTimeout));
        }
        return ctx(providerName, cloudCredentialsId, overrides).getComputeService();
    }

    public ComputeService getCompute() {
        if (this.compute == null) {
            this.compute = newCompute();
        }
        return compute;
    }

    public List<JCloudsSlaveTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    public void setTemplates(List<JCloudsSlaveTemplate> newTemplates) {
        if (null != newTemplates) {
            for (JCloudsSlaveTemplate t : newTemplates) {
                t.setCloud(this);
            }
            templates = new CopyOnWriteArrayList<>(newTemplates);
        } else {
            templates = new CopyOnWriteArrayList<>();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Cloud.CloudState state, int excessWorkload) {
        final Label label = state.getLabel();
        final JCloudsSlaveTemplate tpl = getTemplate(label);
        List<PlannedNode> plannedNodeList = new ArrayList<PlannedNode>();

        while (excessWorkload > 0
                && !Jenkins.get().isQuietingDown()
                && !Jenkins.get().isTerminating()) {

            if ((getRunningNodesCount() + plannedNodeList.size() + pendingNodes) >= instanceCap) {
                LOGGER.info(String.format(
                        "Instance cap of %s reached while adding capacity for label %s",
                        getName(), (label != null) ? label.toString() : "null"));
                break; // maxed out
            }

            final ProvisioningActivity.Id provisioningId = new ProvisioningActivity.Id(this.name, tpl.name);

            plannedNodeList.add(new TrackedPlannedNode(
                    provisioningId, tpl.getNumExecutors(), Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                        public Node call() throws Exception {
                            // TODO: record the output somewhere
                            JCloudsSlave jcloudsSlave;
                            pendingNodes++;
                            try {
                                jcloudsSlave = tpl.provisionSlave(StreamTaskListener.fromStdout(), provisioningId);
                            } finally {
                                pendingNodes--;
                            }
                            Jenkins.get().addNode(jcloudsSlave);

                            /* Cloud instances may have a long init script. If we declare the provisioning complete by returning
                            without the connect operation, NodeProvisioner may decide that it still wants one more instance,
                            because it sees that (1) all the slaves are offline (because it's still being launched) and (2)
                            there's no capacity provisioned yet. Deferring the completion of provisioning until the launch goes
                            successful prevents this problem.  */
                            ensureLaunched(jcloudsSlave);
                            return jcloudsSlave;
                        }
                    })));
            excessWorkload -= tpl.getNumExecutors();
        }
        return plannedNodeList;
    }

    private void ensureLaunched(JCloudsSlave jcloudsSlave) throws InterruptedException, ExecutionException {
        jcloudsSlave.waitForPhoneHome(null);
        Integer launchTimeoutSec = 5 * 60;
        Computer computer = jcloudsSlave.toComputer();
        long startMoment = System.currentTimeMillis();
        while (null != computer && computer.isOffline()) {
            if (computer.getOfflineCauseReason().equals(CliMessages.ONE_OFF_CAUSE.getText())) {
                break;
            }
            try {
                LOGGER.info(String.format("Agent %s not connected yet", jcloudsSlave.getDisplayName()));
                computer.connect(false).get();
                Thread.sleep(5000l);
            } catch (InterruptedException | ExecutionException e) {
                String msg = e.getMessage();
                if (null != msg && msg.contains("NULL agent")) {
                    throw new ExecutionException(new Throwable(msg));
                }
                LOGGER.warning(String.format("Error while launching %s: %s", jcloudsSlave.getDisplayName(), e));
            }

            if ((System.currentTimeMillis() - startMoment) > 1000l * launchTimeoutSec) {
                String msg = String.format(
                        "Failed to connect to %s within %d sec.", jcloudsSlave.getDisplayName(), launchTimeoutSec);
                LOGGER.warning(msg);
                throw new ExecutionException(new Throwable(msg));
            }
        }
    }

    @Override
    public boolean canProvision(final Cloud.CloudState state) {
        return getTemplate(state.getLabel()) != null;
    }

    public JCloudsSlaveTemplate getTemplate(String name) {
        for (JCloudsSlaveTemplate t : templates) if (t.name.equals(name)) return t;
        return null;
    }

    /**
     * Gets {@link jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate} that has the matching {@link Label}.
     * @param label The label to be matched.
     * @return The slave template or {@code null} if the specified label did not match.
     */
    JCloudsSlaveTemplate getTemplate(Label label) {
        for (JCloudsSlaveTemplate t : templates) if (label == null || label.matches(t.getLabelSet())) return t;
        return null;
    }

    @Restricted(NoExternalUse.class) // jelly
    public JCloudsSlaveTemplate.DescriptorImpl getTemplateDescriptor() {
        return (JCloudsSlaveTemplate.DescriptorImpl) Jenkins.get().getDescriptorOrDie(JCloudsSlaveTemplate.class);
    }

    private String checkNewTemplateName(String name) {
        String ret = Util.fixEmptyAndTrim(name);
        if (null == ret) {
            throw new Failure("New template name must not be empty");
        }
        if (null != getTemplate(ret)) {
            throw new Failure("A template named " + ret + " already exists");
        }
        return ret;
    }

    @POST
    public void doReorder(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins j = Jenkins.get();
        j.checkPermission(Jenkins.ADMINISTER);
        var names = req.getParameterValues("name");
        if (names == null) {
            throw new Failure("No template names given");
        }
        var namesList = Arrays.asList(names);
        var ntpls = new ArrayList<>(templates);
        ntpls.sort(Comparator.comparingInt(tpl -> getIndexOf(namesList, tpl)));
        setTemplates(ntpls);
        j.save();
        rsp.sendRedirect2("templates");
    }

    private static int getIndexOf(List<String> namesList, JCloudsSlaveTemplate tpl) {
        var i = namesList.indexOf(tpl.name);
        return i == -1 ? Integer.MAX_VALUE : i;
    }

    @POST
    public synchronized void doCreate(
            StaplerRequest2 req,
            StaplerResponse2 rsp,
            @QueryParameter String name,
            @QueryParameter String mode,
            @QueryParameter String from)
            throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (mode != null && mode.equals("on")) {
            name = checkNewTemplateName(name);
            from = Util.fixEmptyAndTrim(from);
            if (null == from) {
                throw new Failure("No source template was specified");
            }
            JCloudsSlaveTemplate fromTpl = getTemplate(from);
            String xml = Jenkins.XSTREAM.toXML(fromTpl);
            // Not great, but template name is final
            xml = xml.replaceFirst("<name>.*</name>", "<name>" + name + "</name>");
            JCloudsSlaveTemplate tplTo = (JCloudsSlaveTemplate) Jenkins.XSTREAM.fromXML(xml);
            addTemplate(tplTo);
            // send the browser to the config page
            rsp.sendRedirect2(Functions.getNearestAncestorUrl(req, this) + "/" + tplTo.getUrl());
        } else {
            handleNewSlaveTemplatePage(name, req, rsp);
        }
    }

    @POST
    public HttpResponse doDoCreate(StaplerRequest2 req) throws Descriptor.FormException, IOException, ServletException {
        Jenkins j = Jenkins.get();
        j.checkPermission(Jenkins.ADMINISTER);
        JCloudsSlaveTemplate tpl = getTemplateDescriptor().newInstance(req, req.getSubmittedForm());
        if (null == Util.fixEmptyAndTrim(tpl.name)) {
            throw new Descriptor.FormException("Template name is mandatory", "templateName");
        }
        if (null != getTemplate(tpl.name)) {
            throw new Descriptor.FormException("Agent template name must be unique", "templateName");
        }
        addTemplate(tpl);
        j.save();
        // Back to templates
        return FormApply.success("templates");
    }

    private void handleNewSlaveTemplatePage(String name, StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException, ServletException {
        name = checkNewTemplateName(name);
        JSONObject formData = req.getSubmittedForm();
        formData.put("name", name);
        formData.remove("mode");
        req.setAttribute("instance", formData);
        req.getView(this, "_new.jelly").forward(req, rsp);
    }

    @Override
    public Cloud reconfigure(@NonNull StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        // cloud configuration does not contain templates anymore, so just keep existing ones.
        var newInstance = (JCloudsCloud) super.reconfigure(req, form);
        newInstance.setTemplates(templates);
        return newInstance;
    }

    /**
     * Provisions a new node from supplied template.
     *
     * @param t The template to be used.
     */
    public JCloudsSlave doProvisionFromTemplate(final JCloudsSlaveTemplate t) throws IOException {
        final StringWriter sw = new StringWriter();
        final StreamTaskListener listener = new StreamTaskListener(sw);
        final ProvisioningActivity.Id provisioningId = new ProvisioningActivity.Id(this.name, t.name);
        JCloudsSlave node = t.provisionSlave(listener, provisioningId);
        Jenkins.get().addNode(node);
        return node;
    }

    /**
     * Provisions a new node manually (by clicking a button in the computer list).
     *
     * @param req  {@link StaplerRequest2}.
     * @param rsp  {@link StaplerResponse2}.
     * @param tplname Name of the template to provision.
     * @throws ServletException if an error occurs.
     * @throws IOException if an error occurs.
     * @throws Descriptor.FormException if the form does not validate.
     */
    @POST
    public void doProvision(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String tplname)
            throws ServletException, IOException, Descriptor.FormException {
        checkPermission(PROVISION);
        if (tplname == null) {
            sendError("The agent template name query parameter is missing", req, rsp);
            return;
        }
        JCloudsSlaveTemplate t = getTemplate(tplname);
        if (t == null) {
            sendError("No such agent template with name : " + tplname, req, rsp);
            return;
        }

        if (getRunningNodesCount() + pendingNodes < instanceCap) {
            try {
                pendingNodes++;
                JCloudsSlave node = doProvisionFromTemplate(t);
                rsp.sendRedirect2(req.getContextPath() + "/computer/" + node.getNodeName());
            } finally {
                pendingNodes--;
            }
        } else {
            sendError(String.format("Instance cap of %s reached", getName()), req, rsp);
        }
    }

    /**
     * Determine how many nodes are currently running for this cloud.
     * @return number of running nodes.
     */
    public int getRunningNodesCount() {
        int nodeCount = 0;

        for (ComputeMetadata cm : getCompute().listNodes()) {
            if (NodeMetadata.class.isInstance(cm)) {
                NodeMetadata nm = (NodeMetadata) cm;
                String nodeGroup = removeGroupPrefix(nm.getGroup());

                if (getTemplate(nodeGroup) != null
                        && !nm.getStatus().equals(NodeMetadata.Status.SUSPENDED)
                        && !nm.getStatus().equals(NodeMetadata.Status.TERMINATED)) {
                    nodeCount++;
                }
            }
        }
        return nodeCount;
    }

    void registerPhoneHomeMonitor(final PhoneHomeMonitor monitor) {
        if (null == monitor) {
            throw new IllegalArgumentException("monitor may not be null");
        }
        if (null == phms) {
            phms = new CopyOnWriteArrayList<>();
        }
        phms.add(monitor);
    }

    public void unregisterPhoneHomeMonitor(final PhoneHomeMonitor monitor) {
        if (null == monitor) {
            throw new IllegalArgumentException("monitor may not be null");
        }
        if (null != phms) {
            phms.remove(monitor);
        }
    }

    public boolean phoneHomeNotify(final String name) {
        if (null != phms) {
            for (final PhoneHomeMonitor monitor : phms) {
                if (monitor.ring(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final Set<String> CONFIRMED_GZIP_SUPPORTERS =
            ImmutableSet.<String>of("aws-ec2", "openstack-nova", "openstack-nova-ec2");

    public boolean allowGzippedUserData() {
        return !isNullOrEmpty(providerName) && CONFIRMED_GZIP_SUPPORTERS.contains(providerName);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        static boolean needSave = false;

        /**
         * Human readable name of this kind of configurable object.
         * @return The human readable name of this object.
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Cloud (JClouds)";
        }

        @POST
        public FormValidation doTestConnection(
                @QueryParameter String providerName,
                @QueryParameter String cloudCredentialsId,
                @QueryParameter String cloudGlobalKeyId,
                @QueryParameter String endPointUrl,
                @QueryParameter String zones,
                @QueryParameter boolean trustAll)
                throws IOException {

            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (null == Util.fixEmptyAndTrim(cloudCredentialsId)) {
                return FormValidation.error("Cloud credentials not specified.");
            }
            if (null == Util.fixEmptyAndTrim(cloudGlobalKeyId)) {
                return FormValidation.error("Cloud RSA key is not specified.");
            }

            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
            zones = Util.fixEmptyAndTrim(zones);

            FormValidation result = FormValidation.ok("Connection succeeded!");
            try (ComputeServiceContext ctx = ctx(providerName, cloudCredentialsId, endPointUrl, zones, trustAll)) {
                ctx.getComputeService().listNodes();
            } catch (Exception ex) {
                result = FormValidation.error(
                        "Cannot connect to specified cloud, please check the credentials: " + ex.getMessage());
            }
            return result;
        }

        @POST
        public FormValidation doCheckCloudGlobalKeyId(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            FormValidation ret = FormValidation.validateRequired(value);
            if (ret.equals(FormValidation.ok())) {
                StandardUsernameCredentials suc = CredentialsHelper.getCredentialsById(value);
                if (null == suc) {
                    ret = FormValidation.error("Credentials with id %s not found", value);
                } else if (!CredentialsHelper.isRSACredential(value)) {
                    ret = FormValidation.error("Not an RSA SSH key credential");
                }
            }
            return ret;
        }

        ImmutableSortedSet<String> getAllProviders() {
            // correct the classloader so that jclouds extensions can be found
            Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
            // TODO: apis need endpoints, providers don't; do something smarter
            // with this stuff :)
            Builder<String> builder = ImmutableSet.<String>builder();
            builder.addAll(Iterables.transform(Apis.viewableAs(ComputeServiceContext.class), Apis.idFunction()));
            builder.addAll(
                    Iterables.transform(Providers.viewableAs(ComputeServiceContext.class), Providers.idFunction()));
            return ImmutableSortedSet.copyOf(builder.build());
        }

        public String defaultProviderName() {
            return getAllProviders().first();
        }

        @POST
        public ListBoxModel doFillProviderNameItems() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            ListBoxModel m = new ListBoxModel();
            for (final String supportedProvider : getAllProviders()) {
                // docker is not really usable with jclouds (yet?). Too many semantic differences.
                if (!"stub".equals(supportedProvider) && !"docker".equals(supportedProvider)) {
                    m.add(supportedProvider, supportedProvider);
                }
            }
            return m;
        }

        @POST
        public ListBoxModel doFillCloudCredentialsIdItems(
                @AncestorInPath ItemGroup context, @QueryParameter String currentValue) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.get())
                    .hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel().includeCurrentValue(currentValue);
            }
            return new StandardUsernameListBoxModel()
                    .includeAs(ACL.SYSTEM2, context, StandardUsernameCredentials.class)
                    .includeCurrentValue(currentValue);
        }

        @POST
        public ListBoxModel doFillCloudGlobalKeyIdItems(
                @AncestorInPath ItemGroup context, @QueryParameter String currentValue) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.get())
                    .hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel().includeCurrentValue(currentValue);
            }
            return new StandardUsernameListBoxModel()
                    .includeAs(ACL.SYSTEM2, context, SSHUserPrivateKey.class)
                    .includeCurrentValue(currentValue);
        }

        @POST
        public ComboBoxModel doFillCopyNewTemplateFromItems(@RelativePath("..") @QueryParameter String cloudName) {
            ComboBoxModel m = new ComboBoxModel();
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            JCloudsCloud c = getByName(cloudName);
            if (null != c) {
                for (JCloudsSlaveTemplate tpl : c.getTemplates()) {
                    m.add(tpl.name);
                }
            }
            return m;
        }

        @POST
        public FormValidation doCheckNewTemplateName(@QueryParameter String cloudName, @QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            JCloudsCloud c = getByName(cloudName);
            if (null != c && null != c.getTemplate(value)) {
                return FormValidation.error("A template named " + value + " already exists");
            }
            return FormValidation.validateRequired(value);
        }

        @POST
        public FormValidation doCheckProfile(@QueryParameter String initialName, @QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            try {
                Jenkins.checkGoodName(value);
            } catch (Failure x) {
                return FormValidation.error(x.getMessage());
            }
            if (!initialName.equals(value) && null != Jenkins.get().clouds.getByName(value)) {
                return FormValidation.error("A cloud named " + value + " already exists");
            }
            return FormValidation.validateRequired(value);
        }

        @POST
        public FormValidation doCheckProviderName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @POST
        public FormValidation doCheckInstanceCap(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        @POST
        public FormValidation doCheckRetentionTime(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            int n;
            try {
                n = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return FormValidation.error("Not a number");
            }
            if (n >= -1) {
                return FormValidation.ok();
            }
            return FormValidation.error("Number must be >= -1");
        }

        @POST
        public FormValidation doCheckScriptTimeout(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        @POST
        public FormValidation doCheckStartTimeout(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        @POST
        public FormValidation doCheckEndPointUrl(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (!value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://")) {
                return FormValidation.error("The endpoint must be a http(s) URL");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckGroupPrefix(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (!value.matches("^[a-z0-9]*$")) {
                return FormValidation.error("The group prefix may contain lowercase letters and numbers only.");
            }
            return FormValidation.ok();
        }

        @Initializer(after = InitMilestone.JOB_LOADED)
        public static void completed() throws IOException {
            if (needSave) {
                needSave = false;
                LOGGER.info(">>>>>> auto-saving migrated config data...");
                Jenkins.get().save();
            }
        }
    }

    @Restricted(DoNotUse.class)
    public static class ConverterImpl extends XStream2.PassthruConverter<JCloudsCloud> {

        static final Logger LOGGER = Logger.getLogger(ConverterImpl.class.getName());

        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override
        protected void callback(JCloudsCloud c, UnmarshallingContext context) {
            boolean any = false;
            if (isNullOrEmpty(c.getCloudGlobalKeyId()) && !isNullOrEmpty(c.privateKey)) {
                LOGGER.info("Upgrading config data: cloud global key -> via credentials plugin");
                c.setCloudGlobalKeyId(convertCloudPrivateKey(c.name, c.privateKey));
                any = true;
            }
            if (isNullOrEmpty(c.getCloudCredentialsId()) && !isNullOrEmpty(c.identity)) {
                LOGGER.info("Upgrading config data: cloud credentals -> via credentials plugin");
                final String description = "JClouds cloud " + c.name + " - auto-migrated";
                c.setCloudCredentialsId(CredentialsHelper.convertCredentials(description, c.identity, c.credential));
                any = true;
            }
            for (JCloudsSlaveTemplate t : c.templates) {
                if (t.upgrade()) {
                    any = true;
                }
            }
            if (any) {
                LOGGER.info(String.format(">>>>>> cloud %s needs saving migrated config data", c.name));
                ((JCloudsCloud.DescriptorImpl) c.getDescriptor()).needSave = true;
            }
        }

        /**
         * Converts the old privateKey into a new ssh-credential-plugin record.
         * The name of this cloud instance is used as username.
         * @param name The name of the JCloudsCloud.
         * @param privateKey The old privateKey.
         * @return The Id of the newly created  ssh-credential-plugin record.
         */
        private String convertCloudPrivateKey(final String name, final String privateKey) {
            final String description = "JClouds cloud " + name + " - auto-migrated";
            StandardUsernameCredentials u = new BasicSSHUserPrivateKey(
                    CredentialsScope.SYSTEM,
                    null,
                    "Global key",
                    new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey),
                    null,
                    description);
            try {
                return CredentialsHelper.storeCredentials(u);
            } catch (IOException e) {
                LOGGER.warning(String.format("Error while migrating privateKey: %s", e.getMessage()));
            }
            return null;
        }
    }

    private static final ConcurrentMap<Run<?, ?>, List<RunningNode>> supplementalsToCheck = new ConcurrentHashMap<>();

    static void cleanupSupplementalNodes() {
        for (Map.Entry<Run<?, ?>, List<RunningNode>> entry : supplementalsToCheck.entrySet()) {
            Result result = entry.getKey().getResult();
            if (null != result && result == Result.ABORTED) {
                LOGGER.info("job \"" + entry.getKey().getFullDisplayName()
                        + "\"was aborted, cleaning up supplemental nodes");
                for (RunningNode rn : entry.getValue()) {
                    JCloudsCloud c = getByName(rn.getCloudName());
                    if (null != c) {
                        if (rn.getShouldSuspend()) {
                            try {
                                LOGGER.info("Suspending supplemental node: " + rn.getNodeName());
                                c.getCompute().suspendNodesMatching(new Predicate<NodeMetadata>() {
                                    public boolean apply(NodeMetadata input) {
                                        return null != input && input.getId().equals(rn.getNodeId());
                                    }
                                });
                                continue;
                            } catch (UnsupportedOperationException e) {
                                LOGGER.warning("Suspend unsupported on cloud: " + c.name);
                            }
                        }
                        LOGGER.info("Destroying supplemental node: " + rn.getNodeName());
                        c.getCompute().destroyNodesMatching(new Predicate<NodeMetadata>() {
                            public boolean apply(NodeMetadata input) {
                                return null != input && input.getId().equals(rn.getNodeId());
                            }
                        });
                    }
                }
                supplementalsToCheck.remove(entry.getKey());
            }
        }
    }

    /**
     * Publish metadata for supplemental nodes.
     *
     * @param nodes The supplemental nodes to be modified.
     */
    static synchronized void publishMetadata(Iterable<RunningNode> nodes, Map<String, String> data, String indexName) {
        int idx = 0;
        for (RunningNode rn : nodes) {
            String nid = rn.getNodeId();
            String msg = "Publishing metadata to supplemental node " + nid;
            if (!indexName.isEmpty()) {
                data.put(indexName, String.valueOf(idx++));
            }
            MetaDataPublisher mdp = new MetaDataPublisher(getByName(rn.getCloudName()));
            mdp.publish(nid, msg, data);
        }
    }

    /**
     * Register supplemental nodes of a build for a periodical cleanup if the build has been aborted.
     *
     * @param build The build that lounched the nodes.
     * @param nodes The supplemental nodes to be cleaned up
     */
    static synchronized void registerSupplementalCleanup(Run<?, ?> build, Iterable<RunningNode> nodes) {
        LOGGER.fine("Registering build \"" + build.getFullDisplayName() + "\" for cleanup");
        List<RunningNode> newList = new ArrayList<>();
        for (RunningNode rn : nodes) {
            newList.add(rn);
        }
        if (!newList.isEmpty()) {
            List<RunningNode> oldList = supplementalsToCheck.getOrDefault(build, new ArrayList<>());
            oldList.addAll(newList);
            supplementalsToCheck.put(build, oldList);
        }
    }

    static void unregisterSupplementalCleanup(Run<?, ?> build) {
        LOGGER.fine("Unregistering build \"" + build.getFullDisplayName() + "\" from cleanup");
        supplementalsToCheck.remove(build);
    }

    static boolean isNullOrEmpty(final String value) {
        return null == Util.fixEmptyAndTrim(value);
    }
}
