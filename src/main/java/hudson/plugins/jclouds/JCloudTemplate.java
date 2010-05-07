package hudson.plugins.jclouds;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.rest.AuthorizationException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Monty Taylor
 */
public class JCloudTemplate implements Describable<JCloudTemplate>  {

    private String slave;
    private String description;
    private String labels;
    private String image;

    private String numExecutors;

    private transient /*almost final*/ Set<Label> labelSet;

    private transient JClouds parent;

    @DataBoundConstructor
    public JCloudTemplate(String slave, String description, String labelString, String image, String numExecutors)
    {
        this.slave = slave;
        this.description = description;
        this.labels = Util.fixNull(labelString);
        this.image = image;
        this.numExecutors = numExecutors;
        readResolve();
    }


    private static final Logger LOGGER = Logger.getLogger(JClouds.class.getName());

    /**
     * Initializes data structure that we don't persist.
     */
    protected final Object readResolve() {
        labelSet = Label.parse(labels);
        return this;
    }
    
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    public String getSlave() {
        return slave;
    }

    public void setSlave(String slave) {
        this.slave = slave;
    }


    public int getNumExecutors() {
       
        try {
            return Integer.parseInt(numExecutors);
        } catch (NumberFormatException e) {
            /*  @TODO: Make this based on number of CPUs (see EC2) */
            return 1;
        }
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
            TemplateBuilder builder = client.templateBuilder();

            /* @TODO We should include our options here! */
            Map<String, ? extends NodeMetadata> results = client.runNodesWithTag(slave, requestedWorkload, builder.build());


            /* Instance inst = ec2.runInstances(ami, 1, 1, Collections.<String>emptyList(), userData, keyPair.getKeyName(), type).getInstances().get(0);
            return newSlave(inst); */
            return newSlaves(results);
        } catch (Descriptor.FormException e) {
            throw new AssertionError(); // we should have discovered all configuration issues upfront
        }
    }

    private List<JCloudSlave> newSlaves(Map<String, ? extends NodeMetadata> nodes) throws Descriptor.FormException, IOException {

        List<JCloudSlave> slaves = new ArrayList<JCloudSlave>(nodes.size());
        for (String n : nodes.keySet())
        {
            /* @TODO: Actually create a real slave here */
            slaves.add(new JCloudSlave(n, labels, nodes.get(n)));
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

    @Override
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
            for (Image image : client.getImages().values()) {
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
