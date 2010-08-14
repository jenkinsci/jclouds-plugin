package hudson.plugins.jclouds;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static org.jclouds.io.Payloads.newByteArrayPayload;
import static org.jclouds.io.Payloads.newStringPayload;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

import java.io.File;
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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

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
    private final String osFamilyString;

    private String numExecutors;

    private transient /*almost final*/ Set<Label> labelSet;
    private transient Architecture architecture;
    private transient OsFamily os;


    private final String initScript;
    /*private final String userData;
    private final String remoteAdmin;
    private final String rootCommandPrefix;*/

    private transient JCloudsCloud parent;

    @DataBoundConstructor
    public JCloudTemplate(String slave, String description, /*String remoteFS,*/ String labelString, String osFamilyString,
    		/*String image, */
            String architectureString, String numExecutors/* , String initScript, String userData, String remoteAdmin, String rootCommandPrefix*/)
    {
        this.slave = slave;
        this.description = description;
        this.remoteFS = "/var/lib/hudson";
        this.labels = Util.fixNull(labelString);
        this.image = null;
        this.architectureString = architectureString;
        this.architecture = Architecture.valueOf(architectureString);
        this.osFamilyString = osFamilyString;
        this.os = OsFamily.valueOf(osFamilyString);
       // this.image = image;
      //  this.architectureString = architectureString;
        this.numExecutors = numExecutors;
        this.initScript = "aptitude update;  aptitude install -y sun-sun-java6-jdk ; mkdir -p /var/lib/hudson";
       /* this.userData = userData;
        this.remoteAdmin = remoteAdmin;
        this.rootCommandPrefix = rootCommandPrefix;*/
        readResolve();
    }


    private static final Logger LOGGER = Logger.getLogger(JCloudsCloud.class.getName());

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

    void buildTemplate(JCloudsCloud parent)
    {
        this.parent = parent;
    }

    public JCloudsCloud getParent() {
        return parent;
    }

    public void setParent(JCloudsCloud parent) {
        this.parent = parent;
    }

    public static String getSshKey() throws IOException {

        File id_rsa_pub = new File(System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "id_rsa.pub");
        return Files.toString(id_rsa_pub, UTF_8);
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

            options.runScript(newByteArrayPayload(initScript.getBytes()));
            options.inboundPorts(22, 8080);

            options.authorizePublicKey(newStringPayload(getSshKey()));


            TemplateBuilder builder = client.templateBuilder();

            builder.options(options);
            builder.architecture(getArchitecture());
            builder.osFamily(os);
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

	@SuppressWarnings("unchecked")
	public Descriptor<JCloudTemplate> getDescriptor() {
    	return Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<JCloudTemplate> {

        @Override
        public String getDisplayName() {
            return null;
        }

        public Set<String> getSupportedArchitectures() {
        	return newLinkedHashSet(transform(ImmutableSet.of(Architecture.values()),toStringFunction()));
        }
        
        public String getDefaultArchitecture() {
        	return "X86_64";
        }

        public Set<String> getSupportedOsFamilies() {
        	return newLinkedHashSet(transform(ImmutableSet.of(OsFamily.values()),new Function<OsFamily, String>(){

				public String apply(OsFamily from) {
					return from.name();
				}
        	
        	}));
        	
        	
        }

        public String getDefaultOsFamily() {
        	return "UBUNTU";
        }
        
		public ListBoxModel doFillImageItems(@QueryParameter String provider, @QueryParameter String user, @QueryParameter String secret) {

            LOGGER.log(Level.INFO, "Enter doFillImageItems");
            ListBoxModel m = new ListBoxModel();
            ComputeService client = null;
            try {
                client = JCloudsCloud.getComputeService(provider, user, secret);
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
    };
}
