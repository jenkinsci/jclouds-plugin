package jenkins.plugins.jclouds;

import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;


public class BlobstoreProfile {
   private String name;
   private String accessKey;
   private String secretKey;

   public BlobstoreProfile() {
   }

   @DataBoundConstructor
   public BlobstoreProfile(String name, String accessKey, String secretKey) {
      this.name = name;
      this.accessKey = accessKey;
      this.secretKey = secretKey;
   }

   public final String getAccessKey() {
      return accessKey;
   }

   public void setAccessKey(String accessKey) {
      this.accessKey = accessKey;
   }

   public final String getSecretKey() {
      return secretKey;
   }

   public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
   }

   public final String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }


   public void upload(String bucketName, FilePath filePath) throws IOException, InterruptedException {
      if (filePath.isDirectory()) {
         throw new IOException(filePath + " is a directory");
      }

      System.out.println("Uploading File!!!");
   }


}
