package jenkins.plugins.jclouds.compute;

import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.plugins.sshslaves.SSHLauncher;

import java.io.IOException;
import java.io.PrintStream;

import java.net.InetAddress;

import org.jclouds.compute.domain.NodeMetadata;

/**
 * The launcher that launches the jenkins slave.jar on the Slave. Uses the SSHKeyPair configured in the cloud profile settings, and logs in to the server via
 * SSH, and starts the slave.jar.
 *
 * @author Vijay Kiran
 */
public class JCloudsLauncher extends ComputerLauncher {

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
            final String[] addresses = getConnectionAddresses(slave.getNodeMetaData(), logger);
            slave.waitForPhoneHome(logger);

            String host = addresses[0];
            if (InetAddress.getByName(host).isAnyLocalAddress()) {
                logger.println("Invalid host 0.0.0.0, your host is most likely waiting for an ip address.");
                throw new IOException("goto sleep");
            }

            SSHLauncher launcher = new SSHLauncher(host, 22, slave.getCredentialsId(), slave.getJvmOptions(), null, "", "", Integer.valueOf(0), null, null);
            launcher.launch(computer, listener);
        } else {
            throw new IOException("Could not launch NULL slave.");
        }
    }

    /**
     * Get the potential addresses to connect to, opting for public first and then private.
     * @param nodeMetadata The meta data of the configured node.
     * @param logger Reference to a PrintStream for logging purposes.
     * @return An array of Strings containing the IP addresses.
     */
    public static String[] getConnectionAddresses(NodeMetadata nodeMetadata, PrintStream logger) {
        if (nodeMetadata.getPublicAddresses().size() > 0) {
            return nodeMetadata.getPublicAddresses().toArray(new String[nodeMetadata.getPublicAddresses().size()]);
        } else {
            logger.println("No public addresses found, so using private address.");
            return nodeMetadata.getPrivateAddresses().toArray(new String[nodeMetadata.getPrivateAddresses().size()]);
        }
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }

}
