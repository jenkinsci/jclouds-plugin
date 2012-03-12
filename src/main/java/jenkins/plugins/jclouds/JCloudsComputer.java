package jenkins.plugins.jclouds;

import hudson.model.Hudson;
import hudson.model.Slave;
import hudson.slaves.SlaveComputer;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Vijay Kiran
 */
public class JCloudsComputer extends SlaveComputer {

    private static final Logger LOGGER = Logger.getLogger(JCloudsComputer.class.getName());


    public JCloudsComputer(Slave slave) {
        super(slave);
    }

    @Override
    public Slave getNode() {
        return super.getNode();
    }

    /**
     * Really deletes the slave, by terminating the instance.
     */
    @Override
    public HttpResponse doDoDelete() throws IOException {
        LOGGER.info("Terminating " + getName()  + " slave");
        JCloudsSlave slave = (JCloudsSlave) getNode();
        slave.terminate();
        Hudson.getInstance().removeNode(slave);
        return new HttpRedirect("..");
    }
}
