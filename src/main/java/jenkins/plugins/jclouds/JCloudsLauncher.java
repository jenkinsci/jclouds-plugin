package jenkins.plugins.jclouds;

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import org.jclouds.compute.domain.ExecChannel;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.io.payloads.ByteArrayPayload;
import org.jclouds.ssh.SshClient;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

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
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {

        PrintStream logger = listener.getLogger();

        JCloudsSlave jCloudsSlave = (JCloudsSlave) computer.getNode();
        NodeMetadata nodeMetaData = jCloudsSlave.getNodeMetaData();
        SshClient ssh = JCloudsCloud.get().getCompute().getContext().utils().sshForNode().apply(nodeMetaData);


        ssh.connect();
        logger.println("Connected via SSH");
        logger.println("Copying slave.jar");
        ssh.put("/tmp/slave.jar", new ByteArrayPayload(Hudson.getInstance().getJnlpJars("slave.jar").readFully()));
        ExecResponse exec = ssh.exec("java -version");
        logger.println(exec);
        ExecResponse exec1 = ssh.exec("ls -al /tmp/slave.jar");
        logger.println(exec1);

//        ExecChannel execChannel = ssh.execChannel("java -jar /tmp/slave.jar");
//        computer.setChannel(execChannel.getOutput(), execChannel.getInput(), logger, new Channel.Listener() {
//
//            @Override
//            public void onClosed(Channel channel, IOException cause) {
//                cause.printStackTrace();
//            }
//        });


    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }

}
