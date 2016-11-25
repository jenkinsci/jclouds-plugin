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

    private static final long serialVersionUID = 42L;

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
        phm = new PhoneHomeMonitor(waitPhoneHome, waitPhoneHomeTimeout);
    }

    protected Object readResolve() {
        if (null == phm) {
            phm = new PhoneHomeMonitor(waitPhoneHome, waitPhoneHomeTimeout);
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
     * @throws IOException if an error occurs.
     * @throws Descriptor.FormException if the form does not validate.
     */
    public JCloudsSlave(final String cloudName, final String fsRoot, NodeMetadata metadata, final String labelString,

            final String description, final String numExecutors, final boolean stopOnTerminate, final Integer overrideRetentionTime,
            String jvmOptions, final boolean waitPhoneHome, final int waitPhoneHomeTimeout, final String credentialsId, final Mode mode) throws IOException, Descriptor.FormException {
        this(cloudName, uniqueName(metadata, cloudName), description, fsRoot, numExecutors, mode, labelString,
                new JCloudsLauncher(), new JCloudsRetentionStrategy(), Collections.<NodeProperty<?>>emptyList(),
                stopOnTerminate, overrideRetentionTime, metadata.getCredentials().getUser(),
                metadata.getCredentials().getOptionalPassword().orNull(), metadata.getCredentials().getOptionalPrivateKey().orNull(),
                metadata.getCredentials().shouldAuthenticateSudo(), jvmOptions, waitPhoneHome, waitPhoneHomeTimeout, credentialsId);
        this.nodeMetaData = metadata;
        this.nodeId = nodeMetaData.getId();
    }

    // JENKINS-19935 Instances on EC2 don't get random suffix
    final static String uniqueName(final NodeMetadata md, final String cloudName) {
        JCloudsCloud c = JCloudsCloud.getByName(cloudName);
        if (c.providerName.equals("aws-ec2")) {
            return md.getName() + "-" + md.getProviderId();
        }
        return md.getName();
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
     * <p>
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
            phm.interrupt();
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
        final File nodesDir = new File(Jenkins.getInstance().getRootDir(), "nodes");
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
        if (!waitPhoneHome) {
            phm.ring();
        }
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
     * Destroy the node.
     * If stopOnTerminate is {@code true}, calls {@link ComputeService#suspendNode},
     * otherwise {@link ComputeService#destroyNode}.
     */
    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        final ComputeService compute = JCloudsCloud.getByName(cloudName).getCompute();
        if (compute.getNodeMetadata(nodeId) != null && compute.getNodeMetadata(nodeId).getStatus().equals(NodeMetadata.Status.RUNNING)) {
            if (stopOnTerminate) {
                LOGGER.info("Suspending slave : " + getNodeName());
                compute.suspendNode(nodeId);
            } else {
                LOGGER.info("Terminating slave : " + getNodeName());
                compute.destroyNode(nodeId);
            }
        } else {
            LOGGER.info("Slave " + getNodeName() + " is already not running.");
        }
    }

    public void waitForPhoneHome(PrintStream logger) throws InterruptedException {
        phm.waitForPhoneHome(getNodeName(), logger);
    }
}
