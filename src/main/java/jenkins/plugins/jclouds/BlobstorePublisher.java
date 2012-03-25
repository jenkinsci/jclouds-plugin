package jenkins.plugins.jclouds;

import hudson.Extension;
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
import java.util.logging.Logger;


/**
 * Publishes artifacts to Blobstore configured using JClouds
 *
 * @author Vijay Kiran
 */
public class BlobstorePublisher extends Recorder implements Describable<Publisher> {


   private String profileName;
   @Extension
   public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
   private final List<BlobStoreEntry> entries = new ArrayList<BlobStoreEntry>();


   @DataBoundConstructor
   public BlobstorePublisher() {
      super();
   }

   public BlobstorePublisher(String profileName) {
      super();
      if (profileName == null) {
         // TODO Set default as the first one
      }
      this.profileName = profileName;
   }

   public List<BlobStoreEntry> getEntries() {
      return entries;
   }

   public BlobstoreProfile getProfile() {
      BlobstoreProfile[] profiles = DESCRIPTOR.getProfiles();

      if (profileName == null && profiles.length > 0)
         // default
         return profiles[0];

      for (BlobstoreProfile profile : profiles) {
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

      //TODO Vijay: Perform build step (Upload) based on result

      if (build.getResult() == Result.FAILURE) {
         // build failed. don't post
         return true;
      }
      return true;
   }

   public BuildStepMonitor getRequiredMonitorService() {
      return BuildStepMonitor.STEP;
   }

   public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

      private final CopyOnWriteList<BlobstoreProfile> profiles = new CopyOnWriteList<BlobstoreProfile>();
      private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

      public DescriptorImpl(Class<? extends Publisher> clazz) {
         super(clazz);
         load();
      }

      public DescriptorImpl() {
         this(BlobstorePublisher.class);
      }

      @Override
      public String getDisplayName() {
         return "Publish artifacts to JClouds Clouds Storage ";
      }


      @Override
      public BlobstorePublisher newInstance(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
         BlobstorePublisher blobstorePublisher = new BlobstorePublisher();
         req.bindParameters(blobstorePublisher, "jcblobstore.");
         blobstorePublisher.getEntries().addAll(req.bindParametersToList(BlobStoreEntry.class, "jcblobstore.entry."));
         return blobstorePublisher;
      }

      @Override
      public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
         profiles.replaceBy(req.bindParametersToList(BlobstoreProfile.class, "jcblobstore."));
         save();
         return true;
      }

      public BlobstoreProfile[] getProfiles() {
         return profiles.toArray(new BlobstoreProfile[0]);
      }

      public FormValidation doLoginCheck(final StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
         String name = Util.fixEmpty(req.getParameter("name"));
         if (name == null) {// name is not entered yet
            return FormValidation.ok();
         }
         BlobstoreProfile profile = new BlobstoreProfile(name,
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
