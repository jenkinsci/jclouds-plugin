package hudson.plugins.jclouds;

import com.google.common.base.Throwables;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.Secret;

import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.io.IOException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.Size;
import org.jclouds.compute.util.ComputeUtils;
import org.jclouds.rest.AuthorizationException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;


/**
 *
 * @author mordred
 */
public class JClouds extends Cloud {

    private final String provider;
    private final String user;
    private final Secret secret;
    private List<SlaveTemplate> templates;

    @DataBoundConstructor
    public JClouds(String provider, String user, String secret, List<SlaveTemplate> templates) {
        super(String.format("jclouds-{0}-{1}", new Object[]{provider, user}));
        this.provider = provider;
        this.user = user;
        this.secret = Secret.fromString(secret.trim());
        this.templates = templates;
        if(templates==null)
        {
            templates=Collections.emptyList();
        }
        readResolve();

    }

    private Object readResolve() {
        for (SlaveTemplate t : templates)
            t.setParent(this);
        return this;
    }

    private static final Logger LOGGER = Logger.getLogger(JClouds.class.getName());

    public String getProvider() {
        return provider;
    }

    public String getUser() {
        return user;
    }
    public String getSecret() {
        return secret.getEncryptedValue();
    }

    @Override
    public boolean canProvision(Label label) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Collection<PlannedNode> provision(Label label, int i) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public ComputeService connect() throws AuthorizationException {

        ComputeService client = null;
        try {

            ComputeServiceContext context = new ComputeServiceContextFactory().createContext(provider, user, secret.getEncryptedValue());

            client = context.getComputeService();

        } catch (Exception from) {

            if (from.getCause() instanceof AuthorizationException) {
                throw new AuthorizationException(from.getCause());
            } else {
                Throwables.propagate(from);
            }
        }
        return client;
    }

    public static ComputeService getComputeService(String provider, String user, String secret)
            throws AuthorizationException, Throwable {

        ComputeService client = null;
        try {

            ComputeServiceContext context = new ComputeServiceContextFactory().createContext(provider, user, secret);

            client = context.getComputeService();
            return client;

        } catch (Exception from) {

            if (from.getCause() instanceof AuthorizationException) {
                throw new AuthorizationException(from.getCause());


            } else {
                Throwables.propagate(from);

            }
        }
        return client;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return "JClouds";
        }

        public Set<String> getSupportedProviders() {

            return ComputeUtils.getSupportedProviders();
        }

        public FormValidation doTestConnection(
                @QueryParameter String provider,
                @QueryParameter String user,
                @QueryParameter String secret) throws ServletException, IOException, Throwable {


            ComputeService client = null;
            try {
                client = getComputeService(provider, user, secret);

            } catch (AuthorizationException ex) {
                return FormValidation.error("Authentication Error: " + ex.getLocalizedMessage());
            }
            //Set<? extends ComputeMetadata> nodes = Sets.newHashSet(connection.getNodes().values());

            for (Image image : client.getImages().values()) {
                if (image != null) {
                    LOGGER.log(Level.INFO, "image: {0}|{1}|{2}:{3}", new Object[]{
                                image.getArchitecture(),
                                image.getOsFamily(),
                                image.getOsDescription(),
                                image.getDescription()
                            });
                    LOGGER.log(Level.INFO, "image: {0}", image.toString());
                }
            }
            for (Size size : client.getSizes().values()) {
                if (size != null) {
                    LOGGER.log(Level.INFO, "size: {0}", size.toString());

                }
            }
            for (ComputeMetadata node : client.getNodes().values()) {
                if (node != null) {
                    LOGGER.log(Level.INFO, "Node {0}:{1} in {2}", new Object[]{
                                node.getId(),
                                node.getName(),
                                node.getLocation().getId()});
                }
            }
            return FormValidation.ok();


        }
    }
}
