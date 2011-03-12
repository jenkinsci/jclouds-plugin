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
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.jdk.JDKLogger;
import org.jclouds.logging.jdk.JDKLogger.JDKLoggerFactory;
import org.jclouds.logging.jdk.config.JDKLoggingModule;
import org.jclouds.rest.AuthorizationException;
import org.jclouds.ssh.jsch.config.JschSshClientModule;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.inject.Module;

/**
 * 
 * @author mordred
 */
public class JCloudsCloud extends Cloud {

   private final String provider;
   private final String identity;
   private final Secret credential;
   private final String privateKey;

   private List<JCloudTemplate> templates;
   /**
    * Upper bound on how many instances we may provision.
    */
   public final int instanceCap;

   public JCloudsCloud(String provider, String identity, String credential, String instanceCapStr,
            List<JCloudTemplate> templates) {
      this(provider, identity, credential, null, instanceCapStr, templates);
   }

   public JCloudsCloud(String provider, String identity, String credential, String privateKey, String instanceCapStr,
            List<JCloudTemplate> templates) {
      super(String.format("jclouds-%s-%s", new Object[] { provider, identity }));
      this.provider = provider;
      this.identity = identity;
      this.credential = Secret.fromString(credential.trim());
      this.privateKey = privateKey;

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

   public String getIdentity() {
      return identity;
   }

   public String getCredential() {
      return credential.getPlainText();
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

   public void doProvision(StaplerRequest req, StaplerResponse rsp, @QueryParameter String slave)
            throws ServletException, IOException {
      checkPermission(PROVISION);
      if (slave == null) {
         sendError("The 'slave' query parameter is missing", req, rsp);
         return;
      }
      JCloudTemplate t = getTemplate(slave);
      if (t == null) {
         sendError("No such AMI: " + slave, req, rsp);
         return;
      }

      StringWriter sw = new StringWriter();
      StreamTaskListener listener = new StreamTaskListener(sw);
      try {
         /*
          * List<JCloudSlave> nodes = t.provision(listener, 1);
          * for (JCloudSlave node : nodes) {
          * Hudson.getInstance().addNode(node); }
          */
         JCloudSlave node = t.provision(listener);
         Hudson.getInstance().addNode(node);
         rsp.sendRedirect2(req.getContextPath() + "/computer/" + node.getNodeName());
      } catch (Exception e) {
         e.printStackTrace(listener.error(e.getMessage()));
         sendError(sw.toString(), req, rsp);
      } catch (Throwable throwable) {
         throwable.printStackTrace(); // To change body of catch statement use File | Settings |
                                      // File Templates.
      }
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
      return getComputeService(provider, identity, credential.getPlainText());
   }

   /**
    * Gets the first {@link JCloudsCloud} instance configured in the current Hudson, or null if no
    * such thing exists.
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

      Properties overrides = new Properties();

      Builder<Module> modules = ImmutableSet.<Module> builder();
      if (!"stub".equalsIgnoreCase(provider))
         modules.add(new JschSshClientModule());
      modules.add(new JDKLoggingModule() {

         @Override
         public JDKLoggerFactory createLoggerFactory() {
            return new JDKLoggerFactory() {
               @Override
               public org.jclouds.logging.Logger getLogger(String category) {
                  return "jclouds.compute".equals(category) ? bumpUpDebugLogger : super.getLogger(category);
               }
            };
         }

         final JDKLogger bumpUpDebugLogger = new JDKLogger(Logger.getLogger("jclouds.compute")) {
            @Override
            public void debug(String message, Object... args) {
               info(message, args);
            }

            @Override
            public boolean isDebugEnabled() {
               return true;
            }

         };
      }).add(new EnterpriseConfigurationModule());

      return new ComputeServiceContextFactory().createContext(provider, identity, credential, modules.build(),
               overrides).getComputeService();
   }

  public String getPrivateKey() {
    return privateKey;
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

/*
         for (Image image : client.listImages()) {
            if (image != null) {
               LOGGER.log(Level.INFO, "image: {0}|{1}|{2}:{3}:{4}", new Object[] {
                     image.getOperatingSystem().getArch(), image.getOperatingSystem().getFamily(),
                     image.getOperatingSystem().getDescription(), image.getDescription(), image.getId() });
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
*/
         return FormValidation.ok("This is a valid configuration");

      }

      @Override
      public String getDisplayName() {
         return "jclouds generic";
      }
   };
}
