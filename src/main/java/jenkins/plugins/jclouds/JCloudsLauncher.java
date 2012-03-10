package jenkins.plugins.jclouds;

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.StreamCopyThread;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.logging.Logger;

import org.jclouds.compute.domain.ExecChannel;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.io.payloads.ByteArrayPayload;
import org.jclouds.ssh.SshClient;
import org.jclouds.util.Strings2;

import com.google.common.io.Closeables;
import com.trilead.ssh2.StreamGobbler;

/**
 * @author Vijay Kiran
 */
public class JCloudsLauncher extends ComputerLauncher {
    private final int FAILED = -1;
    private final int SAMEUSER = 0;
    private final int RECONNECT = -2;

    private static final Logger LOGGER = Logger.getLogger(JCloudsLauncher.class.getName());

    public boolean isLaunchSupported() {
        return true;
    }

    @Override
   public void launch(SlaveComputer computer, final TaskListener listener) throws IOException, InterruptedException {

      final PrintStream logger = listener.getLogger();

      JCloudsSlave jCloudsSlave = (JCloudsSlave) computer.getNode();
      NodeMetadata nodeMetaData = jCloudsSlave.getNodeMetaData();
      final SshClient ssh = JCloudsCloud.get().getCompute().getContext().utils().sshForNode().apply(nodeMetaData);
      try {

         ssh.connect();
         logger.println("Connected via SSH");
         logger.println("Copying slave.jar");
         ssh.put("/tmp/slave.jar", new ByteArrayPayload(Hudson.getInstance().getJnlpJars("slave.jar").readFully()));
         ExecResponse exec = ssh.exec("java -version");
         logger.println(exec);
         ExecResponse exec1 = ssh.exec("ls -al /tmp/slave.jar");
         logger.println(exec1);

         final ExecChannel execChannel = ssh.execChannel("java -jar /tmp/slave.jar");
         final StreamGobbler out = new StreamGobbler(execChannel.getOutput());
         final StreamGobbler err = new StreamGobbler(execChannel.getError());

         // capture error information from stderr. this will terminate itself
         // when the process is killed.
         new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(), err, listener
                  .getLogger()).start();

         computer.setChannel(out, execChannel.getInput(), logger, new Channel.Listener() {

            @Override
            public void onClosed(Channel channel, IOException cause) {
               try {
                  listener.error(Strings2.toStringAndClose(execChannel.getError()));
               } catch (IOException e) {
                  e.printStackTrace(listener.error(hudson.model.Messages.Slave_Terminated(new Date().toString())));
               }
               try {
                  listener.error(Strings2.toStringAndClose(execChannel.getOutput()));
               } catch (IOException e) {
                  e.printStackTrace(listener.error(hudson.model.Messages.Slave_Terminated(new Date().toString())));
               }
               if (cause != null) {
                  cause.printStackTrace(listener.error(hudson.model.Messages.Slave_Terminated(new Date().toString())));
               }
               Closeables.closeQuietly(execChannel);
            }
         });
      } finally {
         ssh.disconnect();
      }

   }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }

}
