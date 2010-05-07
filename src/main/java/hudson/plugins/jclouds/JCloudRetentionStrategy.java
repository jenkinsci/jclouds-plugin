/*
 *  The MIT License
 * 
 *  Copyright 2010 Monty Taylor
 * 
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 * 
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 * 
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package hudson.plugins.jclouds;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author mordred
 */
public class JCloudRetentionStrategy extends RetentionStrategy<JCloudComputer> {


    private static final Logger LOGGER = Logger.getLogger(JCloudRetentionStrategy.class.getName());

    @DataBoundConstructor
    public JCloudRetentionStrategy() {
    }

    @Override
    public synchronized long check(JCloudComputer c) {
        if (c.isIdle()) {
            // TODO: really think about the right strategy here
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > TimeUnit2.MINUTES.toMillis(30)) {
                LOGGER.log(Level.INFO, "Disconnecting {0}", c.getName());
                //c.getNode().terminate();
            }
        }
        return 1;
    }
    /**
     * Try to connect to it ASAP.
     */
    @Override
    public void start(JCloudComputer c) {
        c.connect(false);
    }

    // no registration since this retention strategy is used only for EC2 nodes that we provision automatically.
    // @Extension
    public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        public String getDisplayName() {
            return "JClouds";
        }
    }
}
