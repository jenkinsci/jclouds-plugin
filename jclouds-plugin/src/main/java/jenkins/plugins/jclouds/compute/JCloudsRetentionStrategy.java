package jenkins.plugins.jclouds.compute;

import hudson.model.Descriptor;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;

import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Vijay Kiran
 */
public class JCloudsRetentionStrategy extends RetentionStrategy<JCloudsComputer> {
    private transient ReentrantLock checkLock;

    @DataBoundConstructor
    public JCloudsRetentionStrategy() {
        readResolve();
    }

    @Override
    public long check(JCloudsComputer c) {
        if (!checkLock.tryLock()) {
            return 1;
        } else {
            try {
                if (c.isIdle() && !c.getNode().isPendingDelete() && !disabled) {
                    // Get the retention time, in minutes, from the JCloudsCloud this JCloudsComputer belongs to.
                    final int retentionTime = c.getRetentionTime();
                    // check executor to ensure we are terminating online slaves
                    if (retentionTime > -1 && c.countExecutors() > 0) {
                        final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
                        if (idleMilliseconds > TimeUnit2.MINUTES.toMillis(retentionTime)) {
                            LOGGER.info("Setting " + c.getName() + " to be deleted.");
                            if (!c.isOffline()) {
                                c.setTemporarilyOffline(true, OfflineCause.create(Messages._DeletedCause()));
                            }
                            c.getNode().setPendingDelete(true);
                        }
                    }
                }
            } finally {
                checkLock.unlock();
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

    protected Object readResolve() {
        checkLock = new ReentrantLock(false);
        return this;
    }

    private static final Logger LOGGER = Logger.getLogger(JCloudsRetentionStrategy.class.getName());

    public static boolean disabled = Boolean.getBoolean(JCloudsRetentionStrategy.class.getName() + ".disabled");

}
