package jenkins.plugins.jclouds.compute;

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.io.PrintStream;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.domain.LoginCredentials;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;

/**
 * The launcher that launches the jenkins slave.jar on the Slave. Uses the SSHKeyPair configured in the
 * cloud profile settings, and logs in to the server via SSH, and starts the slave.jar.
 *
 * @author Vijay Kiran
 */
public class JCloudsLauncher extends ComputerLauncher {
   private final int FAILED = -1;
   private final int SAMEUSER = 0;
   private final int RECONNECT = -2;


   /**
    * Launch the Jenkins Slave on the SlaveComputer.
    * @param computer
    * @param listener
    * @throws IOException
    * @throws InterruptedException
    */
   @Override
   public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {

      PrintStream logger = listener.getLogger();

      final Connection bootstrapConn;
      final Connection conn;
      Connection cleanupConn = null; // java's code path analysis for final doesn't work that well.
      boolean successful = false;
      final NodeMetadata nodeMetadata = ((JCloudsSlave) computer.getNode()).getNodeMetaData();

      try {
         bootstrapConn = connectToSsh(nodeMetadata, logger);
         int bootstrapResult = bootstrap(bootstrapConn, nodeMetadata, logger);
         if (bootstrapResult == FAILED)
            return; // bootstrap closed for us.
         else if (bootstrapResult == SAMEUSER)
            cleanupConn = bootstrapConn; // take over the connection
         else {
            // connect fresh as ROOT
            cleanupConn = connectToSsh(nodeMetadata, logger);
            LoginCredentials credentials = nodeMetadata.getCredentials();
            if (!cleanupConn.authenticateWithPublicKey(credentials.getUser(), credentials.getPrivateKey().toCharArray(), "")) {
               logger.println("Authentication failed");
               return; // failed to connect as root.
            }
         }
         conn = cleanupConn;

         SCPClient scp = conn.createSCPClient();
         logger.println("Copying slave.jar");
         scp.put(Hudson.getInstance().getJnlpJars("slave.jar").readFully(),
               "slave.jar", "/tmp");


         String launchString = "java  -jar /tmp/slave.jar";
         logger.println("Launching slave agent: " + launchString);
         final Session sess = conn.openSession();
         sess.execCommand(launchString);
         computer.setChannel(sess.getStdout(), sess.getStdin(), logger, new Channel.Listener() {
            @Override
            public void onClosed(Channel channel, IOException cause) {
               sess.close();
               conn.close();
            }
         });
         successful = true;
      } finally {
         if (cleanupConn != null && !successful)
            cleanupConn.close();
      }
   }

   /**
    * Authenticates using the bootstrapConn, tries to 20 times before giving up.
    * @param bootstrapConn
    * @param nodeMetadata - JClouds compute instance {@link NodeMetadata} for IP address and credentials.
    * @param logger
    * @return
    * @throws IOException
    * @throws InterruptedException
    */
   private int bootstrap(Connection bootstrapConn, NodeMetadata nodeMetadata, PrintStream logger) throws IOException, InterruptedException {
      boolean closeBootstrap = true;
      try {
         int tries = 20;
         boolean isAuthenticated = false;
         while (tries-- > 0) {
            LoginCredentials credentials = nodeMetadata.getCredentials();
            logger.println("Authenticating as " + credentials.getUser());
            isAuthenticated = bootstrapConn.authenticateWithPublicKey(credentials.getUser(),
                  credentials.getPrivateKey().toCharArray(), "");
            if (isAuthenticated) {
               break;
            }
            logger.println("Authentication failed. Trying again...");
            Thread.sleep(10000);
         }
         if (!isAuthenticated) {
            logger.println("Authentication failed");
            return FAILED;
         }
         closeBootstrap = false;
         return SAMEUSER;
      } finally {
         if (closeBootstrap)
            bootstrapConn.close();
      }
   }

    /**
     * Get the potential addresses to connect to, opting for public first and then private.
     */
    public static String[] getConnectionAddresses(NodeMetadata nodeMetadata, PrintStream logger) {
        if (nodeMetadata.getPublicAddresses().size() > 0) {
            return nodeMetadata.getPublicAddresses().toArray(new String[nodeMetadata.getPublicAddresses().size()]);
        } else {
            logger.println("No public addresses found, so using private address.");
            return nodeMetadata.getPrivateAddresses().toArray(new String[nodeMetadata.getPrivateAddresses().size()]);
        }
    }
    
   /**
    * Connect to SSH, and return the connection.
    * @param nodeMetadata - JClouds compute instance {@link NodeMetadata}, for credentials and the public IP.
    * @param logger - the logger where the log messages need to be sent.
    * @return - Connection - keeps trying forever, until the host closes the connection or we (the thread) die trying.
    * @throws InterruptedException
    */
   private Connection connectToSsh(NodeMetadata nodeMetadata, PrintStream logger) throws InterruptedException {
      while (true) {
         try {

             final String[] addresses = getConnectionAddresses(nodeMetadata, logger);
            String host = addresses[0];
            if ("0.0.0.0".equals(host)) {
               logger.println("Invalid host 0.0.0.0, your host is most likely waiting for an ip address.");
               throw new IOException("goto sleep");
            }

            logger.println("Connecting to " + host + " on port " + 22 + ". ");
            Connection conn = new Connection(host, 22);
            conn.connect(new ServerHostKeyVerifier() {
               public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {
                  return true;
               }
            });
            logger.println("Connected via SSH.");
            return conn; // successfully connected
         } catch (IOException e) {
            // keep retrying until SSH comes up
            logger.println("Waiting for SSH to come up. Sleeping 5.");
            Thread.sleep(5000);
         }
      }
   }

   @Override
   public Descriptor<ComputerLauncher> getDescriptor() {
      throw new UnsupportedOperationException();
   }

}
