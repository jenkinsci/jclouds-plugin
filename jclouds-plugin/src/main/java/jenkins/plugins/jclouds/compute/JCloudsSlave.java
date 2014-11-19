package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.domain.LoginCredentials;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Jenkins Slave node - managed by JClouds.
 *
 * @author Vijay Kiran
 */
public class JCloudsSlave extends AbstractCloudSlave {
    private static final Logger LOGGER = Logger.getLogger(JCloudsSlave.class.getName());
    private transient NodeMetadata nodeMetaData;
    public final boolean stopOnTerminate;
    private final String cloudName;
    private String nodeId;
    private boolean pendingDelete;
    private final int overrideRetentionTime;
    private final String user;
    private final String password;
    private final String privateKey;
    private final boolean authSudo;
    private final String jvmOptions;

    @DataBoundConstructor
    @SuppressWarnings("rawtypes")
    public JCloudsSlave(String cloudName, String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode, String labelString,
                        ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties, boolean stopOnTerminate,
                        int overrideRetentionTime, String user, String password, String privateKey, boolean authSudo, String jvmOptions) throws Descriptor.FormException,
            IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
        this.stopOnTerminate = stopOnTerminate;
        this.cloudName = cloudName;
        this.overrideRetentionTime = overrideRetentionTime;
        this.user = user;
        this.password = password;
        this.privateKey = privateKey;
        this.authSudo = authSudo;
        this.jvmOptions = jvmOptions;
    }

    /**
     * Constructs a new slave from JCloud's NodeMetadata
     *
     * @param cloudName             - the name of the cloud that's provisioning this slave.
     * @param fsRoot                - where on the slave the Jenkins slave root is.
     * @param metadata              - JCloudsNodeMetadata
     * @param labelString           - Label(s) for this slave.
     * @param description           - Description of this slave.
     * @param numExecutors          - Number of executors for this slave.
     * @param stopOnTerminate       - if true, suspend the slave rather than terminating it.
     * @param overrideRetentionTime - Retention time to use specifically for this slave, overriding the cloud default.
     * @throws IOException
     * @throws Descriptor.FormException
     */
    public JCloudsSlave(final String cloudName, final String fsRoot, NodeMetadata metadata, final String labelString, final String description,
                        final String numExecutors, final boolean stopOnTerminate, final int overrideRetentionTime, String jvmOptions) throws IOException,
            Descriptor.FormException {
        this(cloudName, metadata.getName(), description, fsRoot, numExecutors, Mode.EXCLUSIVE, labelString, new JCloudsLauncher(),
                new JCloudsRetentionStrategy(), Collections.<NodeProperty<?>>emptyList(), stopOnTerminate, overrideRetentionTime, metadata.getCredentials()
                        .getUser(), metadata.getCredentials().getPassword(), metadata.getCredentials().getPrivateKey(), metadata.getCredentials()
                        .shouldAuthenticateSudo(), jvmOptions);
        this.nodeMetaData = metadata;
        this.nodeId = nodeMetaData.getId();
    }

    /**
     * Get Jclouds NodeMetadata associated with this Slave.
     *
     * @return {@link NodeMetadata}
     */
    public NodeMetadata getNodeMetaData() {
        if (this.nodeMetaData == null) {
            final ComputeService compute = JCloudsCloud.getByName(cloudName).getCompute();
            this.nodeMetaData = compute.getNodeMetadata(nodeId);
        }
        return nodeMetaData;
    }

    /**
     * Get Jclouds Custom JVM Options associated with this Slave.
     *
     * @return jvmOptions
     */
    public String getJvmOptions() {
        return jvmOptions;
    }

    /**
     * Get Jclouds LoginCredentials associated with this Slave.
     * <p/>
     * If Jclouds doesn't provide credentials, use stored ones.
     *
     * @return {@link LoginCredentials}
     */
    public LoginCredentials getCredentials() {
        LoginCredentials credentials = getNodeMetaData().getCredentials();
        if (credentials == null)
            credentials = LoginCredentials.builder().user(user).password(password).privateKey(privateKey).authenticateSudo(authSudo).build();
        return credentials;
    }

    /**
     * Get the retention time for this slave, defaulting to the parent cloud's if not set.
     *
     * @return overrideTime
     */
    public int getRetentionTime() {
        if (overrideRetentionTime > 0) {
            return overrideRetentionTime;
        } else {
            return JCloudsCloud.getByName(cloudName).getRetentionTime();
        }
    }

    /**
     * Get the JClouds profile identifier for the Cloud associated with this slave.
     *
     * @return cloudName
     */
    public String getCloudName() {
        return cloudName;
    }

    public boolean isPendingDelete() {
        return pendingDelete;
    }

    public void setPendingDelete(boolean pendingDelete) {
        this.pendingDelete = pendingDelete;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractCloudComputer<JCloudsSlave> createComputer() {
        LOGGER.info("Creating a new JClouds Slave");
        return new JCloudsComputer(this);
    }

    @Extension
    public static final class JCloudsSlaveDescriptor extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "JClouds Slave";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isInstantiable() {
            return false;
        }
    }

    /**
     * Destroy the node calls {@link ComputeService#destroyNode}
     */
    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        final ComputeService compute = JCloudsCloud.getByName(cloudName).getCompute();
        if (compute.getNodeMetadata(nodeId) != null && compute.getNodeMetadata(nodeId).getStatus().equals(NodeMetadata.Status.RUNNING)) {
            if (stopOnTerminate) {
                LOGGER.info("Suspending the Slave : " + getNodeName());
                compute.suspendNode(nodeId);
            } else {
                LOGGER.info("Terminating the Slave : " + getNodeName());
                compute.destroyNode(nodeId);
            }
        } else {
            LOGGER.info("Slave " + getNodeName() + " is already not running.");
        }
    }
}
