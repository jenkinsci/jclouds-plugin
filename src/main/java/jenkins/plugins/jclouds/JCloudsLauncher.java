package jenkins.plugins.jclouds;

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Vijay Kiran
 */
public class JCloudsLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(JCloudsLauncher.class.getName());

    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {

        LOGGER.info("In === JCloudsLauncher - launch " + computer);

    }
}
