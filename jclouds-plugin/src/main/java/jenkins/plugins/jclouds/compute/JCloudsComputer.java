package jenkins.plugins.jclouds.compute;

import hudson.remoting.VirtualChannel;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

/**
 * JClouds version of Jenkins {@link SlaveComputer} - responsible for terminating an instance.
 *
 * @author Vijay Kiran
 */
public class JCloudsComputer extends AbstractCloudComputer<JCloudsSlave> {

    private static final Logger LOGGER = Logger.getLogger(JCloudsComputer.class.getName());

    public JCloudsComputer(JCloudsSlave slave) {
        super(slave);
    }

    public String getInstanceId() {
        return getName();
    }

    public int getRetentionTime() {
        final JCloudsSlave node = getNode();
        return null == node ? CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES : node.getRetentionTime();
    }

    @CheckForNull
    public String getCloudName() {
        final JCloudsSlave node = getNode();
        return null == node ? null : node.getCloudName();
    }

    /**
     * Deletes a jenkins slave node.
     * The not is first marked pending delete and the actual deletion will
     * be performed at the next run of {@link JCloudsCleanupThread}.
     * If called again after already being marked, the deletion is
     * performed immediately.
     */
    @Override
    public HttpResponse doDoDelete() throws IOException {
        disconnect(OfflineCause.create(Messages._DeletedCause()));
        final JCloudsSlave node = getNode();
        if (null != node) {
            if (node.isPendingDelete()) {
                // User attempts to delete an already delete-pending slave
                LOGGER.info("Slave already pendig delete: " + getName());
                deleteSlave(true);
            } else {
                node.setPendingDelete(true);
            }
        }
        return new HttpRedirect("..");
    }

    /**
     * Delete the slave, terminate or suspend the instance.
     * Can be called either by doDoDelete() or from JCloudsRetentionStrategy.
     * Whether the instance gets terminated or suspended is handled in
     * {@link JCloudsSlave#_terminate}
     *
     * @throws InterruptedException if the deletion gets interrupted.
     * @throws IOException if an error occurs.
     */
    public void deleteSlave() throws IOException, InterruptedException {
        if (isIdle()) { // Fixes JENKINS-27471
            LOGGER.info("Deleting slave: " + getName());
            JCloudsSlave slave = getNode();
            if (null != slave ) {
                final VirtualChannel ch = slave.getChannel();
                if (null != ch) {
                    ch.close();
                }
                slave.terminate();
                Jenkins.getInstance().removeNode(slave);
            }
        } else {
            LOGGER.info(String.format("Slave %s is not idle, postponing deletion", getName()));
            // Fixes JENKINS-28403
            final JCloudsSlave node = getNode();
            if (null != node && !node.isPendingDelete()) {
                node.setPendingDelete(true);
            }
        }
    }

    /**
     * Delete the slave, terminate or suspend the instance.
     * Like {@link #deleteSlave}, but catching all exceptions and logging the if desired.
     *
     * @param logging {@code true}, if exception logging is desired.
     */
    public void deleteSlave(final boolean logging) {
        try {
            deleteSlave();
        } catch (Exception e) {
            if (logging) {
                LOGGER.log(Level.WARNING, "Failed to delete slave", e);
            }
        }
    }

}
