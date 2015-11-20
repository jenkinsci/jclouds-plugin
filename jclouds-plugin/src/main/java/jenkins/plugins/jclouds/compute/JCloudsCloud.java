package jenkins.plugins.jclouds.compute;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.inject.Module;
import hudson.Extension;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.apis.Apis;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.config.ComputeServiceProperties;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.location.reference.LocationConstants;
import org.jclouds.logging.jdk.config.JDKLoggingModule;
import org.jclouds.providers.Providers;
import org.jclouds.sshj.config.SshjSshClientModule;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import shaded.com.google.common.base.Objects;
import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.collect.ImmutableSet;
import shaded.com.google.common.collect.ImmutableSet.Builder;
import shaded.com.google.common.collect.ImmutableSortedSet;
import shaded.com.google.common.collect.Iterables;
import shaded.com.google.common.io.Closeables;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import hudson.security.ACL;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.security.AccessControlled;

import jenkins.plugins.jclouds.internal.CredentialsHelper;
import jenkins.plugins.jclouds.internal.SSHPublicKeyExtractor;

import hudson.util.XStream2;
import com.thoughtworks.xstream.converters.UnmarshallingContext;

/**
 * The JClouds version of the Jenkins Cloud.
 *
 * @author Vijay Kiran
 */
public class JCloudsCloud extends Cloud {

    static final Logger LOGGER = Logger.getLogger(JCloudsCloud.class.getName());

    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private final transient String identity;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private final transient Secret credential;

    public final String providerName;

    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private final transient String privateKey;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private final transient String publicKey;

    public final String endPointUrl;
    public final String profile;
    private final int retentionTime;
    public int instanceCap;
    public final List<JCloudsSlaveTemplate> templates;
    public final int scriptTimeout;
    public final int startTimeout;
    private transient ComputeService compute;
    public final String zones;

    private String cloudGlobalKeyId;
    private String cloudCredentialsId;

    public static List<String> getCloudNames() {
        List<String> cloudNames = new ArrayList<String>();
        for (Cloud c : Jenkins.getActiveInstance().clouds) {
            if (JCloudsCloud.class.isInstance(c)) {
                cloudNames.add(c.name);
            }
        }

        return cloudNames;
    }

    public static JCloudsCloud getByName(String name) {
        return (JCloudsCloud) Jenkins.getActiveInstance().clouds.getByName(name);
    }

    public String getCloudCredentialsId() {
        return cloudCredentialsId;
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

    private String getPrivateKeyFromCredential(final String id) {
        if (!Strings.isNullOrEmpty(id)) {
            // Added to remove ambiguous calling of lookupCredentials below
            List<DomainRequirement> nullList = null;

            SSHUserPrivateKey supk = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(SSHUserPrivateKey.class, Hudson.getInstance(), ACL.SYSTEM, nullList),
                    CredentialsMatchers.withId(id));
            if (null != supk) {
                return supk.getPrivateKey();
            }
        }
        return "";
    }

    private String getPublicKeyFromCredential(final String id) {
        if (!Strings.isNullOrEmpty(id)) {
            try {
                return SSHPublicKeyExtractor.extract(getPrivateKeyFromCredential(id), null);
            } catch (IOException e) {
                LOGGER.warning(String.format("Error while extracting public key: %s", e));
            }
        }
        return "";
    }

    @DataBoundConstructor
    public JCloudsCloud(final String profile, final String providerName, final String cloudCredentialsId, final String cloudGlobalKeyId,
            final String endPointUrl, final int instanceCap, final int retentionTime, final int scriptTimeout, final int startTimeout,
            final String zones, final List<JCloudsSlaveTemplate> templates) {
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
        this.scriptTimeout = scriptTimeout;
        this.startTimeout = startTimeout;
        this.templates = Objects.firstNonNull(templates, Collections.<JCloudsSlaveTemplate> emptyList());
        this.zones = Util.fixEmptyAndTrim(zones);
        readResolve();
    }

    protected Object readResolve() {
        for (JCloudsSlaveTemplate template : templates) {
            template.cloud = this;
        }
        return this;
    }

    /**
     * Get the retention time in minutes or default value from CloudInstanceDefaults if it is zero.
     * @see CloudInstanceDefaults#DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES
     */
    public int getRetentionTime() {
        return retentionTime == 0 ? CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES : retentionTime;
    }

