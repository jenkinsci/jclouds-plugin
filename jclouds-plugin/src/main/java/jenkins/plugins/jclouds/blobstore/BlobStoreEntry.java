package jenkins.plugins.jclouds.blobstore;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A simple "bean" for blobstore entries.
 *
 * @author Vijay Kiran
 */
public final class BlobStoreEntry extends AbstractDescribableImpl<BlobStoreEntry> {
    /**
     * The container where the file is saved. See http://www.jclouds.org/documentation/userguide/blobstore-guide#container
     */
    public String container;
    /**
     * The sub path under the container where the file is saved.
     */
    public String path;
    /**
     * The source file relative to the workspace directory, which needs to be uploaded to the container.
     */
    public String sourceFile;
    /**
     * Whether or not the sourceFile's path relative to the workspace should be preserved upon upload to the Blobstore.
     */
    public boolean keepHierarchy;

    @DataBoundConstructor
    public BlobStoreEntry(String container, String path, String sourceFile, boolean keepHierarchy) {
        this.container = container;
        this.path = path;
        this.sourceFile = sourceFile;
        this.keepHierarchy = keepHierarchy;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<BlobStoreEntry> {
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
