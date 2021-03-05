/*
 * Copyright 2010-2016 Adrian Cole, Andrew Bayer, Fritz Elfert, Marat Mavlyutov, Monty Taylor, Vijay Kiran et. al.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    private void fastTerminate(final JCloudsComputer c) {
        if (!c.isOffline()) {
            LOGGER.info("Setting " + c.getName() + " to be deleted.");
            try {
                c.disconnect(OfflineCause.create(Messages._DeletedCause())).get();
            } catch (Exception e) {
                LOGGER.info("Caught " + e.toString());
            }
        }
        c.deleteSlave(true);
    }

    @Override
    public long check(JCloudsComputer c) {
        if (!checkLock.tryLock()) {
            return 1;
        } else {
            try {
                final JCloudsSlave node = c.getNode();
                // check isIdle() to ensure we are terminating busy slaves (including FlyWeight)
                if (null != node && c.isIdle()) {
                    if (node.isPendingDelete()) {
                        // Fixes JENKINS-28403
                        fastTerminate(c);
                    } else if (!node.isWaitPhoneHome()) {
                        OfflineCause oc = c.getOfflineCause();
                        if (null != oc && oc instanceof OfflineCause.LaunchFailed) {
                            final int errorRetentionTime = c.getErrorRetentionTime();
                            final long failedMilliseconds = System.currentTimeMillis() - oc.getTimestamp();
                            if (failedMilliseconds > TimeUnit2.MINUTES.toMillis(errorRetentionTime)) {
                                LOGGER.info(String.format("Error retention time of %d min for %s has expired.", errorRetentionTime, c.getName()));
                                node.setPendingDelete(true);
                                fastTerminate(c);
                            }
                        } else {
                            // Get the retention time, in minutes, from the JCloudsCloud this JCloudsComputer belongs to.
                            final int retentionTime = c.getRetentionTime();
                            if (retentionTime > -1) {
                                final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
                                if (idleMilliseconds > TimeUnit2.MINUTES.toMillis(retentionTime)) {
                                    LOGGER.info(String.format("Retention time of %d min for %s has expired.", retentionTime, c.getName()));
                                    node.setPendingDelete(true);
                                    fastTerminate(c);
                                }
                            }
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

    // Serialization
    protected Object readResolve() {
        checkLock = new ReentrantLock(false);
        return this;
    }

    private static final Logger LOGGER = Logger.getLogger(JCloudsRetentionStrategy.class.getName());
}
