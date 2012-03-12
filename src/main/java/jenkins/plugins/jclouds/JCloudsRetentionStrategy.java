package jenkins.plugins.jclouds;

import hudson.model.Computer;
import hudson.slaves.RetentionStrategy;

/**
 * @author Vijay Kiran
 */
public class JCloudsRetentionStrategy extends RetentionStrategy<JCloudsComputer> {
    @Override
    public long check(JCloudsComputer c) {
    return 0;
    }

    /**
     * Try to connect to it ASAP.
     */
    @Override
    public void start(JCloudsComputer c) {
        c.connect(false);
    }
}
