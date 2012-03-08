package jenkins.plugins.jclouds;

import hudson.model.Descriptor;
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

    @Override
    public void launch(SlaveComputer _computer, TaskListener listener) throws IOException, InterruptedException {
        //TODO Launch the slave, and set channel
        LOGGER.info("TODO: :: Launching jclouds slave");

    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }


}
