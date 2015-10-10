package jenkins.plugins.jclouds.compute;

import hudson.remoting.VirtualChannel;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
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
     * Really deletes the slave, after terminating the instance.
     */
    @Override
    public HttpResponse doDoDelete() throws IOException {
        disconnect(OfflineCause.create(Messages._DeletedCause()));
        final JCloudsSlave node = getNode();
        if (null != node) {
            node.setPendingDelete(true);
        }
        return new HttpRedirect("..");
    }

    /**
     * Delete the slave, terminate the instance. Can be called either by doDoDelete() or from JCloudsRetentionStrategy.
     *
     * @throws InterruptedException
     */
    public void deleteSlave() throws IOException, InterruptedException {
        LOGGER.info("Terminating " + getName() + " slave");
        JCloudsSlave slave = getNode();
        if (null != slave ) {
            final VirtualChannel ch = slave.getChannel();
            if (null != ch) {
                ch.close();
            }
            slave.terminate();
            Jenkins.getActiveInstance().removeNode(slave);
        }
    }
}
