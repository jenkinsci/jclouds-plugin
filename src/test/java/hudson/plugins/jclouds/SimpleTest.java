package hudson.plugins.jclouds;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import javax.xml.bind.JAXBException;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.util.ComputeUtils;
import org.jclouds.rest.AuthorizationException;

/**
 * @author Kohsuke Kawaguchi
 */
public class SimpleTest extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public SimpleTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(SimpleTest.class);
    }

    /**
     * Rigorous Test :-)
     */
    public void testApp() throws IOException {
        /**
         * @TODO: How does this property injection work?
         */
        @javax.inject.Named(value = "jclouds.test.user")
        String user= "mordred";
        @javax.inject.Named(value = "jclouds.test.key")
        String secret = "";

        boolean gotException = false;

            

        Set<String> providers= ComputeUtils.getSupportedProviders();
        
        try {
            ComputeServiceContext context = new ComputeServiceContextFactory().createContext("cloudservers", user, secret);

            ComputeService client = context.getComputeService();

            //Set<? extends ComputeMetadata> nodes = Sets.newHashSet(connection.getNodes().values());

            for (ComputeMetadata node : client.listNodes()) {
                System.err.println(node.getId());
                System.err.println(node.getLocation().getId()); // where in the world is the node
            }
        } catch (AuthorizationException ex) {

            gotException = true;

        }
        assertTrue(gotException);
    }
}
