package hudson.plugins.jclouds;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.rest.AuthorizationException;
import org.jclouds.ssh.jsch.config.JschSshClientModule;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.collect.ImmutableSet;

/**
 * 
 * @author mordred
 */
public class JCloudsCloud extends Cloud {

   private final String provider;
   private final String identity;
   private final Secret credential;
   private List<JCloudTemplate> templates;
   /**
    * Upper bound on how many instances we may provision.
    */
   public final int instanceCap;

   @DataBoundConstructor
   public JCloudsCloud(String provider, String identity, String credential, String instanceCapStr,
         List<JCloudTemplate> templates) {
      super(String.format("jclouds-{0}-{1}", new Object[] { provider, identity }));
      this.provider = provider;
      this.identity = identity;
      this.credential = Secret.fromString(credential.trim());
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

   private static final Logger LOGGER = Logger.getLogger(JCloudsCloud.class.getName());

   public String getProvider() {
      return provider;
   }

   public String getUser() {
      return identity;
   }

   public String getSecret() {
      return credential.getEncryptedValue();
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
         if (node.getId() != null) {
            n++;
         }
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

         StringWriter sw = new StringWriter();
         List<JCloudSlave> slaves = t.provision(new StreamTaskListener(sw), calculateNodesToLaunch(requestedWorkload));

         List<PlannedNode> r = new ArrayList<PlannedNode>();
         for (final JCloudSlave slave : slaves) {

            r.add(new PlannedNode(t.getDescription(), Computer.threadPoolForRemoting.submit(new Callable<Node>() {

               public Node call() throws Exception {
                  // TODO: record the output somewhere
                  try {

                     Hudson.getInstance().addNode(slave);
                     // Instances may have a long init script. If we declare
                     // the provisioning complete by returning without the
                     // connect
                     // operation, NodeProvisioner may decide that it still
                     // wants
                     // one more instance, because it sees that (1) all the
                     // slaves
                     // are offline (because it's still being launched) and
                     // (2) there's no capacity provisioned yet.
                     //
                     // deferring the completion of provisioning until the
                     // launch
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

   protected ComputeService connect() throws AuthorizationException, Throwable {
      return getComputeService(provider, identity, credential.getEncryptedValue());
   }

   /**
    * Gets the first {@link JCloudsCloud} instance configured in the current
    * Hudson, or null if no such thing exists.
    */
   public static JCloudsCloud get() {
      return Hudson.getInstance().clouds.get(JCloudsCloud.class);
   }

   /**
    * Gets the named cloud
    * 
    * @param name
    *           name of cloud to get
    * @return JClouds instance matching name
    */
   public static JCloudsCloud get(String name) {
      for (JCloudsCloud j : Hudson.getInstance().clouds.getAll(JCloudsCloud.class)) {
         if (j.name.matches(name)) {
            return j;
         }
      }
      return null;
   }

   public static ComputeService getComputeService(String provider, String identity, String credential)
         throws AuthorizationException, IOException {

      ComputeService client = null;

      ComputeServiceContext context = new ComputeServiceContextFactory().createContext(provider, identity, credential,
            ImmutableSet.of(new JschSshClientModule()));

      client = context.getComputeService();

      return client;
   }

   public static class DescriptorImpl extends Descriptor<Cloud> {

      public FormValidation doTestConnection(@QueryParameter String provider, @QueryParameter String identity,
            @QueryParameter String credential) throws ServletException, IOException, Throwable {

         ComputeService client = null;
         try {
            client = getComputeService(provider, identity, credential);

         } catch (AuthorizationException ex) {
            return FormValidation.error("Authentication Error: " + ex.getLocalizedMessage());
         }
         // Set<? extends ComputeMetadata> nodes =
         // Sets.newHashSet(connection.getNodes().values());

         for (Image image : client.listImages()) {
            if (image != null) {
               LOGGER.log(Level.INFO, "image: {0}|{1}|{2}:{3}:{4}", new Object[] { image.getOperatingSystem().getArch(),
                     image.getOperatingSystem().getFamily(), image.getOperatingSystem().getDescription(), image.getDescription(), image.getId() });
               LOGGER.log(Level.INFO, "image: {0}", image.toString());
            }
         }
         for (Hardware size : client.listHardwareProfiles()) {
            if (size != null) {
               LOGGER.log(Level.INFO, "size: {0}", size.toString());

            }
         }
         for (ComputeMetadata node : client.listNodes()) {
            if (node != null) {
               LOGGER.log(Level.INFO, "Node {0}:{1} in {2}", new Object[] { node.getId(), node.getName(),
                     node.getLocation().getId() });
            }
         }
         return FormValidation.ok();

      }

      @Override
      public String getDisplayName() {
         return "jclouds generic";
      }
   };
}
