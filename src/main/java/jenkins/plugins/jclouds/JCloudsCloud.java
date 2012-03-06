package jenkins.plugins.jclouds;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.statements.java.InstallJDK;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.jclouds.location.reference.LocationConstants.PROPERTY_REGIONS;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;

/**
 * @author Vijay Kiran
 */
public class JCloudsCloud extends Cloud {

    private static final Logger logger = Logger.getLogger(JCloudsCloud.class.getName());
    private ComputeService compute;
    private final String providerName;
    private final String identity;
    private final String credential;


    @DataBoundConstructor
    public JCloudsCloud(final String id, final String providerName, String identity, String credential) {
        super(id);
        this.providerName = providerName;
        this.identity = identity;
        this.credential = credential;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getIdentity() {
        return identity;
    }

    public String getCredential() {
        return credential;
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> nodes = new ArrayList<NodeProvisioner.PlannedNode>();
        nodes.add(new NodeProvisioner.PlannedNode("jclouds-node-1",
                Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                    public Node call() throws Exception {
                        logger.info("Provison the node here");
                        NodeMetadata nodeMetadata = initComputeAndCreateANode();
                        return null;
                    }
                }), 1));

        return nodes;
    }


    private NodeMetadata initComputeAndCreateANode() {
        Properties properties = new Properties();
        properties.setProperty(PROPERTY_REGIONS, "us-east-1");
        properties.setProperty("jclouds.ec2.ami-query", "owner-id=137112412989;state=available;image-type=machine");
        // example of injecting a ssh implementation
        Iterable<Module> modules = ImmutableSet.<Module>of(new SshjSshClientModule(), new SLF4JLoggingModule(),
                new EnterpriseConfigurationModule());

        this.compute = new ComputeServiceContextFactory()
                .createContext(this.providerName, this.identity, this.credential, modules, properties).getComputeService();
        return createNodeWithAdminUserAndJDKInGroupOpeningPortAndMinRam("jenkins", 8000, 512);

    }

    public NodeMetadata createNodeWithAdminUserAndJDKInGroupOpeningPortAndMinRam(String group, int port, int minRam) {
        ImmutableMap<String, String> userMetadata = ImmutableMap.<String, String>of("Name", group);

        // we want everything as defaults except ram
        Template defaultTemplate = compute.templateBuilder().build();
        Template template = compute.templateBuilder().fromTemplate(defaultTemplate).minRam(minRam).build();

        // setup the template to customize the node with jdk, etc. also opening ports.
        Statement bootstrap = newStatementList(AdminAccess.standard(), InstallJDK.fromURL());
        template.getOptions().inboundPorts(22, port).userMetadata(userMetadata).runScript(bootstrap);

        logger.info("creating jclouds node");

        try {

            NodeMetadata node = getOnlyElement(compute.createNodesInGroup(group, 1, template));
            logger.info(node.getHostname() + " created");
            return node;
        } catch (RunNodesException e) {
            throw destroyBadNodesAndPropagate(e);
        }
    }


    private RuntimeException destroyBadNodesAndPropagate(RunNodesException e) {
        for (Map.Entry<? extends NodeMetadata, ? extends Throwable> nodeError : e.getNodeErrors().entrySet())
            compute.destroyNode(nodeError.getKey().getId());
        throw propagate(e);
    }


    @Override
    public boolean canProvision(Label label) {
        return true;
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

        //Fields
        public FormValidation doCheckProviderName(@QueryParameter String value) {
            return FormValidation.validateBase64(value, false, false, "Please enter a provider name - e.g, aws-ec2 or glesys");
        }

        public FormValidation doCheckCredential(@QueryParameter String value) {
            return FormValidation.validateBase64(value, false, false, "Credential can't be empty");
        }

        public FormValidation doCheckIdentity(@QueryParameter String value) {
            return FormValidation.validateBase64(value, false, false, "Identity can't be empty");
        }

    }
}