    static final Iterable<Module> MODULES = ImmutableSet.<Module>of(new SshjSshClientModule(), new JDKLoggingModule() {
        @Override
        public org.jclouds.logging.Logger.LoggerFactory createLoggerFactory() {
            return new ComputeLogger.Factory();
        }
    }, new EnterpriseConfigurationModule());

    private static ComputeServiceContext ctx(final String providerName, final String identity, final String credential,
            final Properties overrides, final String zones) {
        if (!Strings.isNullOrEmpty(zones)) {
            overrides.setProperty(LocationConstants.PROPERTY_ZONES, zones);
        }
        // correct the classloader so that extensions can be found
        Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
        return ContextBuilder.newBuilder(providerName).credentials(identity, credential).overrides(overrides).modules(MODULES)
            .buildView(ComputeServiceContext.class);
    }

    private static ComputeServiceContext ctx(final String providerName, final String credentialsId, final Properties overrides, final String zones) {
        StandardUsernameCredentials u = CredentialsHelper.getCredentialsById(credentialsId);
        if (null != u && u instanceof StandardUsernamePasswordCredentials) {
            StandardUsernamePasswordCredentials up = (StandardUsernamePasswordCredentials)u;
            return ctx(providerName, up.getUsername(), up.getPassword().toString(), overrides, zones);
        }
        throw new RuntimeException("Using keys as credential for google cloud is not (yet) supported");
    }

    static ComputeServiceContext ctx(final String providerName, final String credentialsId, final String endPointUrl, final String zones) {
        final Properties overrides = new Properties();
        if (!Strings.isNullOrEmpty(endPointUrl)) {
            overrides.setProperty(Constants.PROPERTY_ENDPOINT, endPointUrl);
        }
        return ctx(providerName, credentialsId, overrides, zones);
    }

