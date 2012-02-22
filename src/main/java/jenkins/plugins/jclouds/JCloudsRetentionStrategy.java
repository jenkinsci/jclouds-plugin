package jenkins.plugins.jclouds;

import hudson.model.Computer;
import hudson.slaves.RetentionStrategy;

/**
 * @author Vijay Kiran
 */
public class JCloudsRetentionStrategy extends RetentionStrategy {
    @Override
    public long check(Computer c) {
        //TODO: Fix this - Vijay
        return 0;
    }
}
