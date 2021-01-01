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

import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.io.PrintStream;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jclouds.compute.domain.NodeMetadata;

import edazdarevic.commons.net.CIDRUtils;

/**
 * The launcher that launches the jenkins agent.jar on the Agent. Uses the SSHKeyPair configured in the cloud profile settings, and logs in to the server via
 * SSH, and starts the agent.jar.
 *
 * @author Vijay Kiran
 */
public class JCloudsLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(JCloudsLauncher.class.getName());

    private static void invokeSSHLauncher(final String address, final String credentialsId, final String jvmOptions,
                                          SlaveComputer agent, TaskListener listener) throws IOException {
        try {
            SSHLauncher launcher = new SSHLauncher(address, 22, credentialsId);
            launcher.setJvmOptions(jvmOptions);
            launcher.launch(agent, listener);
        } catch (Throwable t) {
            LOGGER.log(java.util.logging.Level.SEVERE, t.getMessage(), t);
            throw new IOException(t);
        }
    }

    /**
     * Launch the Jenkins Slave on the SlaveComputer.
     *
     * @param computer The node on which to launch the slave.
     * @param listener Task listener for notification purposes.
     * @throws IOException if an error occurs.
     * @throws InterruptedException if the launch gets interrupted.
     */
    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {

        PrintStream logger = listener.getLogger();

        final JCloudsSlave slave = (JCloudsSlave) computer.getNode();
        if (null != slave) {
            final String address = getConnectionAddress(slave.getNodeMetaData(), logger, slave.getPreferredAddress());
            slave.waitForPhoneHome(logger);
            LOGGER.info("launch resumed");

            if (InetAddress.getByName(address).isAnyLocalAddress()) {
                LOGGER.severe("Invalid address 0.0.0.0, your host is most likely waiting for an ip address.");
                logger.println("Invalid address 0.0.0.0, your host is most likely waiting for an ip address.");
                throw new IOException("goto sleep");
            }
            invokeSSHLauncher(address, slave.getCredentialsId(), slave.getJvmOptions(), computer, listener);

        } else {
            LOGGER.severe("Could not launch NULL slave.");
            throw new IOException("Could not launch NULL slave.");
        }
    }

    /**
     * Get the potential address to connect to, opting for public first and then private.
     * @param nodeMetadata The meta data of the configured node.
     * @param logger Reference to a PrintStream for logging purposes.
     * @param preferredAddress An optional String, containing an address/prefix expression which will be used for matching.
     * @return A String containing the IP address to connect to.
     */
    public static String getConnectionAddress(NodeMetadata nodeMetadata, PrintStream logger, final String preferredAddress) {
        if (null != preferredAddress && !preferredAddress.isEmpty()) {
            LOGGER.info("preferredAddress is " + preferredAddress);
            try {
                CIDRUtils cu = new CIDRUtils(preferredAddress);
                List<String> addrs = new ArrayList<>();
                addrs.addAll(nodeMetadata.getPublicAddresses());
                addrs.addAll(nodeMetadata.getPrivateAddresses());
                for (final String addr : addrs) {
                    if (null != addr && !addr.isEmpty() && cu.isInRange(addr)) {
                        LOGGER.info(addr + " matches against " + preferredAddress);
                        return addr;
                    }
                    LOGGER.info(addr + " does NOT match against " + preferredAddress);
                }
            } catch (UnknownHostException x) {
                if (null != logger) {
                    logger.println("Error during address match: " + x.getMessage());
                }
            }
            if (null != logger) {
                logger.println("Unable to match any address against " + preferredAddress + ". Falling back to simple selection.");
            }
        }
        if (nodeMetadata.getPublicAddresses().size() > 0) {
            return nodeMetadata.getPublicAddresses().iterator().next();
        } else {
            if (null != logger) {
                logger.println("No public addresses found, so using private address.");
            }
            return nodeMetadata.getPrivateAddresses().iterator().next();
        }
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }

}
