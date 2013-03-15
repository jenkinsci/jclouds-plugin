package jenkins.plugins.jclouds.compute;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;
import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.*;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.statements.java.InstallJDK;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlaveTemplate implements Describable<JCloudsSlaveTemplate>, Supplier<NodeMetadata> {

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
   public final boolean stopOnTerminate;
   public final String vmUser;
   public final String vmPassword;
   public final boolean preInstalledJava;
   public final boolean preExistingJenkinsUser;
   private final String jenkinsUser;
   private final String fsRoot;
   public final boolean allowSudo;
   public final int overrideRetentionTime;
   public final int spoolDelayMs;
   private final Object delayLockObject = new Object();
   
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
                               final boolean stopOnTerminate,
                               final String vmPassword,
                               final String vmUser,
                               final boolean preInstalledJava,
                               final String jenkinsUser,
                               final boolean preExistingJenkinsUser,
                               final String fsRoot,
                               final boolean allowSudo,
                               final int overrideRetentionTime,
                               final int spoolDelayMs
                               ) {

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
       this.vmPassword = Util.fixEmptyAndTrim(vmPassword);
       this.vmUser = Util.fixEmptyAndTrim(vmUser);
       this.preInstalledJava = preInstalledJava;
       this.stopOnTerminate = stopOnTerminate;
       this.jenkinsUser = Util.fixEmptyAndTrim(jenkinsUser);
       this.preExistingJenkinsUser = preExistingJenkinsUser;
       this.fsRoot = Util.fixEmptyAndTrim(fsRoot);
       this.allowSudo = allowSudo;
       this.overrideRetentionTime = overrideRetentionTime;
       this.spoolDelayMs = spoolDelayMs;
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

   public String getJenkinsUser() {
       if (jenkinsUser == null || jenkinsUser.equals("")) {
           return "jenkins";
       } else {
           return jenkinsUser;
       }
   }

   public String getFsRoot() {
       if (fsRoot == null || fsRoot.equals("")) {
           return "/jenkins";
       } else {
           return fsRoot;
       }
   }
       
   
   public Set<LabelAtom> getLabelSet() {
      return labelSet;
   }

   public JCloudsSlave provisionSlave(TaskListener listener) throws IOException {
       NodeMetadata nodeMetadata = get();
       
       try {
           return new JCloudsSlave(getCloud().getDisplayName(), getFsRoot(), nodeMetadata, labelString, description,
                                   numExecutors, stopOnTerminate, overrideRetentionTime);
       } catch (Descriptor.FormException e) {
           throw new AssertionError("Invalid configuration " + e.getMessage());
       }
   }

   public NodeMetadata get() {
      LOGGER.info("Provisioning new jclouds node");

      ImmutableMap<String, String> userMetadata = ImmutableMap.of("Name", name);
      TemplateBuilder templateBuilder = getCloud().getCompute().templateBuilder();
      if (!Strings.isNullOrEmpty(imageId)) {
         LOGGER.info("Setting image id to " + imageId);
         templateBuilder.imageId(imageId);
      } else {
         if (!Strings.isNullOrEmpty(osFamily)) {
            LOGGER.info("Setting osFamily to " + osFamily);
            templateBuilder.osFamily(OsFamily.fromValue(osFamily));
         }
         if (!Strings.isNullOrEmpty(osVersion)) {
            LOGGER.info("Setting osVersion to " + osVersion);
            templateBuilder.osVersionMatches(osVersion);
         }
      }
      if (!Strings.isNullOrEmpty((hardwareId))) {
         LOGGER.info("Setting hardware Id to " + hardwareId);
         templateBuilder.hardwareId(hardwareId);
      } else {
         LOGGER.info("Setting minRam " + ram + " and minCores " + cores);
         templateBuilder.minCores(cores).minRam(ram);
      }

      Template template = templateBuilder.build();

      if (!Strings.isNullOrEmpty(vmPassword)) {
          LoginCredentials lc = LoginCredentials.builder().user(vmUser).password(vmPassword).build();
          template.getOptions().overrideLoginCredentials(lc);
      }

      if (spoolDelayMs > 0)
      {
          // (JENKINS-15970) Add optional delay before spooling. Author: Adam Rofer
          synchronized(delayLockObject)
          {
              LOGGER.info("Delaying " + spoolDelayMs + " milliseconds. Current ms -> " + System.currentTimeMillis());
              try
              {
                  Thread.sleep(spoolDelayMs);
              }
              catch (InterruptedException e)
              {
              }
          }
      }

      Statement initStatement = null;
      Statement bootstrap = null;
      
      if (this.preExistingJenkinsUser) {
          if( this.initScript.length() > 0 ) {
    	    initStatement = Statements.exec(this.initScript);
          }
      } else {
	      // setup the jcloudTemplate to customize the nodeMetadata with jdk, etc. also opening ports
	      AdminAccess adminAccess = AdminAccess.builder().adminUsername(getJenkinsUser())
	          .installAdminPrivateKey(false) // no need
	          .grantSudoToAdminUser(allowSudo) // no need
	          .adminPrivateKey(getCloud().privateKey) // temporary due to jclouds bug
	          .authorizeAdminPublicKey(true)
	          .adminPublicKey(getCloud().publicKey)
                  .adminHome(getFsRoot())
	          .build();


	      // Jenkins needs /jenkins dir.
	      Statement jenkinsDirStatement = Statements.newStatementList(Statements.exec("mkdir -p "+getFsRoot()), Statements.exec("chown "+getJenkinsUser()+" "+getFsRoot()));

          initStatement = newStatementList(adminAccess, jenkinsDirStatement, Statements.exec(this.initScript));
      }

      if (preInstalledJava) {
          bootstrap = initStatement;
      } else {
          bootstrap = newStatementList(initStatement, InstallJDK.fromOpenJDK());
      }
      
      template.getOptions()
            .inboundPorts(22)
            .userMetadata(userMetadata);

      if( bootstrap != null )
            template.getOptions().runScript(bootstrap);

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
   public static final class  DescriptorImpl extends Descriptor<JCloudsSlaveTemplate> {

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
               // note: string form of osFamily is lower-hyphen
               candidates.add(osFamily.toString());
            }
         }
         return candidates;
      }

      public FormValidation doCheckNumExecutors(@QueryParameter String value) {
          return FormValidation.validatePositiveInteger(value);
      }

      public ListBoxModel doFillImageIdItems(@RelativePath("..") @QueryParameter String providerName,
                                             @RelativePath("..") @QueryParameter String identity,
                                             @RelativePath("..") @QueryParameter String credential,
                                             @RelativePath("..") @QueryParameter String endPointUrl) {
         return doFillItems(providerName, identity, credential, endPointUrl, new ImageBehavior());
      }

      public ListBoxModel doFillHardwareIdItems(@RelativePath("..") @QueryParameter String providerName,
                                                @RelativePath("..") @QueryParameter String identity,
                                                @RelativePath("..") @QueryParameter String credential,
                                                @RelativePath("..") @QueryParameter String endPointUrl) {
         return doFillItems(providerName, identity, credential, endPointUrl, new HardwareBehavior());
      }

      private ListBoxModel doFillItems(String providerName,
                                       String identity,
                                       String credential,
                                       String endPointUrl,
                                       ComputeMetadataBehavior computeMetadataBehavior) {

         ListBoxModel listBoxModel = new ListBoxModel();

         if (Strings.isNullOrEmpty(identity)) {
            LOGGER.warning("identity is null or empty");
            return listBoxModel;
         }
         if (Strings.isNullOrEmpty(credential)) {
            LOGGER.warning("credential is null or empty");
            return listBoxModel;
         }
         if (Strings.isNullOrEmpty(providerName)) {
            LOGGER.warning("providerName is null or empty");
            return listBoxModel;
         }

         // Remove empty text/whitespace from the fields.
         providerName = Util.fixEmptyAndTrim(providerName);
         identity = Util.fixEmptyAndTrim(identity);
         credential = Util.fixEmptyAndTrim(credential);
         endPointUrl = Util.fixEmptyAndTrim(endPointUrl);

         ComputeService computeService = null;
         listBoxModel.add("None specified", "");
         try {
            // TODO: endpoint is ignored
            computeService = JCloudsCloud.ctx(providerName, identity, credential, endPointUrl).getComputeService();
            Set<? extends ComputeMetadata> computeMetadata = computeMetadataBehavior.list(computeService);
            for (ComputeMetadata metadata : computeMetadata) {
               listBoxModel.add(String.format("%s (%s)", metadata.getId(), metadata.getName()), metadata.getId());
            }
         } catch (Exception ex) {

         } finally {
            if (computeService != null) {
               computeService.getContext().close();
            }
         }

         return listBoxModel;
      }

      interface ComputeMetadataBehavior {
         public String getName();
         public Set<? extends ComputeMetadata> list(ComputeService computeService);
      }

      private class ImageBehavior implements ComputeMetadataBehavior {
         public String getName() { return Image.class.getSimpleName(); }
         public Set<? extends Image> list(ComputeService computeService) {
            return computeService.listImages();
         }
      }

      private class HardwareBehavior implements ComputeMetadataBehavior {
         public String getName() { return Hardware.class.getSimpleName(); }
         public Set<? extends Hardware> list(ComputeService computeService) {
            return computeService.listHardwareProfiles(); // FIXME: should be a ImmutableSortedSet.copyOf() ?
         }
      }

      public FormValidation doValidateImageId(@QueryParameter String providerName,
                                              @QueryParameter String identity,
                                              @QueryParameter String credential,
                                              @QueryParameter String endPointUrl,
                                              @QueryParameter String imageId) {
         return doValidateId(providerName, identity, credential, endPointUrl, imageId, new ImageBehavior());
      }

      public FormValidation doValidateHardwareId(@QueryParameter String providerName,
                                                 @QueryParameter String identity,
                                                 @QueryParameter String credential,
                                                 @QueryParameter String endPointUrl,
                                                 @QueryParameter String hardwareId) {
         return doValidateId(providerName, identity, credential, endPointUrl, hardwareId, new HardwareBehavior());
      }

      private FormValidation doValidateId(String providerName,
                                          String identity,
                                          String credential,
                                          String endPointUrl,
                                          String thingId,
                                          ComputeMetadataBehavior computeMetadataBehavior) {

         if (Strings.isNullOrEmpty(identity))
            return FormValidation.error("Invalid identity (AccessId).");
         if (Strings.isNullOrEmpty(credential))
            return FormValidation.error("Invalid credential (secret key).");
         if (Strings.isNullOrEmpty(providerName))
            return FormValidation.error("Provider Name shouldn't be empty");
         String computeMetadataName = computeMetadataBehavior.getName();
         if (Strings.isNullOrEmpty(thingId)) {
            return FormValidation.error(String.format("%s Id shouldn't be empty", computeMetadataName));
         }

         // Remove empty text/whitespace from the fields.
         providerName = Util.fixEmptyAndTrim(providerName);
         identity = Util.fixEmptyAndTrim(identity);
         credential = Util.fixEmptyAndTrim(credential);
         endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
         thingId = Util.fixEmptyAndTrim(thingId);

         FormValidation result = FormValidation.error(
               String.format("Invalid %s Id, please check the value and try again.", computeMetadataName));
         ComputeService computeService = null;
         try {
            // TODO: endpoint is ignored
            computeService = JCloudsCloud.ctx(providerName, identity, credential, endPointUrl).getComputeService();
            Set<? extends ComputeMetadata> computeMetadata = computeMetadataBehavior.list(computeService);
            for (ComputeMetadata metadata : computeMetadata) {
               String metadataId = metadata.getId();
               if (!metadataId.equals(thingId)) {
                  if (metadataId.contains(thingId)) {
                     result = FormValidation.warning(String.format("Sorry cannot find the %s id, " +
                           "Did you mean: %s ?\n" + metadata, computeMetadataName, metadataId));
                  }
               } else {
                  result = FormValidation.ok(String.format("%s Id is valid.", computeMetadataName));
               }
            }

         } catch (Exception ex) {
            result = FormValidation.error(String.format("Unable to check the %s id, " +
                  "please check if the credentials you provided are correct.", computeMetadataName), ex);
         } finally {
            if (computeService != null) {
               computeService.getContext().close();
            }
         }
         return result;
      }
   }
}
