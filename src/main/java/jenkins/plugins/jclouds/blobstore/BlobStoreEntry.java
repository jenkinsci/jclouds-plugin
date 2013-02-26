package jenkins.plugins.jclouds.blobstore;

/**
 * A simple "bean" for blobstore entries.
 *
 * @author Vijay Kiran
 */
public final class BlobStoreEntry {
   /**
    * The container where the file is saved.
    * See http://www.jclouds.org/documentation/userguide/blobstore-guide#container
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
    * Whether or not the sourceFile's path relative to the workspace should be
    * preserved upon upload to the Blobstore.
    */
   public boolean keepHierarchy;
}
