package jenkins.plugins.jclouds;

import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;


public class BlobstoreProfile {

   private String profileName;
   private String providerName;
   private String identity;
   private String credential;

   @DataBoundConstructor
   public BlobstoreProfile(final String profileName,
                           final String providerName,
                           final String identity,
                           final String credential) {
      this.profileName = profileName;
      this.providerName = providerName;
      this.identity = identity;
      this.credential = credential;
   }


   public String getProfileName() {
      return profileName;
   }

   public String getProviderName() {
      return providerName;
   }

   public String getIdentity() {
      return identity;
   }

   public String getCredential() {
      return credential;
   }

   public void upload(String bucketName, FilePath filePath) throws IOException, InterruptedException {
      if (filePath.isDirectory()) {
         throw new IOException(filePath + " is a directory");
      }

      System.out.println("Uploading File!!!");
   }


}
