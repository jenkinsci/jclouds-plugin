package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;

import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
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
    private boolean waitPhoneHome;
    private final Integer overrideRetentionTime;
    private final int waitPhoneHomeTimeout;
    private final String user;
    private final String password;
    private final String privateKey;
    private final boolean authSudo;
    private final String jvmOptions;
    private final String credentialsId;
    private final Mode mode;

    private transient PhoneHomeMonitor phm;

    private static final class PhoneHomeMonitor {
        private final Lock phoneHomeLock = new ReentrantLock();
        private final Condition doneWaitPhoneHome = phoneHomeLock.newCondition();
    
        public void signalCondition() {
            phoneHomeLock.lock();
            try {
                doneWaitPhoneHome.signal();
            } finally {
                phoneHomeLock.unlock();
            }
        }

        public void waitCondition(final long millis) throws InterruptedException {
            phoneHomeLock.lock();
            try {
                doneWaitPhoneHome.await(millis, TimeUnit.MILLISECONDS);
            } finally {
                phoneHomeLock.unlock();
            }
        }
    }

    @DataBoundConstructor
    @SuppressWarnings("rawtypes")
    public JCloudsSlave(String cloudName, String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode, String labelString,
            ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties, boolean stopOnTerminate,
            Integer overrideRetentionTime, String user, String password, String privateKey, boolean authSudo, String jvmOptions, final boolean waitPhoneHome,
            final int waitPhoneHomeTimeout, final String credentialsId) throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
        this.stopOnTerminate = stopOnTerminate;
        this.cloudName = cloudName;
        this.overrideRetentionTime = overrideRetentionTime;
        this.user = user;
        this.password = password;
        this.privateKey = privateKey;
        this.authSudo = authSudo;
        this.jvmOptions = jvmOptions;
        this.waitPhoneHome = waitPhoneHome;
        this.waitPhoneHomeTimeout = waitPhoneHomeTimeout;
        this.credentialsId = credentialsId;
        this.mode = mode;
        phm = new PhoneHomeMonitor();
    }

    protected Object readResolve() {
        if (null == phm) {
            phm = new PhoneHomeMonitor();
        }
        return this;
    }

    /**
     * Constructs a new slave from JCloud's NodeMetadata
     *
     * @param cloudName             - the name of the cloud that's provisioning this slave.
     * @param fsRoot                - Location of Jenkins root (homedir) on the slave.
     * @param metadata              - JCloudsNodeMetadata
     * @param labelString           - Label(s) for this slave.
     * @param description           - Description of this slave.
     * @param numExecutors          - Number of executors for this slave.
     * @param stopOnTerminate       - if {@code true}, suspend the slave rather than terminating it.
     * @param overrideRetentionTime - Retention time to use specifically for this slave, overriding the cloud default.
     * @param jvmOptions            - Custom options for lauching the JVM on the slave.
     * @param waitPhoneHome         - if {@code true}, delay initial SSH connect until slave has "phoned home" back to jenkins.
     * @param waitPhoneHomeTimeout  - Timeout in minutes util giving up waiting for the "phone home" POST.
     * @param credentialsId         - Id of the credentials in Jenkin's global credentials database.
     * @param mode                  - Jenkins usage mode for this node
     * @throws IOException
     * @throws Descriptor.FormException
     */
    public JCloudsSlave(final String cloudName, final String fsRoot, NodeMetadata metadata, final String labelString,

            final String description, final String numExecutors, final boolean stopOnTerminate, final Integer overrideRetentionTime,
            String jvmOptions, final boolean waitPhoneHome, final int waitPhoneHomeTimeout, final String credentialsId, final Mode mode) throws IOException, Descriptor.FormException {
        this(cloudName, metadata.getName(), description, fsRoot, numExecutors, mode, labelString,
                new JCloudsLauncher(), new JCloudsRetentionStrategy(), Collections.<NodeProperty<?>>emptyList(),
                stopOnTerminate, overrideRetentionTime, metadata.getCredentials().getUser(),
                metadata.getCredentials().getPassword(), metadata.getCredentials().getPrivateKey(),
                metadata.getCredentials().shouldAuthenticateSudo(), jvmOptions, waitPhoneHome, waitPhoneHomeTimeout, credentialsId);
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
        if (credentials == null) {
            LOGGER.info("Using credentials from CloudSlave instance");
            credentials = LoginCredentials.builder().user(user).password(password).privateKey(privateKey).authenticateSudo(authSudo).build();
        } else {
            LOGGER.info("Using credentials from JClouds");
        }
        return credentials;
    }

    /**
     * Get the retention time for this slave, defaulting to the parent cloud's if not set.
     * Sometime parent cloud cannot be determined (returns Null as I see), in which case this method will
     * return default value set in CloudInstanceDefaults.
     *
     * @return overrideTime
     * @see CloudInstanceDefaults#DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES
     */
    public int getRetentionTime() {
        if (null != overrideRetentionTime) {
            return overrideRetentionTime.intValue();
        }
        final JCloudsCloud cloud = JCloudsCloud.getByName(cloudName);
        return cloud == null ? CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES : cloud.getRetentionTime();
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
        if (pendingDelete) {
            phm.signalCondition();
        }
    }

    public boolean isWaitPhoneHome() {
        return waitPhoneHome;
    }

    /*
     * This is extremely ugly!
     * There should be a Jenkins#updateNode(Node) instead of just addNode() and removeNode().
     * We MUST persist the value of the waitPhoneHome flag, because otherwise if jenkins is
     * restarted after a node has successfully phoned home and that node is still running,
     * jenkins would then repeat the whole thing, obviously timing out when the node does not
     * phone home again.
     * Another solution would be for jenkins to simply persist all nodes once before restarting.
     *
     * TODO: Remove, after https://github.com/jenkinsci/jenkins/pull/1860 has been merged.
     */
    private void updateXml() {
        final File nodesDir = new File(Jenkins.getActiveInstance().getRootDir(), "nodes");
        final File cfg = new File(new File(nodesDir, getNodeName()), "config.xml");
        if (cfg.exists()) {
            XmlFile xmlFile = new XmlFile(Jenkins.XSTREAM, cfg);
            try {
                xmlFile.write(this);
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            }
        }
    }

    public void setWaitPhoneHome(boolean value) {
        waitPhoneHome = value;
        // TODO: Replace after https://github.com/jenkinsci/jenkins/pull/1860 has been merged.
        updateXml();
        phm.signalCondition();
    }

    public long getWaitPhoneHomeTimeoutMs() {
        if (0 < waitPhoneHomeTimeout) {
            return 60000L * waitPhoneHomeTimeout;
        }
        return 0;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public Mode getMode() {
        return mode;
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

    public void waitForPhoneHome(PrintStream logger) throws InterruptedException {
        long timeout = System.currentTimeMillis() + getWaitPhoneHomeTimeoutMs();
        while (true) {
            long tdif = timeout - System.currentTimeMillis();
            if (tdif < 0) {
                setWaitPhoneHome(false);
                throw new InterruptedException("wait for phone home timed out");
            }
            if (isPendingDelete()) {
                setWaitPhoneHome(false);
                throw new InterruptedException("wait for phone home interrupted by delete request");
            }
            if (isWaitPhoneHome()) {
                final String msg = "Waiting for " + getNodeName() + " to phone home. " + tdif / 1000 + " seconds until timeout.";
                LOGGER.info(msg);
                if (null != logger) {
                    logger.println(msg);
                }
                if (tdif > 30000L) {
                    // Wait exactly, but still log a message every 30sec.
                    tdif = 30000L;
                }
                phm.waitCondition(tdif);
            } else {
                break;
            }
        }
    }
}
