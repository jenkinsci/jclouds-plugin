package jenkins.plugins.jclouds.blobstore;

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
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import org.jclouds.rest.AuthorizationException;

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
    private final List<BlobStoreEntry> entries;

    /**
     * Create a new Blobstore publisher for the cofigured profile identified by profileName
     *
     * @param profileName - the name of the configured profile name
     */
    @DataBoundConstructor
    public BlobStorePublisher(String profileName, List<BlobStoreEntry> entries) {
        super();
        if (profileName == null) {
            // defaults to the first one
            BlobStoreProfile[] sites = DESCRIPTOR.getProfiles();
            if (sites.length > 0)
                profileName = sites[0].getProfileName();
        }
        this.entries = entries;
        this.profileName = profileName;
    }

    /**
     * Get list of entries to be uploaded.
     *
     * @return
     */
    public List<BlobStoreEntry> getEntries() {
        return entries;
    }

    /**
     * @return - current profile for a profileName or returns the first one if the profileName isn't configured
     */
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

    /**
     * Perform the build step of uploading the configured file entries to the blobstore.
     * <p/>
     * <ul>
     * <li>If the build result is failure, will not do anything except logging the stuff.</li>
     * <li>If the blobstore profile isn't configured, or the uploading failed, the build is set to be unstable.</li>
     * <li>If the upload is succesful, the build is set to be stable.</li>
     * </ul>
     *
     * @param build    - reference to curerent build.
     * @param launcher - {@link Launcher}
     * @param listener - {@link BuildListener}
     * @return Always returns to indicate that build can continue, so we won't block other steps.
     * @throws InterruptedException
     * @throws IOException
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

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
                String expandedSource = Util.replaceMacro(blobStoreEntry.sourceFile, envVars);
                String expandedContainer = Util.replaceMacro(blobStoreEntry.container, envVars);
                FilePath ws = build.getWorkspace();
                FilePath[] paths = ws.list(expandedSource);
                String wsPath = ws.getRemote();

                if (paths.length == 0) {
                    // try to do error diagnostics
                    log(listener.getLogger(), "No file(s) found: " + expandedSource);
                    String error = ws.validateAntFileMask(expandedSource);
                    if (error != null)
                        log(listener.getLogger(), error);
                }
                for (FilePath src : paths) {
                    String expandedPath = getDestinationPath(blobStoreEntry.path, blobStoreEntry.keepHierarchy, wsPath, src, envVars);
                    log(listener.getLogger(), "container=" + expandedContainer + ", path=" + expandedPath + ", file=" + src.getName());
                    blobStoreProfile.upload(expandedContainer, expandedPath, src);
                }
            }
        } catch (AuthorizationException e) {
            LOGGER.severe("Failed to upload files to Blob Store due to authorization exception.");
            RuntimeException overrideException = new RuntimeException("Failed to upload files to Blob Store due to authorization exception.");
            overrideException.printStackTrace(listener.error("Failed to upload files"));
            build.setResult(Result.UNSTABLE);
        } catch (IOException e) {
            LOGGER.severe("Failed to upload files to Blob Store: " + e.getMessage());
            e.printStackTrace(listener.error("Failed to upload files"));
            build.setResult(Result.UNSTABLE);
        }

        return true;
    }

    private String getDestinationPath(String path, boolean appendFilePath, String wsPath, FilePath file, Map<String, String> envVars) {
        String resultPath;
        String expandedPath = "";
        String relativeFilePath = "";
        String fileFullPath = file.getParent().getRemote();
        if (path != null && !path.equals("")) {
            expandedPath = Util.replaceMacro(path, envVars);
            if (expandedPath.endsWith("/")) {
                expandedPath = expandedPath.substring(0, expandedPath.length() - 1);
            }
        }
        if (appendFilePath && fileFullPath.startsWith(wsPath)) {
            // Determine relative path to file relative to the workspace.
            relativeFilePath = fileFullPath.substring(wsPath.length());
            if (relativeFilePath.startsWith("/")) {
                relativeFilePath = relativeFilePath.substring(1);
            }
        }
        if (!expandedPath.equals("") && !relativeFilePath.equals("")) {
            resultPath = expandedPath + "/" + relativeFilePath;
        } else {
            resultPath = expandedPath + relativeFilePath;
        }

        // Strip leading and trailing slashes to play nice with object stores.
        if (resultPath.startsWith("/")) {
            resultPath = resultPath.substring(1);
        }
        if (resultPath.endsWith("/")) {
            resultPath = resultPath.substring(0, resultPath.length() - 1);
        }

        return resultPath;
    }

    /**
     * @return BuildStepMonitor.STEP
     * @{see BuildStepMonitor#STEP}
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    /**
     * {@see hudson.model.Descriptor}
     */
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
            BlobStoreProfile profile = new BlobStoreProfile(name, req.getParameter("providerName"), req.getParameter("identity"),
                    req.getParameter("credential"));
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public ListBoxModel doFillProfileNameItems() {
            ListBoxModel model = new ListBoxModel();
            for (BlobStoreProfile profile : getProfiles()) {
                model.add(profile.getProfileName());
            }
            return model;
        }
    }
}
