package hudson.plugins.jclouds;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.Secret;

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
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
import org.jclouds.ssh.jsch.config.JschSshClientModule;
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
    private List<JCloudTemplate> templates;
    /**
     * Upper bound on how many instances we may provision.
     */
    public final int instanceCap;

    @DataBoundConstructor
    public JClouds(String provider, String user, String secret, String instanceCapStr, List<JCloudTemplate> templates) {
        super(String.format("jclouds-{0}-{1}", new Object[]{provider, user}));
        this.provider = provider;
        this.user = user;
        this.secret = Secret.fromString(secret.trim());
        if (instanceCapStr.equals("")) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }
        this.templates = templates;
        if (templates == null) {
            templates = Collections.emptyList();
        }
        readResolve();

    }

    private Object readResolve() {
        if (templates != null) {
            for (JCloudTemplate t : templates) {
                t.setParent(this);
            }
        }
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

    public String getInstanceCapStr() {
        if (instanceCap == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(instanceCap);
        }
    }

    public List<JCloudTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    public JCloudTemplate getTemplate(String slave) {
        for (JCloudTemplate t : templates) {
            if (t.getSlave().equals(slave)) {
                return t;
            }
        }
        return null;
    }

    public JCloudTemplate getTemplate(Label label) {
        for (JCloudTemplate t : templates) {
            if (t.containsLabel(label)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Counts the number of instances currently running.
     *
     * <p>
     * This includes those instances that may be started outside Hudson.
     */
    public int countCurrentSlaves() throws AuthorizationException, Throwable {
        int n = 0;
        for (ComputeMetadata node : connect().listNodes()) {
            n++;
        }
        return n;
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    private int calculateNodesToLaunch(int requestedWorkload) {
        int current = 0;
        try {
            current = countCurrentSlaves();
        } catch (Throwable ex) {
            return 0;
        }
        if (current >= instanceCap) {
            return 0;
        }
        int remaining = instanceCap - current;
        return remaining < requestedWorkload ? remaining : requestedWorkload;
    }

    @Override
    public Collection<PlannedNode> provision(Label label, int requestedWorkload) {

        try {

            final JCloudTemplate t = getTemplate(label);

            /* How many nodes should we spawn? */
            int toLaunch = calculateNodesToLaunch(requestedWorkload);
            StringWriter sw = new StringWriter();
            List<JCloudSlave> slaves = t.provision(new StreamTaskListener(sw),
                    calculateNodesToLaunch(requestedWorkload));

            List<PlannedNode> r = new ArrayList<PlannedNode>();
            for (final JCloudSlave slave : slaves) {

                r.add(new PlannedNode(t.getDescription(),
                        Computer.threadPoolForRemoting.submit(new Callable<Node>() {

                    public Node call() throws Exception {
                        // TODO: record the output somewhere
                        try {

                            Hudson.getInstance().addNode(slave);
                            // Instances may have a long init script. If we declare
                            // the provisioning complete by returning without the connect
                            // operation, NodeProvisioner may decide that it still wants
                            // one more instance, because it sees that (1) all the slaves
                            // are offline (because it's still being launched) and
                            // (2) there's no capacity provisioned yet.
                            //
                            // deferring the completion of provisioning until the launch
                            // goes successful prevents this problem.
                            slave.toComputer().connect(false).get();
                            return slave;
                        } catch (Throwable ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }), t.getNumExecutors()));
            }
            return r;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, e.getLocalizedMessage());
            return Collections.emptyList();
        }

    }

    protected ComputeService connect()
            throws AuthorizationException, Throwable {
        return getComputeService(provider, user, secret.getEncryptedValue());
    }

    /**
     * Gets the first {@link JClouds} instance configured in the current Hudson, or null if no such thing exists.
     */
    public static JClouds get() {
        return Hudson.getInstance().clouds.get(JClouds.class);
    }

    /**
     * Gets the named cloud
     * @param name name of cloud to get
     * @return JClouds instance matching name
     */
    public static JClouds get(String name) {
        for (JClouds j : Hudson.getInstance().clouds.getAll(JClouds.class)) {
            if (j.name.matches(name)) {
                return j;
            }
        }
        return null;
    }

    public static ComputeService getComputeService(String provider, String user, String secret)
            throws AuthorizationException, IOException {

        ComputeService client = null;


        ComputeServiceContext context = new ComputeServiceContextFactory().createContext(provider, user, secret,
                ImmutableSet.of(new JschSshClientModule()));

        client = context.getComputeService();

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

            for (Image image : client.listImages()) {
                if (image != null) {
                    LOGGER.log(Level.INFO, "image: {0}|{1}|{2}:{3}:{4}", new Object[]{
                                image.getArchitecture(),
                                image.getOsFamily(),
                                image.getOsDescription(),
                                image.getDescription(), image.getId()
                            });
                    LOGGER.log(Level.INFO, "image: {0}", image.toString());
                }
            }
            for (Size size : client.listSizes()) {
                if (size != null) {
                    LOGGER.log(Level.INFO, "size: {0}", size.toString());

                }
            }
            for (ComputeMetadata node : client.listNodes()) {
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
