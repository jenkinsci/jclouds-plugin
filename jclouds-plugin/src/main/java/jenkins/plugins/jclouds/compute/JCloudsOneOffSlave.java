package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.slaves.OfflineCause;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

public class JCloudsOneOffSlave extends BuildWrapper {
    private static final Logger LOGGER = Logger.getLogger(JCloudsOneOffSlave.class.getName());

    @DataBoundConstructor
    public JCloudsOneOffSlave() {
    }

    //
    // convert Jenkins staticy stuff into pojos; performing as little critical stuff here as
    // possible, as this method is very hard to test due to static usage, etc.
    //
    @Override
    @SuppressWarnings("rawtypes")
    public Environment setUp(AbstractBuild build, Launcher launcher, final BuildListener listener) {
        if (JCloudsComputer.class.isInstance(build.getExecutor().getOwner())) {
            final JCloudsComputer c = (JCloudsComputer) build.getExecutor().getOwner();
            return new Environment() {
                @Override
                public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
                    LOGGER.warning("Single-use slave " + c.getName() + " getting torn down.");
                    c.setTemporarilyOffline(true, OfflineCause.create(Messages._OneOffCause()));
                    return true;
                }
            };
        } else {
            return new Environment() {
            };
        }

    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return "JClouds Single-Use Slave";
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(AbstractProject item) {
            return true;
        }

    }
}
