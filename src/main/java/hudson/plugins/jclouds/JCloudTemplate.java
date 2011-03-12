package hudson.plugins.jclouds;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static org.jclouds.io.Payloads.newByteArrayPayload;

import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
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
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Location;
import org.jclouds.io.Payloads;
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
    private final String location;
    private final String remoteFS;
    private final String labels;
    private final String imageId;
    private final String architecture;
    private final String osFamily;

    private String numExecutors;

    private transient /*almost final*/ Set<LabelAtom> labelSet;
    private transient OsFamily os;
    private transient Image image;


    private final String initScript;
    /*private final String identityData;
    private final String remoteAdmin;
    private final String rootCommandPrefix;*/

    private transient JCloudsCloud parent;

  @DataBoundConstructor
    public JCloudTemplate(String slave, String description, String location, /*String remoteFS,*/ String labels, String osFamily, String imageId,
    		/*String image, */
            String architecture, String numExecutors/* , String initScript, String identityData, String remoteAdmin, String rootCommandPrefix*/)
    {
        this.slave = slave;
        this.description = description;
        this.location = location;
        this.remoteFS = "/var/lib/hudson";
        this.labels = Util.fixNull(labels);
//        this.image = Image;
        this.imageId = imageId;
        this.architecture = architecture;
        this.osFamily = osFamily;
        this.os = OsFamily.valueOf(osFamily);
       // this.image = image;
        this.numExecutors = numExecutors;
        this.initScript = "aptitude update;  aptitude install -y openjdk-6-jdk ; mkdir -p /var/lib/hudson";
//        this.identityData = identityData;
//        this.remoteAdmin = remoteAdmin;
//        this.rootCommandPrefix = rootCommandPrefix;
        readResolve();
    }


    private static final Logger LOGGER = Logger.getLogger(JCloudsCloud.class.getName());

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

    public String getLocation() {
        return location;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public String getLabels() {
        return labels;
    }

    public String getImageId() {
        return imageId;
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

    public String getArchitecture() {
        return architecture;
    }

    public OsFamily getOsFamily() {
      return (os!=null?os:OsFamily.valueOf(osFamily));
    }

/*
    public String getOsFamily() {
      return os.toString();
    }
*/

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
        System.out.println("RSA Public Key: " + Files.toString(id_rsa_pub, UTF_8));
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
          TemplateOptions options = client.templateOptions()
              .blockUntilRunning(false)
              .blockOnComplete(false)
//              .inboundPorts(22)
              .authorizePublicKey(getSshKey())
              .runScript( Payloads.newByteArrayPayload( initScript.getBytes() ));

//            options.runScript(newByteArrayPayload(initScript.getBytes()));
//            options.inboundPorts(22, 8080);

//            options.authorizePublicKey(getSshKey());


            TemplateBuilder builder = client.templateBuilder()
                .options(options)
                .osFamily(OsFamily.valueOf(osFamily))
                .minRam(512)
                .locationId( location );

//            builder.options(options);
//            builder.osFamily(OsFamily.valueOf(osFamily));
//            builder.osArchMatches(architecture);
//            builder.locationId("ORD1");  //TODO: Store and use location from config screen
            
//            builder.minRam(512);

            /* @TODO We should include our options here! */
            Set<? extends NodeMetadata> results = client.createNodesInGroup(slave, requestedWorkload, builder.build());



            /* Instance inst = ec2.runInstances(ami, 1, 1, Collections.<String>emptyList(), identityData, keyPair.getKeyName(), type).getInstances().get(0);
            return newSlave(inst); */
            return newSlaves(results, client);
        } catch (Descriptor.FormException e) {
            throw new AssertionError(); // we should have discovered all configuration issues upfront
        }
    }
    /**
     * Provisions a new Compute Service
     *
     * @return always non-null. This needs to be then added to {@link Hudson#addNode(Node)}.
     */

    public JCloudSlave provision(TaskListener listener) throws AuthorizationException, Throwable {
      return this.provision(listener, 1).get(0);
    }

    private List<JCloudSlave> newSlaves(Set<? extends NodeMetadata> nodes, ComputeService client) throws Descriptor.FormException, IOException {

        List<JCloudSlave> slaves = new ArrayList<JCloudSlave>(nodes.size());
        for (NodeMetadata n : nodes)
        {

            /* @TODO: Actually create a real slave here */
            slaves.add(new JCloudSlave(parent.getProvider(), n.getId(), getDescription(), getRemoteFS(), n.getLocation(), labels, client, n));
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
        
        public String getDefaultArchitecture() {
        	return "X86_64";
        }

        public Set<String> getSupportedOsFamilies() {
			return newLinkedHashSet(transform(ImmutableSet.copyOf(OsFamily.values()),new Function<OsFamily, String>(){

				public String apply(OsFamily from) {
					return from.name();
				}
        	
        	}));
        	
        	
        }

        public String getDefaultOsFamily() {
        	return "UBUNTU";
        }
        
		public ListBoxModel doFillImageIdItems(@RelativePath("..") @QueryParameter String provider, @RelativePath("..") @QueryParameter String identity, @RelativePath("..") @QueryParameter String credential) {

            LOGGER.log(Level.INFO, "Enter doFillImageItems {0}", provider + identity + credential);
            ListBoxModel m = new ListBoxModel();
            ComputeService client = null;
            try {
                client = JCloudsCloud.getComputeService(provider, identity, credential);
            } catch (Throwable ex) {
                LOGGER.log(Level.INFO, "compute service problem {0}", ex.getLocalizedMessage());
                return m;
            }
            for (Image image : client.listImages()) {
                m.add(image.getOperatingSystem().getDescription(), image.getId());

                    LOGGER.log(Level.INFO, "image: {0}|{1}|{2}:{3}", new Object[]{
                        image.getOperatingSystem().getArch(),
                        image.getOperatingSystem(),
                        image.getOperatingSystem().getDescription(),
                        image.getDescription()
                    });
            }
            return m;
        }

      public ListBoxModel doFillLocationItems(@RelativePath("..") @QueryParameter String provider, @RelativePath("..") @QueryParameter String identity, @RelativePath("..") @QueryParameter String credential) {

        LOGGER.log(Level.INFO, "Enter doFillLocationItems {0}", provider + identity + credential);
        ListBoxModel m = new ListBoxModel();
        ComputeService client = null;
        try {
          client = JCloudsCloud.getComputeService(provider, identity, credential);
        } catch (Throwable ex) {
          LOGGER.log(Level.SEVERE, "compute service problem {0}", ex.getLocalizedMessage());
          return m;
        }

        LOGGER.log(Level.INFO, "Populate Location {0}", provider + identity + credential);

        for (Location location : client.listAssignableLocations()) {
          m.add(location.getDescription(), location.getId());

          LOGGER.log(Level.INFO, "location: {0}|{1}", new Object[]{
              location.getId(),
              location.getDescription()
          });
        }
        return m;
      }
    }

}
