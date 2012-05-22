package jenkins.plugins.jclouds.compute;

import hudson.util.FormValidation;

import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;

import org.jclouds.crypto.SshKeys;

public class JCloudsCloudLiveTest extends TestCase {

   private ComputeTestFixture fixture;
   private JCloudsCloud cloud;
   private Map<String, String> generatedKeys;

   @Override
   public void setUp() throws Exception {
      super.setUp();
      fixture = new ComputeTestFixture();
      fixture.setUp();
      generatedKeys = SshKeys.generate();
      
      // TODO: this may need to vary per test
      cloud = new JCloudsCloud(
               fixture.getProvider() + "-profile", 
               fixture.getProvider(), 
               fixture.getIdentity(),
               fixture.getCredential(), 
               generatedKeys.get("private"), 
               generatedKeys.get("public"), 
               fixture.getEndpoint(), 
               1, 
               30, 
               600*1000,
               Collections.<JCloudsSlaveTemplate> emptyList());
   }

   public void testDoTestConnectionCorrectCredentialsEtc() {
      FormValidation result = new JCloudsCloud.DescriptorImpl().doTestConnection(
                                    fixture.getProvider(),
                                    fixture.getIdentity(),
                                    fixture.getCredential(), 
                                    generatedKeys.get("private"),
                                    fixture.getEndpoint());
      assertEquals("Connection succeeded!", result.getMessage());
   }

   @Override
   public void tearDown() {
      if (fixture != null)
         fixture.tearDown();
   }
}