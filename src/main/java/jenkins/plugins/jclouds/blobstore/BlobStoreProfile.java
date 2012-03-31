package jenkins.plugins.jclouds.blobstore;

import hudson.FilePath;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.BlobStoreContextFactory;
import org.jclouds.blobstore.InputStreamMap;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Model class for Blobstore profile. User can configure multiple profiles to upload artifacts to different providers.
 *
 * @author Vijay Kiran
 */
public class BlobStoreProfile {

   private static final Logger LOGGER = Logger.getLogger(BlobStoreProfile.class.getName());

   private String profileName;
   private String providerName;
   private String identity;
   private String credential;

   @DataBoundConstructor
   public BlobStoreProfile(final String profileName,
                           final String providerName,
                           final String identity,
                           final String credential) {
      this.profileName = profileName;
      this.providerName = providerName;
      this.identity = identity;
      this.credential = credential;
   }

   /**
    * Configured profile.
    *
    * @return - name of the profile.
    */
   public String getProfileName() {
      return profileName;
   }

   /**
    * Provider Name as per the JClouds Blobstore supported providers.
    *
    * @return - providerName String
    */
   public String getProviderName() {
      return providerName;
   }

   /**
    * Cloud provider identity.
    *
    * @return
    */
   public String getIdentity() {
      return identity;
   }

   /**
    * Cloud provider credential.
    *
    * @return
    */
   public String getCredential() {
      return credential;
   }

   /**
    * Upload the specified file from the {@param filePath} to container
    *
    * @param container - The container where the file need to uploaded.
    * @param filePath  - the {@link FilePath} of the file which needs to be uploaded.
    * @throws IOException
    * @throws InterruptedException
    */
   public void upload(String container, FilePath filePath) throws IOException, InterruptedException {
      if (filePath.isDirectory()) {
         throw new IOException(filePath + " is a directory");
      }

      final BlobStoreContext context = new BlobStoreContextFactory().createContext(this.providerName, this.identity, this.credential);
      try {
         final BlobStore blobStore = context.getBlobStore();
         if (!blobStore.containerExists(container)) {
            blobStore.createContainerInLocation(null, container);
         }
         InputStreamMap map = context.createInputStreamMap(container);
         map.put(filePath.getName(), filePath.read());
         LOGGER.info("Published " + filePath.getName() + " to container " + container + " with profile " + this.profileName);
      } finally {
         context.close();
      }
   }
}
