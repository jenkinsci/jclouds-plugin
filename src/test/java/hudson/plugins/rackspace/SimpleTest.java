package hudson.plugins.rackspace;

import java.io.IOException;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import javax.xml.bind.JAXBException;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.ComputeMetadata;

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
     * Rigourous Test :-)
     */
    public void testApp() throws JAXBException, IOException {
        @javax.inject.Named(value = "jclouds.rackspace.user")
        String user= "mordred";
        @javax.inject.Named(value = "jclouds.rackspace.key")
        String secret = "";

        ComputeServiceContext context = new ComputeServiceContextFactory().createContext("cloudservers", user, secret);

        ComputeService client = context.getComputeService();

        //Set<? extends ComputeMetadata> nodes = Sets.newHashSet(connection.getNodes().values());

        for (ComputeMetadata node : client.getNodes().values()) {
            System.err.println(node.getId());
            System.err.println(node.getLocationId()); // where in the world is the node
        }

    }
}