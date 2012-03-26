package jenkins.plugins.jclouds;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Publishes artifacts to Blobstore configured using JClouds
 *
 * @author Vijay Kiran
 */
public class BlobStorePublisher extends Recorder implements Describable<Publisher> {

   private static final Logger LOGGER = Logger.getLogger(BlobStorePublisher.class.getName());

   private String profileName;
   @Extension
   public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
   private final List<BlobStoreEntry> entries = new ArrayList<BlobStoreEntry>();


   @DataBoundConstructor
   public BlobStorePublisher() {
      super();
   }

   public BlobStorePublisher(String profileName) {
      super();
      if (profileName == null) {
         // defaults to the first one
         BlobStoreProfile[] sites = DESCRIPTOR.getProfiles();
         if (sites.length > 0)
            profileName = sites[0].getProfileName();
      }
      this.profileName = profileName;
   }

   public List<BlobStoreEntry> getEntries() {
      return entries;
   }

   public BlobStoreProfile getProfile() {
      BlobStoreProfile[] profiles = DESCRIPTOR.getProfiles();

      if (profileName == null && profiles.length > 0)
         // default
         return profiles[0];

      for (BlobStoreProfile profile : profiles) {
         if (profile.getProfileName().equals(profileName))
            return profile;
      }
      return null;
   }

   public String getName() {
      return this.profileName;
   }

   public void setName(String profileName) {
      this.profileName = profileName;
   }

   protected void log(final PrintStream logger, final String message) {
      logger.println(StringUtils.defaultString(getDescriptor().getDisplayName()) + " " + message);
   }

   @Override
   public boolean perform(AbstractBuild<?, ?> build,
                          Launcher launcher,
                          BuildListener listener)
         throws InterruptedException, IOException {

      if (build.getResult() == Result.FAILURE) {
         // build failed. don't post
         LOGGER.info("Build failed, not publishing any files to blobstore");
         return true;
      }
      BlobStoreProfile blobStoreProfile = getProfile();
      if (blobStoreProfile == null) {
         log(listener.getLogger(), "No JClouds Blob Store blobStoreProfile is configured.");
         build.setResult(Result.UNSTABLE);
         return true;
      }
      log(listener.getLogger(), "Using JClouds blobStoreProfile: " + blobStoreProfile.getProfileName());
      try {
         Map<String, String> envVars = build.getEnvironment(listener);

         for (BlobStoreEntry blobStoreEntry : entries) {
            String expanded = Util.replaceMacro(blobStoreEntry.sourceFile, envVars);
            FilePath ws = build.getWorkspace();
            FilePath[] paths = ws.list(expanded);

            if (paths.length == 0) {
               // try to do error diagnostics
               log(listener.getLogger(), "No file(s) found: " + expanded);
               String error = ws.validateAntFileMask(expanded);
               if (error != null)
                  log(listener.getLogger(), error);
            }
            for (FilePath src : paths) {
               log(listener.getLogger(), "container=" + blobStoreEntry.container + ", file=" + src.getName());
               blobStoreProfile.upload(blobStoreEntry.container, src);
            }
         }
      } catch (IOException e) {
         LOGGER.severe("Failed to upload files to Blob Store: " + e.getMessage());
         e.printStackTrace(listener.error("Failed to upload files"));
         build.setResult(Result.UNSTABLE);
      }


      return true;
   }

   public BuildStepMonitor getRequiredMonitorService() {
      return BuildStepMonitor.STEP;
   }

   public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

      private final CopyOnWriteList<BlobStoreProfile> profiles = new CopyOnWriteList<BlobStoreProfile>();
      private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

      public DescriptorImpl(Class<? extends Publisher> clazz) {
         super(clazz);
         load();
      }

      public DescriptorImpl() {
         this(BlobStorePublisher.class);
      }

      @Override
      public String getDisplayName() {
         return "Publish artifacts to JClouds Clouds Storage ";
      }


      @Override
      public BlobStorePublisher newInstance(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
         BlobStorePublisher blobstorePublisher = new BlobStorePublisher();
         req.bindParameters(blobstorePublisher, "jcblobstore.");
         blobstorePublisher.getEntries().addAll(req.bindParametersToList(BlobStoreEntry.class, "jcblobstore.entry."));
         return blobstorePublisher;
      }

      @Override
      public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
         profiles.replaceBy(req.bindParametersToList(BlobStoreProfile.class, "jcblobstore."));
         save();
         return true;
      }

      public BlobStoreProfile[] getProfiles() {
         return profiles.toArray(new BlobStoreProfile[0]);
      }

      public FormValidation doLoginCheck(final StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
         String name = Util.fixEmpty(req.getParameter("name"));
         if (name == null) {// name is not entered yet
            return FormValidation.ok();
         }
         BlobStoreProfile profile = new BlobStoreProfile(name,
               req.getParameter("providerName"),
               req.getParameter("identity"),
               req.getParameter("credential"));
         return FormValidation.ok();
      }

      @Override
      public boolean isApplicable(Class<? extends AbstractProject> aClass) {
         return true;
      }

   }
}
