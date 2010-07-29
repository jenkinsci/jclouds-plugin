package hudson.plugins.jclouds;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Architecture;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.rest.AuthorizationException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Monty Taylor
 */
public class JCloudTemplate implements Describable<JCloudTemplate>  {

    private final String slave;
    private final String description;
    private final String remoteFS;
    private final String labels;
    private final String image;
    private final String architectureString;

    private String numExecutors;

    private transient /*almost final*/ Set<Label> labelSet;
    private transient Architecture architecture;


    private final String initScript;
    /*private final String userData;
    private final String remoteAdmin;
    private final String rootCommandPrefix;*/

    private transient JClouds parent;

    @DataBoundConstructor
    public JCloudTemplate(String slave, String description, /*String remoteFS,*/ String labelString, /*String image, */
            /*String architectureString, */ String numExecutors/* , String initScript, String userData, String remoteAdmin, String rootCommandPrefix*/)
    {
        this.slave = slave;
        this.description = description;
        this.remoteFS = "/var/lib/hudson";
        this.labels = Util.fixNull(labelString);
        this.image = null;
        this.architectureString = Architecture.X86_64.toString();
       // this.image = image;
      //  this.architectureString = architectureString;
        this.numExecutors = numExecutors;
        this.initScript = "aptitude update;  aptitude install -y sun-sun-java6-jdk ; mkdir -p /var/lib/hudson";
       /* this.userData = userData;
        this.remoteAdmin = remoteAdmin;
        this.rootCommandPrefix = rootCommandPrefix;*/
        readResolve();
    }


    private static final Logger LOGGER = Logger.getLogger(JClouds.class.getName());

    /**
     * Initializes data structure that we don't persist.
     */
    protected final Object readResolve() {
        labelSet = Label.parse(labels);
        architecture = Architecture.valueOf(architectureString);
        return this;
    }


    public String getDescription() {
        return description;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public String getLabels() {
        return labels;
    }

    public String getImage() {
        return image;
    }

    public String getSlave() {
        return slave;
    }

    public int getNumExecutors() {

        if (numExecutors == null)
            return 1;
        try {
            return Integer.parseInt(numExecutors);
        } catch (NumberFormatException e) {
            /*  @TODO: Make this based on number of CPUs (see EC2) */
            return 1;
        }
    }

    public Architecture getArchitecture() {
        if (architecture == null) {
            architecture= Architecture.valueOf(architectureString);
        }
        return architecture;
    }

    public void setNumExecutors(String numExecutors) {
        this.numExecutors = numExecutors;
    }

    void buildTemplate(JClouds parent)
    {
        this.parent = parent;
    }

    public JClouds getParent() {
        return parent;
    }

    public void setParent(JClouds parent) {
        this.parent = parent;
    }

    public static String getSshKey() throws IOException {
        File id_rsa_pub = new File(System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "id_rsa.pub");
        BufferedReader irp = new BufferedReader(new FileReader(id_rsa_pub));
        String line;
        String key = "";
        while ((line = irp.readLine()) != null) {
            key += line;
        }
        return key;
    }
    
    /**
     * Provisions a new Compute Service
     *
     * @return always non-null. This needs to be then added to {@link Hudson#addNode(Node)}.
     */
    public List<JCloudSlave> provision(TaskListener listener, int requestedWorkload) throws AuthorizationException, Throwable {
        PrintStream logger = listener.getLogger();


        try {
            logger.println("Launching " + slave);

            ComputeService client = getParent().connect();
            TemplateOptions options = new TemplateOptions();

            options.runScript(initScript.getBytes());
            options.inboundPorts(22, 8080);

            options.authorizePublicKey(getSshKey());


            TemplateBuilder builder = client.templateBuilder();

            builder.options(options);
            builder.architecture(getArchitecture());
            builder.osFamily(OsFamily.UBUNTU);
            builder.minRam(512);

            /* @TODO We should include our options here! */
            Set<? extends NodeMetadata> results = client.runNodesWithTag(slave, requestedWorkload, builder.build());


            /* Instance inst = ec2.runInstances(ami, 1, 1, Collections.<String>emptyList(), userData, keyPair.getKeyName(), type).getInstances().get(0);
            return newSlave(inst); */
            return newSlaves(results, client);
        } catch (Descriptor.FormException e) {
            throw new AssertionError(); // we should have discovered all configuration issues upfront
        }
    }

    private List<JCloudSlave> newSlaves(Set<? extends NodeMetadata> nodes, ComputeService client) throws Descriptor.FormException, IOException {

        List<JCloudSlave> slaves = new ArrayList<JCloudSlave>(nodes.size());
        for (NodeMetadata n : nodes)
        {

            /* @TODO: Actually create a real slave here */
            slaves.add(new JCloudSlave(n.getId(), getDescription(), getRemoteFS(), n.getLocation(), labels, client, n));
        }
        return slaves;
    }

    /**
     * Does this contain the given label?
     *
     * @param l
     *      can be null to indicate "don't care".
     */
    public boolean containsLabel(Label l) {
        return l==null || labelSet.contains(l);
    }

    public Descriptor<JCloudTemplate> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<JCloudTemplate> {

        @Override
        public String getDisplayName() {
            return null;
        }


        public ListBoxModel doFillImageItems(@QueryParameter String provider, @QueryParameter String user, @QueryParameter String secret) {

            LOGGER.log(Level.INFO, "Enter doFillImageItems");
            ListBoxModel m = new ListBoxModel();
            ComputeService client = null;
            try {
                client = JClouds.getComputeService(provider, user, secret);
            } catch (Throwable ex) {
                LOGGER.log(Level.INFO, "compute service problem {0}", ex.getLocalizedMessage());
                return m;
            }
            for (Image image : client.listImages()) {
                m.add(image.getDescription(), image.getId());

                    LOGGER.log(Level.INFO, "image: {0}|{1}|{2}:{3}", new Object[]{
                                image.getArchitecture(),
                                image.getOsFamily(),
                                image.getOsDescription(),
                                image.getDescription()
                            });
            }
            return m;
        }
    }
}
