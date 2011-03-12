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

import com.google.common.base.Predicate;
import com.google.common.io.NullOutputStream;
import com.jcraft.jsch.JSch;
import com.trilead.ssh2.Connection;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.domain.Credentials;
import org.jclouds.http.handlers.BackoffLimitedRetryHandler;
import org.jclouds.io.Payloads;
import org.jclouds.net.IPSocket;
import org.jclouds.predicates.RetryablePredicate;
import org.jclouds.ssh.SshClient;
import org.jclouds.ssh.jsch.JschSshClient;
import org.jclouds.predicates.InetSocketAddressConnect;

/**
 *
 * @author Monty Taylor
 */
public class JCloudComputerLauncher extends ComputerLauncher  {

    @Override
    public void launch(SlaveComputer _computer, TaskListener listener) {

        SshClient sshClient = null;
        boolean successful = false;

        try {
            JCloudComputer computer = (JCloudComputer) _computer;
            PrintStream logger = listener.getLogger();


            logger.println("Waiting for " + computer.getName() + " to launch");
            OUTER:
            while (true) {
                logger.println("State: " + computer.getState().toString());
                switch (computer.getState()) {
                    case PENDING:
                        Thread.sleep(5000); // check every 5 secs
                        continue OUTER;
                    case RUNNING:
                        break OUTER;
                    case TERMINATED:
                        // abort
                        logger.println("The instance " + computer.getInstanceId() + " appears to be shut down. Aborting launch.");
                        return;
                    default:
                        logger.println("Unknown state, waiting 5 seconds to check again");
                        Thread.sleep(5000); // check every 5 secs
                }
            }


            logger.println(computer.getInstanceId() + " launched. Initializing hudson");

            // TODO: parse the version number. maven-enforcer-plugin might help
            logger.println("Verifying that java exists");

            int ret;

            //TODO: Need to add this to the slave template config but that isn't working yet
            // This java instance is just to get the slave.jar working, build Java versions can be pushed by Jenkins
            ret = computer.executeScript("apt-get update; apt-get install -y openjdk-6-jdk", logger);
            if(ret != 0) {
                logger.println("Could not install Java on this platform");
            }

            ret = computer.executeScript("java -fullversion", logger);
            if(ret != 0) {
                logger.println("Java not found! Please install java as part of your node initialization");
            }

            // TODO: on Windows with ec2-sshd, this scp command ends up just putting slave.jar as c:\tmp
            // bug in ec2-sshd?

            logger.println("Copying slave.jar");


            IPSocket socket = new IPSocket(computer.describeNode().getPublicAddresses().toArray(new String[0])[0], 22);
            Predicate<IPSocket> socketOpen = new RetryablePredicate<IPSocket>(
                    new InetSocketAddressConnect(), 180, 5, TimeUnit.SECONDS);
            socketOpen.apply(socket);
            Credentials instanceCredentials = computer.describeNode().getCredentials();
            logger.println("Connecting");

            sshClient = new JschSshClient(new BackoffLimitedRetryHandler(), socket, 60000,
                    instanceCredentials.identity, instanceCredentials.credential, null);

            sshClient.connect();
            logger.println( "Transferring" );
            sshClient.put( "/tmp/slave.jar",
                           Payloads.newByteArrayPayload( Hudson.getInstance().getJnlpJars( "slave.jar" ).readFully() ) );
//            sshClient.disconnect();

            logger.println("Copied jar");


            logger.println("Launching slave agent");

            ret = computer.executeScript("java -jar /tmp/slave.jar", logger);
            logger.println("java -jar /tmp/slave.jar returned: " + ret);
            if( ret != 0) {
                logger.println("Could not execute slave");
            }

            // @TODO jvmopts: sess.execCommand("java " + computer.getNode().jvmopts + " -jar /tmp/slave.jar");
            computer.executeScript( "java -jar /tmp/slave.jar", logger );
/*
            computer.setChannel(listener.getLogger(), <need OutputStream here>,  listener, new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    sshClient.disconnect();
                }
            });
*/
            sshClient.disconnect();
            successful = true;
        } catch (IOException e) {
            e.printStackTrace(listener.error(e.getLocalizedMessage()));
        } catch (InterruptedException e) {
            e.printStackTrace(listener.error(e.getMessage()));


        } finally {
            PrintStream logger = listener.getLogger();
            logger.println("In finally");

            if (sshClient != null && !successful) {
                sshClient.disconnect();
            }
            logger.println("End of finally");

        }

    }


/*
    private Connection connectToSsh(NodeMetadata inst) throws InterruptedException {
        while(true) {
            try {
                Connection conn = new Connection(inst.getPublicAddresses().toArray()[0].toString(),22);
                conn.connect();

                return conn; // successfully connected
            } catch (IOException e) {
                // keep retrying until SSH comes up
                Thread.sleep(5000);
            }
        }
    }
*/

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }



}
