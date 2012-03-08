package jenkins.plugins.jclouds;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import org.jclouds.compute.domain.NodeMetadata;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlave extends Slave {
    private static final Logger LOGGER = Logger.getLogger(JCloudsComputer.class.getName());


    public JCloudsSlave(NodeMetadata metadata) throws IOException, Descriptor.FormException {
        this(metadata.getName(),
                "jclouds-jenkins-node",
                "/jenkins",
                "1",
                Mode.NORMAL,
                "labelString",
                new JCloudsLauncher(),
                new JCloudsRetentionStrategy(),
                null);
    }


    @DataBoundConstructor
    public JCloudsSlave(String name,
                        String nodeDescription,
                        String remoteFS,
                        String numExecutors,
                        Mode mode,
                        String labelString,
                        ComputerLauncher launcher,
                        RetentionStrategy retentionStrategy,
                        List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
    }


    @Override
    public Computer createComputer() {
        LOGGER.info("Creating a JClouds Slave Computer");
        return new JCloudsComputer(this);
    }

    @Extension
    public static final class JCloudsSlaveDescriptor extends SlaveDescriptor {
        @Override
        public String getDisplayName() {
            return "JClouds Slave";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }
}
