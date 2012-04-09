package jenkins.plugins.jclouds.compute;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.statements.java.InstallJDK;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlaveTemplate implements Describable<JCloudsSlaveTemplate> {

   private static final Logger LOGGER = Logger.getLogger(JCloudsSlaveTemplate.class.getName());

   public final String name;
   public final String imageId;
   public final String hardwareId;
   public final double cores;
   public final int ram;
   public final String osFamily;
   public final String labelString;
   public final String description;
   public final String osVersion;
   public final String initScript;
   public final String numExecutors;
   public boolean stopOnTerminate;
   private transient Set<LabelAtom> labelSet;

   protected transient JCloudsCloud cloud;


   @DataBoundConstructor
   public JCloudsSlaveTemplate(final String name,
                               final String imageId,
                               final String hardwareId,
                               final double cores,
                               final int ram,
                               final String osFamily,
                               final String osVersion,
                               final String labelString,
                               final String description,
                               final String initScript,
                               final String numExecutors,
                               final boolean stopOnTerminate) {

       this.name = Util.fixEmptyAndTrim(name);
       this.imageId = Util.fixEmptyAndTrim(imageId);
       this.hardwareId = Util.fixEmptyAndTrim(hardwareId);
       this.cores = cores;
       this.ram = ram;
       this.osFamily = Util.fixNull(osFamily);
       this.osVersion = Util.fixNull(osVersion);
       this.labelString = Util.fixNull(labelString);
       this.description = Util.fixNull(description);
       this.initScript = Util.fixNull(initScript);
       this.numExecutors = Util.fixNull(numExecutors);
       this.stopOnTerminate = stopOnTerminate;
       
       readResolve();
   }


   public JCloudsCloud getCloud() {
      return cloud;
   }

   /**
    * Initializes data structure that we don't persist.
    */
   protected Object readResolve() {
      labelSet = Label.parse(labelString);
      return this;
   }

   public Set<LabelAtom> getLabelSet() {
      return labelSet;
   }

   public JCloudsSlave provision(TaskListener listener) throws IOException {
      LOGGER.info("Provisioning new node");
      NodeMetadata nodeMetadata = createNodeWithJdk();
      try {
          return new JCloudsSlave(getCloud().getDisplayName(), nodeMetadata, labelString, description, numExecutors, stopOnTerminate);
      } catch (Descriptor.FormException e) {
         throw new AssertionError("Invalid configuration " + e.getMessage());
      }


   }


   private NodeMetadata createNodeWithJdk() {
      LOGGER.info("creating jclouds node");

      ImmutableMap<String, String> userMetadata = ImmutableMap.of("Name", name);
      TemplateBuilder templateBuilder = getCloud().getCompute().templateBuilder();
      if (!Strings.isNullOrEmpty(imageId)) {
         LOGGER.info("Setting image id to " + imageId);
         templateBuilder.imageId(imageId);
      } else {
         if (!Strings.isNullOrEmpty(osFamily)) {
            LOGGER.info("Setting osFamily to " + osFamily);
            templateBuilder.osFamily(OsFamily.valueOf(osFamily));
         }
         if (!Strings.isNullOrEmpty(osVersion)) {
            LOGGER.info("Setting osVersion to " + osVersion);
            templateBuilder.osVersionMatches(osVersion);
         }
      }
      if (!Strings.isNullOrEmpty((hardwareId))) {
         LOGGER.info("Setting hardware Id to " + hardwareId);
      } else {
         LOGGER.info("Setting minRam " + ram + " and minCores " + cores);
         templateBuilder.minCores(cores).minRam(ram);
      }

      Template template = templateBuilder.build();

      // setup the jcloudTemplate to customize the nodeMetadata with jdk, etc. also opening ports
      AdminAccess adminAccess = AdminAccess.builder().adminUsername("jenkins")
          .installAdminPrivateKey(false) // no need
          .grantSudoToAdminUser(false) // no need
          .adminPrivateKey(getCloud().privateKey) // temporary due to jclouds bug
          .authorizeAdminPublicKey(true)
          .adminPublicKey(getCloud().publicKey)
          .build();


      // Jenkins needs /jenkins dir.
      Statement jenkinsDirStatement = Statements.newStatementList(Statements.exec("mkdir /jenkins"), Statements.exec("chown jenkins /jenkins"));

      Statement bootstrap = newStatementList(adminAccess, jenkinsDirStatement, Statements.exec(this.initScript), InstallJDK.fromOpenJDK());

      template.getOptions()
            .inboundPorts(22)
            .userMetadata(userMetadata)
            .runScript(bootstrap);

      NodeMetadata nodeMetadata = null;


      try {
         nodeMetadata = getOnlyElement(getCloud().getCompute().createNodesInGroup(name, 1, template));
      } catch (RunNodesException e) {
         throw destroyBadNodesAndPropagate(e);
      }

      //Check if nodeMetadata is null and throw
      return nodeMetadata;
   }

   private RuntimeException destroyBadNodesAndPropagate(RunNodesException e) {
      for (Map.Entry<? extends NodeMetadata, ? extends Throwable> nodeError : e.getNodeErrors().entrySet())
         getCloud().getCompute().destroyNode(nodeError.getKey().getId());
      throw propagate(e);
   }


   public Descriptor<JCloudsSlaveTemplate> getDescriptor() {
      return Jenkins.getInstance().getDescriptor(getClass());
   }

   @Extension
   public static final class DescriptorImpl extends Descriptor<JCloudsSlaveTemplate> {

      @Override
      public String getDisplayName() {
         return null;
      }

      public FormValidation doCheckCores(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }

      public FormValidation doCheckRam(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }

      public AutoCompletionCandidates doAutoCompleteOsFamily(@QueryParameter final String value) {
         OsFamily[] osFamilies = OsFamily.values();

         AutoCompletionCandidates candidates = new AutoCompletionCandidates();
         for (OsFamily osFamily : osFamilies) {
            if (StringUtils.containsIgnoreCase(osFamily.toString(), value)) {
               candidates.add(osFamily.toString().toUpperCase());
            }
         }
         return candidates;
      }

      public FormValidation doCheckNumExecutors(@QueryParameter String value) {
          return FormValidation.validatePositiveInteger(value);
      }

      public FormValidation doValidateImageId(
            @QueryParameter String providerName,
            @QueryParameter String identity,
            @QueryParameter String credential,
            @QueryParameter String imageId) {

         if (Strings.isNullOrEmpty(identity))
            return FormValidation.error("Invalid identity (AccessId).");
         if (Strings.isNullOrEmpty(credential))
            return FormValidation.error("Invalid credential (secret key).");
         if (Strings.isNullOrEmpty(providerName))
            return FormValidation.error("Provider Name shouldn't be empty");
         if (Strings.isNullOrEmpty(imageId)) {
            return FormValidation.error("Image Id shouldn't be empty");
         }

         // Remove empty text/whitespace from the fields.
         providerName = Util.fixEmptyAndTrim(providerName);
         identity = Util.fixEmptyAndTrim(identity);
         credential = Util.fixEmptyAndTrim(credential);
         imageId = Util.fixEmptyAndTrim(imageId);

         FormValidation result = FormValidation.error("Invalid Image Id, please check the value and try again.");
         ComputeService computeService = null;
         try {
            computeService = new ComputeServiceContextFactory()
                  .createContext(providerName, identity, credential).getComputeService();
            Set<? extends Image> images = computeService.listImages();
            for (Image image : images) {
               if (!image.getId().equals(imageId)) {
                  if (image.getId().contains(imageId)) {
                     return FormValidation.warning("Sorry cannot find the image id, " +
                           "Did you mean: " + image.getId() + "?\n" + image);
                  }
               } else {
                  return FormValidation.ok("Image Id is valid.");
               }
            }

         } catch (Exception ex) {
            result = FormValidation.error("Unable to check the image id, " +
                  "please check if the credentials you provided are correct.", ex);
         } finally {
            if (computeService != null) {
               computeService.getContext().close();
            }
         }
         return result;
      }

      public FormValidation doValidateHardwareId(
            @QueryParameter String providerName,
            @QueryParameter String identity,
            @QueryParameter String credential,
            @QueryParameter String hardwareId) {

         if (Strings.isNullOrEmpty(identity))
            return FormValidation.error("Invalid identity (AccessId).");
         if (Strings.isNullOrEmpty(credential))
            return FormValidation.error("Invalid credential (secret key).");
         if (Strings.isNullOrEmpty(providerName))
            return FormValidation.error("Provider Name shouldn't be empty");
         if (Strings.isNullOrEmpty(hardwareId)) {
            return FormValidation.error("Hardware Id shouldn't be empty");
         }

         // Remove empty text/whitespace from the fields.
         providerName = Util.fixEmptyAndTrim(providerName);
         identity = Util.fixEmptyAndTrim(identity);
         credential = Util.fixEmptyAndTrim(credential);
         hardwareId = Util.fixEmptyAndTrim(hardwareId);

         FormValidation result = FormValidation.error("Invalid Hardware Id, please check the value and try again.");
         ComputeService computeService = null;
         try {
            computeService = new ComputeServiceContextFactory()
                  .createContext(providerName, identity, credential).getComputeService();
            Set<? extends Hardware> hardwareProfiles = computeService.listHardwareProfiles();
            for (Hardware hardware : hardwareProfiles) {
               if (!hardware.getId().equals(hardwareId)) {
                  if (hardware.getId().contains(hardwareId)) {
                     return FormValidation.warning("Sorry cannot find the hardware id, " +
                           "Did you mean: " + hardware.getId() +  "?\n" + hardware);
                  }
               } else {
                  return FormValidation.ok("Hardware Id is valid.");
               }
            }

         } catch (Exception ex) {
            result = FormValidation.error("Unable to check the hardware id, " +
                  "please check if the credentials you provided are correct.", ex);
         } finally {
            if (computeService != null) {
               computeService.getContext().close();
            }
         }
         return result;
      }
   }
}