    public ComputeService newCompute() {
        Properties overrides = new Properties();
        if (!Strings.isNullOrEmpty(this.endPointUrl)) {
            overrides.setProperty(Constants.PROPERTY_ENDPOINT, this.endPointUrl);
        }
        if (scriptTimeout > 0) {
            overrides.setProperty(ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE, String.valueOf(scriptTimeout));
        }
        if (startTimeout > 0) {
            overrides.setProperty(ComputeServiceProperties.TIMEOUT_NODE_RUNNING, String.valueOf(startTimeout));
        }
        return ctx(this.providerName, this.cloudCredentialsId, overrides, this.zones).getComputeService();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        final JCloudsSlaveTemplate template = getTemplate(label);
        List<PlannedNode> plannedNodeList = new ArrayList<PlannedNode>();

        while (excessWorkload > 0 && !Jenkins.getActiveInstance().isQuietingDown() && !Jenkins.getActiveInstance().isTerminating()) {

            if ((getRunningNodesCount() + plannedNodeList.size()) >= instanceCap) {
                LOGGER.info("Instance cap reached while adding capacity for label " + ((label != null) ? label.toString() : "null"));
                break; // maxed out
            }

            plannedNodeList.add(new PlannedNode(template.name, Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                public Node call() throws Exception {
                    // TODO: record the output somewhere
                    JCloudsSlave jcloudsSlave = template.provisionSlave(StreamTaskListener.fromStdout());
                    Jenkins.getActiveInstance().addNode(jcloudsSlave);

                    /* Cloud instances may have a long init script. If we declare the provisioning complete by returning
                       without the connect operation, NodeProvisioner may decide that it still wants one more instance,
                       because it sees that (1) all the slaves are offline (because it's still being launched) and (2)
                       there's no capacity provisioned yet. Deferring the completion of provisioning until the launch goes
                       successful prevents this problem.  */
                    ensureLaunched(jcloudsSlave);
                    return jcloudsSlave;
                }
            }), template.getNumExecutors()));
            excessWorkload -= template.getNumExecutors();
        }
        return plannedNodeList;
    }

    private void ensureLaunched(JCloudsSlave jcloudsSlave) throws InterruptedException, ExecutionException {
        jcloudsSlave.waitForPhoneHome(null);
        Integer launchTimeoutSec = 5 * 60;
        Computer computer = jcloudsSlave.toComputer();
        long startMoment = System.currentTimeMillis();
        while (null != computer && computer.isOffline()) {
            try {
                LOGGER.info(String.format("Slave [%s] not connected yet", jcloudsSlave.getDisplayName()));
                computer.connect(false).get();
                Thread.sleep(5000l);
            } catch (InterruptedException e) {
                LOGGER.warning(String.format("Error while launching slave: %s", e));
            } catch (ExecutionException e) {
                LOGGER.warning(String.format("Error while launching slave: %s", e));
            }

            if ((System.currentTimeMillis() - startMoment) > 1000l * launchTimeoutSec) {
                String message = String.format("Failed to connect to slave within timeout (%d s).", launchTimeoutSec);
                LOGGER.warning(message);
                throw new ExecutionException(new Throwable(message));
            }
        }
    }

    @Override
    public boolean canProvision(final Label label) {
        return getTemplate(label) != null;
    }

    public JCloudsSlaveTemplate getTemplate(String name) {
        for (JCloudsSlaveTemplate t : templates)
            if (t.name.equals(name))
                return t;
        return null;
    }

    /**
     * Gets {@link jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate} that has the matching {@link Label}.
     */
    public JCloudsSlaveTemplate getTemplate(Label label) {
        for (JCloudsSlaveTemplate t : templates)
            if (label == null || label.matches(t.getLabelSet()))
                return t;
        return null;
    }

    JCloudsSlave doProvisionFromTemplate(final JCloudsSlaveTemplate t) throws IOException {
        final StringWriter sw = new StringWriter();
        final StreamTaskListener listener = new StreamTaskListener(sw);
        JCloudsSlave node = t.provisionSlave(listener);
        Jenkins.getActiveInstance().addNode(node);
        return node;
    }

    /**
     * Provisions a new node manually (by clicking a button in the computer list)
     *
     * @param req  {@link StaplerRequest}
     * @param rsp  {@link StaplerResponse}
     * @param name Name of the template to provision
     * @throws ServletException
     * @throws IOException
     * @throws Descriptor.FormException
     */
    public void doProvision(StaplerRequest req, StaplerResponse rsp, @QueryParameter String name) throws ServletException, IOException,
           Descriptor.FormException {
               checkPermission(PROVISION);
               if (name == null) {
                   sendError("The slave template name query parameter is missing", req, rsp);
                   return;
               }
               JCloudsSlaveTemplate t = getTemplate(name);
               if (t == null) {
                   sendError("No such slave template with name : " + name, req, rsp);
                   return;
               }

               if (getRunningNodesCount() < instanceCap) {
                   JCloudsSlave node = doProvisionFromTemplate(t);
                   rsp.sendRedirect2(req.getContextPath() + "/computer/" + node.getNodeName());
               } else {
                   sendError("Instance cap for this cloud is now reached for cloud profile: " + profile + " for template type " + name, req, rsp);
               }
    }

    /**
     * Determine how many nodes are currently running for this cloud.
     */
    public int getRunningNodesCount() {
        int nodeCount = 0;

        for (ComputeMetadata cm : getCompute().listNodes()) {
            if (NodeMetadata.class.isInstance(cm)) {
                String nodeGroup = ((NodeMetadata) cm).getGroup();

                if (getTemplate(nodeGroup) != null && !((NodeMetadata) cm).getStatus().equals(NodeMetadata.Status.SUSPENDED)
                        && !((NodeMetadata) cm).getStatus().equals(NodeMetadata.Status.TERMINATED)) {
                    nodeCount++;
                        }
            }
        }
        return nodeCount;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        /**
         * Human readable name of this kind of configurable object.
         */
        @Override
        public String getDisplayName() {
            return "Cloud (JClouds)";
        }

        public FormValidation doTestConnection(@QueryParameter String providerName, @QueryParameter String cloudCredentialsId,
                @QueryParameter String cloudGlobalKeyId, @QueryParameter String endPointUrl, @QueryParameter String zones) throws IOException {
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
            ComputeServiceContext ctx = null;
            try {
                Properties overrides = new Properties();
                if (!Strings.isNullOrEmpty(endPointUrl)) {
                    overrides.setProperty(Constants.PROPERTY_ENDPOINT, endPointUrl);
                }

                ctx = ctx(providerName, cloudCredentialsId, overrides, zones);

                ctx.getComputeService().listNodes();
            } catch (Exception ex) {
                result = FormValidation.error("Cannot connect to specified cloud, please check the credentials: " + ex.getMessage());
            } finally {
                Closeables.close(ctx,true);
            }
            return result;
        }

        public FormValidation doCheckCloudGlobalKeyId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public ListBoxModel doFillProviderNameItems() {
            ListBoxModel m = new ListBoxModel();

            // correct the classloader so that extensions can be found
            Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
            // TODO: apis need endpoints, providers don't; do something smarter
            // with this stuff :)
            Builder<String> builder = ImmutableSet.<String> builder();
            builder.addAll(Iterables.transform(Apis.viewableAs(ComputeServiceContext.class), Apis.idFunction()));
            builder.addAll(Iterables.transform(Providers.viewableAs(ComputeServiceContext.class), Providers.idFunction()));
            Iterable<String> supportedProviders = ImmutableSortedSet.copyOf(builder.build());

            for (String supportedProvider : supportedProviders) {
                m.add(supportedProvider, supportedProvider);
            }
            return m;
        }

        public ListBoxModel  doFillCloudCredentialsIdItems(@AncestorInPath ItemGroup context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getActiveInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }

            // Added to remove ambiguous calling of lookupCredentials below
            List<DomainRequirement> nullList = null;
            return new StandardUsernameListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM, nullList));
        }

        public ListBoxModel  doFillCloudGlobalKeyIdItems(@AncestorInPath ItemGroup context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getActiveInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }

            // Added to remove ambiguous calling of lookupCredentials below
            List<DomainRequirement> nullList = null;
            return new StandardUsernameListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(SSHUserPrivateKey.class, context, ACL.SYSTEM, nullList));
        }

        public AutoCompletionCandidates doAutoCompleteProviderName(@QueryParameter final String value) {
            // correct the classloader so that extensions can be found
            Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
            // TODO: apis need endpoints, providers don't; do something smarter
            // with this stuff :)
            Builder<String> builder = ImmutableSet.<String> builder();
            builder.addAll(Iterables.transform(Apis.viewableAs(ComputeServiceContext.class), Apis.idFunction()));
            builder.addAll(Iterables.transform(Providers.viewableAs(ComputeServiceContext.class), Providers.idFunction()));
            Iterable<String> supportedProviders = builder.build();

            Iterable<String> matchedProviders = Iterables.filter(supportedProviders, new Predicate<String>() {
                public boolean apply(@Nullable String input) {
                    return input != null && input.startsWith(value.toLowerCase());
                }
            });

            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            for (String matchedProvider : matchedProviders) {
                candidates.add(matchedProvider);
            }
            return candidates;
        }

        public FormValidation doCheckProfile(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckProviderName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPublicKey(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckCredential(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckIdentity(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckInstanceCap(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public FormValidation doCheckRetentionTime(@QueryParameter String value) {
            try {
                if (Integer.parseInt(value) == -1)
                    return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.validateNonNegativeInteger(value);
        }

        public FormValidation doCheckScriptTimeout(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public FormValidation doCheckStartTimeout(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public FormValidation doCheckEndPointUrl(@QueryParameter String value) {
            if (!value.isEmpty() && !value.startsWith("http")) {
                return FormValidation.error("The endpoint must be an URL");
            }
            return FormValidation.ok();
        }
    }

    @Restricted(DoNotUse.class)
    public static class ConverterImpl extends XStream2.PassthruConverter<JCloudsCloud> {

        static final Logger LOGGER = Logger.getLogger(ConverterImpl.class.getName());

        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override protected void callback(JCloudsCloud c, UnmarshallingContext context) {
            if (Strings.isNullOrEmpty(c.getCloudGlobalKeyId()) && !Strings.isNullOrEmpty(c.privateKey)) {
                c.setCloudGlobalKeyId(convertCloudPrivateKey(c.name, c.privateKey));
            }
            if (Strings.isNullOrEmpty(c.getCloudCredentialsId()) && !Strings.isNullOrEmpty(c.identity)) {
                c.setCloudCredentialsId(CredentialsHelper.convertCloudCredentials(c.name, c.identity, c.credential));
            }
            for (JCloudsSlaveTemplate t : c.templates) {
                t.upgrade();
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
            StandardUsernameCredentials u =
                new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, null, "Global key",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey), null, description);
            try {
                return CredentialsHelper.storeCredentials(u);
            } catch (IOException e) {
                LOGGER.warning(String.format("Error while migrating privateKey: %s", e.getMessage()));
            } 
            return null;
        }

    }

}
