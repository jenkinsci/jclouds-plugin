package hudson.plugins.rackspace;

import hudson.Extension;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.Secret;

import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.io.IOException;

import java.util.Collection;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.ComputeMetadata;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;


/**
 *
 * @author mordred
 */
public class RackspaceCloud extends Cloud {

    private final String user;
    private final Secret secret;

    @DataBoundConstructor
    public RackspaceCloud(String user, String secret) {
        super("rackspace-" + user);
        this.user = user;
        this.secret = Secret.fromString(secret.trim());
    }
    private static final Logger LOGGER = Logger.getLogger(RackspaceCloud.class.getName());

    public String getSecret() {
        return secret.getEncryptedValue();
    }

    public String getUser() {
        return user;
    }

    @Override
    public boolean canProvision(Label label) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Collection<PlannedNode> provision(Label label, int i) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        public String getDisplayName() {
            return "Rackspace Cloud";
        }

        public FormValidation doTestConnection(@QueryParameter String user,
                @QueryParameter String secret) throws ServletException, IOException {

            try {
                ComputeServiceContext context = new ComputeServiceContextFactory().createContext("cloudservers", user, secret);

                ComputeService client = context.getComputeService();

                //Set<? extends ComputeMetadata> nodes = Sets.newHashSet(connection.getNodes().values());

                for (ComputeMetadata node : client.getNodes().values()) {
                    LOGGER.info(node.getId());
                    LOGGER.info(node.getLocationId()); // where in the world is the node
                }
                return FormValidation.ok();
            } catch (org.jclouds.rest.AuthorizationException ex) {
                return FormValidation.error("Authentication Error: " + ex.getLocalizedMessage());
            }
        }
    }
}
