package jenkins.plugins.jclouds.compute;

import hudson.model.Descriptor;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Vijay Kiran
 */
public class JCloudsRetentionStrategy extends RetentionStrategy<JCloudsComputer> {
    @DataBoundConstructor
    public JCloudsRetentionStrategy() {
    }

    @Override
    public synchronized long check(JCloudsComputer c) {
         if (c.isIdle() && !c.getNode().isPendingDelete() && !disabled) {
            // Get the retention time, in minutes, from the JCloudsCloud this JCloudsComputer belongs to.
            final int retentionTime = c.getRetentionTime();
            if (retentionTime > -1) {
                final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
                if (idleMilliseconds > TimeUnit2.MINUTES.toMillis(retentionTime)) {
                    LOGGER.info("Setting "+c.getName()+" to be deleted.");
                    if (!c.isOffline()) {
                        c.setTemporarilyOffline(true, OfflineCause.create(Messages._DeletedCause()));
                    }
                    c.getNode().setPendingDelete(true);
                }
            }
                
        }
        return 1;
    }
    
    /**
     * Try to connect to it ASAP.
     */
    @Override
    public void start(JCloudsComputer c) {
        c.connect(false);
    }

    // no registration since this retention strategy is used only for cloud nodes that we provision automatically.
    // @Extension
    public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "JClouds";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JCloudsRetentionStrategy.class.getName());

    public static boolean disabled = Boolean.getBoolean(JCloudsRetentionStrategy.class.getName()+".disabled");

}
