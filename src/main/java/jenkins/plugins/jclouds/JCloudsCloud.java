package jenkins.plugins.jclouds;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
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


    protected JCloudsCloud(String name) {
        super(name);
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        logger.info("In == JCloudsCloud provision " + label);
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
        properties.setProperty("jclouds.ec2.cc-ami-query", "");
        // example of injecting a ssh implementation
        Iterable<Module> modules = ImmutableSet.<Module>of(new SshjSshClientModule(), new SLF4JLoggingModule(),
                new EnterpriseConfigurationModule());

        this.compute = new ComputeServiceContextFactory()
                .createContext("aws-ec2", IDENTITY, CREDENTIAL , modules, properties).getComputeService();
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
}
