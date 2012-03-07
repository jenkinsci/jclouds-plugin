package jenkins.plugins.jclouds;

import hudson.model.Slave;
import hudson.slaves.SlaveComputer;

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

}
