package hudson.plugins.jclouds;

import static org.testng.Assert.assertEquals;
import hudson.util.FormValidation;


import java.util.Set;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * 
 * @author Monty Taylor
 */
@Test(groups = { "integration", "live" }, sequential = true, testName = "hudson.JCloudsCloudLiveTest")
public class JCloudsTemplateLiveTest {

   private String provider;
   private String identity;
   private String credential;

   @BeforeTest
   @Parameters( { "jclouds.test.provider", "jclouds.test.identity", "jclouds.test.credential" })
   public void setUp(String provider, String identity, String credential) {
      this.provider = provider;
      this.identity = identity;
      this.credential = credential;
   }

   public void testDescriptorImpl() throws ServletException, IOException, Throwable {
      JCloudTemplate.DescriptorImpl desc = new JCloudTemplate.DescriptorImpl();
      Set<String> OsFamilies = desc.getSupportedOsFamilies();
      assertEquals(OsFamilies.contains("UBUNTU"), true);
   }
   

   @AfterTest
   protected void cleanup() throws InterruptedException, ExecutionException, TimeoutException {
      // TODO
   }

}
