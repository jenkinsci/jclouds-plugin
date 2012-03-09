package jenkins.plugins.jclouds;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Module;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.util.ComputeServiceUtils;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.io.Payloads;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.statements.java.InstallJDK;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.ssh.SshClient;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;

/**
 * @author Vijay Kiran
 */
public class JCloudsCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(JCloudsCloud.class.getName());

    private final String identity;
    private final String credential;
    private String providerName;

    private transient ComputeService compute;
    private String privateKey;

    public static JCloudsCloud get() {
        return Hudson.getInstance().clouds.get(JCloudsCloud.class);
    }

    @DataBoundConstructor
    public JCloudsCloud(final String providerName, final String identity, final String credential, final String privateKey) {
        super("jclouds");
        this.identity = identity;
        this.credential = credential;
        this.providerName = providerName;
        this.privateKey = privateKey;
        Iterable<Module> modules = ImmutableSet.<Module>of(new SshjSshClientModule(), new SLF4JLoggingModule(),
                new EnterpriseConfigurationModule());

        //Do we really need to create context here ?
        this.compute = new ComputeServiceContextFactory()
                .createContext(this.providerName, this.identity, this.credential, modules).getComputeService();
    }

    public ComputeService getCompute() {
        return compute;
    }

    public String getIdentity() {
        return identity;
    }

    public String getCredential() {
        return credential;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    @Override
    public boolean canProvision(Label label) {
        return true;
    }

    //Called from computerSet.jelly
    public void doProvision(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException, Descriptor.FormException {
        LOGGER.info("Provisioning new node");
        NodeMetadata nodeMetadata = createNodeWithAdminUserAndJDKInGroupOpeningPortAndMinRam("jenkins", 8000, 512, this.getPrivateKey());
        JCloudsSlave node = new JCloudsSlave(nodeMetadata);
        Hudson.getInstance().addNode(node);
        rsp.sendRedirect2(req.getContextPath() + "/computer/" + node.getLabelString());

    }

    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        LOGGER.info("Provisioning new node with label " + label.getName() + ", excessWorkload: " + excessWorkload);
        List<NodeProvisioner.PlannedNode> nodes = new ArrayList<NodeProvisioner.PlannedNode>();
        nodes.add(new NodeProvisioner.PlannedNode(label.getName(),
                Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                    public Node call() throws Exception {
                        NodeMetadata nodeMetadata =
                                createNodeWithAdminUserAndJDKInGroupOpeningPortAndMinRam("jenkins", 8000, 512, JCloudsCloud.this.getPrivateKey());
                        return new JCloudsSlave(nodeMetadata);
                    }
                }), 1));
        return nodes;
    }

    private NodeMetadata createNodeWithAdminUserAndJDKInGroupOpeningPortAndMinRam(String group, int port, int minRam, String privateKey) {
        ImmutableMap<String, String> userMetadata = ImmutableMap.<String, String>of("Name", group);

        // we want everything as defaults except ram
        Template defaultTemplate = compute.templateBuilder().build();
        Template template = compute.templateBuilder().fromTemplate(defaultTemplate).minRam(minRam).build();

        // setup the template to customize the node with jdk, etc. also opening ports
//        AdminAccess.builder().installAdminPrivateKey(false)
        Statement bootstrap = newStatementList(AdminAccess.standard(), InstallJDK.fromURL());
        template.getOptions().inboundPorts(22, port).userMetadata(userMetadata).runScript(bootstrap);

        NodeMetadata node = null;
        LOGGER.info("creating jclouds node");
        SshClient sshClient = null;

        try {
            node = getOnlyElement(compute.createNodesInGroup(group, 1, template));
            sshClient = compute.getContext().utils().sshForNode().apply(NodeMetadataBuilder.fromNodeMetadata(node).credentials(node.getCredentials()).build());

            sshClient.connect();
            sshClient.put("/tmp/slave.jar", Payloads.newByteArrayPayload(Hudson.getInstance().getJnlpJars("slave.jar").readFully()));

            LOGGER.info(node.getHostname() + " created");
            return node;
        } catch (RunNodesException e) {
            throw destroyBadNodesAndPropagate(e);
        } catch (IOException e) {
            LOGGER.severe("Can't copy slave jar to the instance");
        } finally {
            if (sshClient != null) {
                sshClient.disconnect();
            }
        }
        //Check if node is null and throw
        return node;
    }

    private RuntimeException destroyBadNodesAndPropagate(RunNodesException e) {
        for (Map.Entry<? extends NodeMetadata, ? extends Throwable> nodeError : e.getNodeErrors().entrySet())
            compute.destroyNode(nodeError.getKey().getId());
        throw propagate(e);
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

        public FormValidation doTestConnection(@QueryParameter String providerName,
                                               @QueryParameter String identity,
                                               @QueryParameter String credential,
                                               @QueryParameter String privateKey) {
            if (identity == null)
                return FormValidation.error("Invalid (AccessId).");
            if (credential == null)
                return FormValidation.error("Invalid credential (secret key).");
            if (privateKey == null)
                return FormValidation.error("Private key is not specified. Click 'Generate Key' to generate one.");


            Iterable<Module> modules = ImmutableSet.<Module>of(new SshjSshClientModule(), new SLF4JLoggingModule(),
                    new EnterpriseConfigurationModule());
            FormValidation result = FormValidation.ok("Connection succeeded!");
            ComputeService computeService = null;
            try {

                ComputeServiceContext context = new ComputeServiceContextFactory()
                        .createContext(providerName, identity, credential, modules);
                computeService = context.getComputeService();
                computeService.listNodes();
                //TODO Catch only exceptions that re really thrown.
            } catch (Exception ex) {
                result = FormValidation.error("Cannot connect to specified cloud, please check the identity and credentials: " + ex.getLocalizedMessage());
            } finally {
                if (computeService != null) {
                    computeService.getContext().close();
                }
            }
            return result;
        }

        public FormValidation doCheckPrivateKey(@QueryParameter String value) throws IOException, ServletException {
            boolean hasStart = false, hasEnd = false;
            BufferedReader br = new BufferedReader(new StringReader(value));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.equals("-----BEGIN RSA PRIVATE KEY-----"))
                    hasStart = true;
                if (line.equals("-----END RSA PRIVATE KEY-----"))
                    hasEnd = true;
            }
            if (!hasStart)
                return FormValidation.error("This doesn't look like a private key at all");
            if (!hasEnd)
                return FormValidation.error("The private key is missing the trailing 'END RSA PRIVATE KEY' marker. Copy&paste error?");
            return FormValidation.ok();
        }

        public AutoCompletionCandidates doAutoCompleteProviderName(@QueryParameter final String value) {

            Iterable<String> supportedProviders = ComputeServiceUtils.getSupportedProviders();

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

        public FormValidation doCheckProviderName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckCredential(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckIdentity(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }
    }
}
