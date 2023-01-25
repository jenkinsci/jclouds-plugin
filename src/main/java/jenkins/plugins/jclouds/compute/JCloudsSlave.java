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
import hudson.slaves.SlaveComputer;

import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.domain.LoginCredentials;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Jenkins Slave node - managed by JClouds.
 *
 * @author Vijay Kiran
 */
public class JCloudsSlave extends AbstractCloudSlave implements TrackedItem{

    private static final long serialVersionUID = 42L;

    private static final Logger LOGGER = Logger.getLogger(JCloudsSlave.class.getName());

    private ProvisioningActivity.Id provisioningId;
    private transient NodeMetadata nodeMetaData;
    public final boolean stopOnTerminate;
    private final String cloudName;
    private String nodeId;
    private boolean pendingDelete;
    private boolean waitPhoneHome;
    private Integer overrideRetentionTime;
    private final int waitPhoneHomeTimeout;
    private final String user;
    private final String password;
    private final String privateKey;
    private final boolean authSudo;
    private final String jvmOptions;
    private final String credentialsId;
    private final String preferredAddress;
    private final boolean useJnlp;
    private final boolean jnlpProvisioning;
    private String jnlpProvisioningNonce;

    private transient PhoneHomeMonitor phm;

    @DataBoundConstructor
    @SuppressWarnings("rawtypes")
    public JCloudsSlave(String cloudName, String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode, String labelString,
            ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties, boolean stopOnTerminate,
            Integer overrideRetentionTime, String user, String password, String privateKey, boolean authSudo, String jvmOptions, final boolean waitPhoneHome,
            final int waitPhoneHomeTimeout, final String credentialsId, final String preferredAddress, final boolean useJnlp, final boolean jnlpProvisioning,
            final String jnlpProvisioningNonce)
        throws Descriptor.FormException, IOException
    {
        super(name, remoteFS, launcher);
        setLabelString(labelString);
        setNodeDescription(nodeDescription);
        setNumExecutors(Integer.parseInt(numExecutors));
        setMode(mode);
        setRetentionStrategy(retentionStrategy);
        setNodeProperties(nodeProperties);
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
        this.preferredAddress = preferredAddress;
        this.useJnlp = useJnlp;
        this.jnlpProvisioning = jnlpProvisioning;
        this.jnlpProvisioningNonce = jnlpProvisioningNonce;
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
     * @param preferredAddress      - The preferred Address expression to connect to
     * @param useJnlp               - if {@code true}, the final ssh connection attempt will be skipped.
     * @param jnlpProvisioning      - if {@code true}, enables JNLP provisioning.
     * @param jnlpProvisioningNonce - nonce, used to authenticate provisioning requests via JnlpProvisionWebHook.
     * @throws IOException if an error occurs.
     * @throws Descriptor.FormException if the form does not validate.
     */
    public JCloudsSlave(final String cloudName, final String fsRoot, NodeMetadata metadata, final String labelString,
            final String description, final String numExecutors, final boolean stopOnTerminate, final Integer overrideRetentionTime,
            String jvmOptions, final boolean waitPhoneHome, final int waitPhoneHomeTimeout, final String credentialsId,
            final Mode mode, final String preferredAddress, boolean useJnlp, final boolean jnlpProvisioning, final String jnlpProvisioningNonce)
            throws IOException, Descriptor.FormException
        {
            this(cloudName, uniqueName(metadata, cloudName), description, fsRoot, numExecutors, mode, labelString,
                    useJnlp ? new JCloudsJnlpLauncher(true) : new JCloudsLauncher(), new JCloudsRetentionStrategy(),
                    Collections.<NodeProperty<?>>emptyList(), stopOnTerminate, overrideRetentionTime, metadata.getCredentials().getUser(),
                metadata.getCredentials().getOptionalPassword().orNull(), metadata.getCredentials().getOptionalPrivateKey().orNull(),
                metadata.getCredentials().shouldAuthenticateSudo(), jvmOptions, waitPhoneHome, waitPhoneHomeTimeout, credentialsId,
                preferredAddress, useJnlp, jnlpProvisioning, jnlpProvisioningNonce);
        this.nodeMetaData = metadata;
        this.nodeId = nodeMetaData.getId();
    }

    public JCloudsSlave(ProvisioningActivity.Id provisioningId, final String cloudName, final String fsRoot, NodeMetadata metadata, final String labelString,
            final String description, final String numExecutors, final boolean stopOnTerminate, final Integer overrideRetentionTime,
            String jvmOptions, final boolean waitPhoneHome, final int waitPhoneHomeTimeout, final String credentialsId,
            final Mode mode, final String preferredAddress, boolean useJnlp, final boolean jnlpProvisioning, final String jnlpProvisioningNonce)
            throws IOException, Descriptor.FormException
    {
        this(cloudName, fsRoot, metadata, labelString, description, numExecutors,  stopOnTerminate, overrideRetentionTime,
                jvmOptions,  waitPhoneHome, waitPhoneHomeTimeout, credentialsId, mode, preferredAddress,  useJnlp, jnlpProvisioning, jnlpProvisioningNonce);
        this.provisioningId = provisioningId;
    }

    // JENKINS-19935 Instances on EC2 don't get random suffix
    final static String uniqueName(final NodeMetadata md, final String cloudName) {
        JCloudsCloud c = JCloudsCloud.getByName(cloudName);
        if (c.providerName.equals("aws-ec2")) {
            return md.getName() + "-" + md.getProviderId();
        }
        return md.getName();
    }

    @CheckForNull
    private String calculateJnlpProvisioningHash() {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final String src = jnlpProvisioningNonce + getNodeName();
            return Base64.encodeBase64String(md.digest(src.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException x) {
            LOGGER.severe("Should not happen: SHA256 digest algorithm not available");
            return null;
        }
    }

    private Map <String, String> getJnlpProperties() {
        Map<String, String> ret = new HashMap();
        String rootUrl = Jenkins.get().getRootUrl();
        if (null == rootUrl) {
            rootUrl = "";
        }
        SlaveComputer computer = getComputer();
        if (null != computer) {
            ret.put("X-url", rootUrl + computer.getUrl() + "slave-agent.jnlp");
            ret.put("X-jar", rootUrl + "jnlpJars/agent.jar");
            ret.put("X-sec", computer.getJnlpMac());
        }
        LOGGER.severe("Should not happen: No associated SlaveComputer");
        return ret;
    }

    private String ToJsonString(final Map <String, String> map) {
        final JSONObject ret = new JSONObject();
        ret.putAll(map);
        return ret.toString();
    }

    /**
     * Handles JNLP provisioning request from slave.
     *
     * @param hash The authentication hash, provided in the client's request
     * @return The parameters for establishing a Jnlp connection as JSON response.
     *         An empty string, if the provided hash is incorrect.
     */
    public String handleJnlpProvisioning(@NonNull String hash) {
        if (jnlpProvisioning) {
            LOGGER.info("Handling JNLP provisioning request");
            String expectedHash = calculateJnlpProvisioningHash();
            if (null != expectedHash && expectedHash.equals(hash)) {
                LOGGER.info("Responding to JNLP provisioning request");
                return ToJsonString(getJnlpProperties());
            }
        }
        return "";
    }

    /**
     * Publishes JNLP metadata to the virtual machine.
     */
    public void publishJnlpMetaData() {
        if (jnlpProvisioning) {
            MetaDataPublisher mdp = new MetaDataPublisher(JCloudsCloud.getByName(cloudName));
            mdp.publish(nodeId, "Publishing JNLP properties for ID " + nodeId, getJnlpProperties());
        }
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

    public String getPreferredAddress() {
        return preferredAddress;
    }

    public boolean getJnlpProvisioning() {
        return jnlpProvisioning;
    }


    public boolean getUseJnlp() {
        return useJnlp;
    }

    public String getJnlpProvisioningNonce() {
        return jnlpProvisioningNonce;
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

    public void setOverrideRetentionTime(Integer value) {
        overrideRetentionTime = value;
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
     * Get the errorRetention time for this agents parent cloud.
     * Sometimes parent cloud cannot be determined (returns Null as I see), in which case this method will
     * return default value set in CloudInstanceDefaults.
     *
     * @return errorRetentionTime
     * @see CloudInstanceDefaults#DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES
     */
    int getErrorRetentionTime() {
        final JCloudsCloud cloud = JCloudsCloud.getByName(cloudName);
        return cloud == null ? CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES : cloud.getErrorRetentionTime();
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
        final File nodesDir = new File(Jenkins.get().getRootDir(), "nodes");
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

    public void setJnlpProvisioningNonce(String value) {
        jnlpProvisioningNonce = value;
        // TODO: Replace after https://github.com/jenkinsci/jenkins/pull/1860 has been merged.
        updateXml();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractCloudComputer<JCloudsSlave> createComputer() {
        LOGGER.info("Creating a new JClouds agent");
        return new JCloudsComputer(this);
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        return provisioningId;
    }

    @Extension
    public static final class JCloudsSlaveDescriptor extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "JClouds agent";
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
                LOGGER.info("Suspending node: " + getNodeName());
                compute.suspendNode(nodeId);
            } else {
                LOGGER.info("Terminating node: " + getNodeName());
                compute.destroyNode(nodeId);
            }
        } else {
            LOGGER.info("Node " + getNodeName() + " is already terminated.");
        }
        ProvisioningActivity activity = CloudStatistics.get().getActivityFor(this);
        if (activity != null) {
            activity.enterIfNotAlready(ProvisioningActivity.Phase.COMPLETED);
        }
    }

    public void waitForPhoneHome(PrintStream logger) throws InterruptedException {
        try {
            phm.waitForPhoneHome(getNodeName(), logger);
        } catch (InterruptedException e) {
            setWaitPhoneHome(false);
            throw e;
        }
    }
}
