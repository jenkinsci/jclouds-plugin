package hudson.plugins.jclouds;

import static org.testng.Assert.assertNotNull;

import java.io.IOException;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.util.ComputeServiceUtils;
import org.testng.annotations.Test;

/**
 * @author Kohsuke Kawaguchi
 */
@Test(groups = "unit")
public class SimpleTest {

   /**
    * Rigorous Test :-)
    */
   public void testCanUseStub() throws IOException {

      Iterable<String> providers = ComputeServiceUtils.getSupportedProviders();

      ComputeServiceContext context = new ComputeServiceContextFactory().createContext("stub", "foo", "bar");

      ComputeService client = context.getComputeService();

      // Set<? extends ComputeMetadata> nodes =
      // Sets.newHashSet(connection.getNodes().values());

      for (ComputeMetadata node : client.listNodes()) {
         assertNotNull(node.getId());
         assertNotNull(node.getLocation().getId()); // where in the
         // world is the node
      }
   }
}
