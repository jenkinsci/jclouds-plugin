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
    * The source file relative to the workspace directory, which needs to be uploaded to the container.
    */
   public String sourceFile;
}
