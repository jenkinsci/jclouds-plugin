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

import hudson.remoting.VirtualChannel;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

import org.jclouds.compute.domain.NodeMetadata;

import shaded.com.google.common.base.Joiner;
import shaded.com.google.common.collect.ImmutableSet;

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

    private Set<String> getIpAddresses(final boolean wantPublic) {
        final JCloudsSlave node = getNode();
        if (null != node) {
            final NodeMetadata md = node.getNodeMetaData();
            Set<String> ret = wantPublic ? md.getPublicAddresses() : md.getPrivateAddresses();
            if (!ret.isEmpty()) {
                return ret;
            }
        }
        return ImmutableSet.<String>of("None");
    }

    public String getPublicIpAddressHeader() {
        return "Public IP-Address" + (getIpAddresses(true).size() > 1 ? "es" : "");
    }

    public String getPrivateIpAddressHeader() {
        return "Private IP-Address" + (getIpAddresses(false).size() > 1 ? "es" : "");
    }

    public String getPublicIpAddresses() {
        return Joiner.on(" ").join(getIpAddresses(true));
    }

    public String getPrivateIpAddresses() {
        return Joiner.on(" ").join(getIpAddresses(false));
    }
}
