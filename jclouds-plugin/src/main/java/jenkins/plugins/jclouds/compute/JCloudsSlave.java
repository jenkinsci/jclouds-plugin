package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.util.ListBoxModel.Option;

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
	private final String guestOS;
	private final int guestOsStartupTimeout;
	private final boolean authSudo;
	private final String jvmOptions;

	@DataBoundConstructor
	@SuppressWarnings("rawtypes")
	public JCloudsSlave(String cloudName, String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode, String labelString,
			ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties, boolean stopOnTerminate,
			int overrideRetentionTime, String user, String password, String privateKey, boolean authSudo, String jvmOptions, String guestOS,
			int guestOsStartupTimeout) throws Descriptor.FormException,
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
		this.guestOS = guestOS;
		this.guestOsStartupTimeout = guestOsStartupTimeout;
	}

	/**
	 * Constructs a new slave from JCloud's NodeMetadata
	 * 
	 * @param cloudName
	 *            - the name of the cloud that's provisioning this slave.
	 * @param fsRoot
	 *            - where on the slave the Jenkins slave root is.
	 * @param metadata
	 *            - JCloudsNodeMetadata
	 * @param labelString
	 *            - Label(s) for this slave.
	 * @param description
	 *            - Description of this slave.
	 * @param numExecutors
	 *            - Number of executors for this slave.
	 * @param stopOnTerminate
	 *            - if true, suspend the slave rather than terminating it.
	 * @param overrideRetentionTime
	 *            - Retention time to use specifically for this slave, overriding the cloud default.
	 * @param guestOS
	 *            - Type of guest operating system.
	 * @throws IOException
	 * @throws Descriptor.FormException
	 */
	public JCloudsSlave(final String cloudName, final String fsRoot, NodeMetadata metadata, final String labelString, final String description,
			final String numExecutors, final boolean stopOnTerminate, final int overrideRetentionTime, String jvmOptions, final String guestOS,
			final int guestOsStartupTimeout) throws IOException,
			Descriptor.FormException {
		this(cloudName, metadata.getName(), description, fsRoot, numExecutors, Mode.EXCLUSIVE, labelString, new JCloudsLauncher(),
				new JCloudsRetentionStrategy(), Collections.<NodeProperty<?>> emptyList(), stopOnTerminate, overrideRetentionTime, metadata.getCredentials()
						.getUser(), metadata.getCredentials().getPassword(), metadata.getCredentials().getPrivateKey(), metadata.getCredentials()
						.shouldAuthenticateSudo(), jvmOptions, guestOS, guestOsStartupTimeout);
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
	 * 
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
		if (overrideRetentionTime > 0 || overrideRetentionTime == -1) {
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

	public String getGuestOS() {
		return guestOS;
	}
	
	public int getGuestOsStartupTimeout() {
		return guestOsStartupTimeout;
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
	 * 
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

	/**
	 * Enumerate for supported JClouds guest operating systems.
	 *
	 * @author iakovenko
	 */
	public enum GuestOS {
		JNLP_WINDOWS("Pre-configured Windows that starts slave over JNLP", "win", false),
		UNIX("Unix", "unix", false);

		public static final String JENKINS_SCRIPTS_LOCATION = "/cygdrive/c/start";
		public static final String JNLP_URL_TEMPLATE = "%scomputer/%s/slave-agent.jnlp";
		public static final String CREATE_LAUNCH_SCRIPT_TEMPLATE =
				"echo 'cd C:\\start & java %s -jar slave.jar -jnlpUrl " +
				"\"%s\"' >> " + JENKINS_SCRIPTS_LOCATION + "/agent-launcher.bat";

		private final String longName;
		private final String shortName;
		private final boolean selected;

		private GuestOS(String longName, String shortName, boolean selected) {
			this.longName = longName;
			this.shortName = shortName;
			this.selected = selected;
		}

		public Option toOption() {
			return new Option(longName, shortName, selected);
		}

		/**
		 * @return the longName
		 */
		public String getLongName() {
			return longName;
		}

		/**
		 * @return the shortName
		 */
		public String getShortName() {
			return shortName;
		}

		/**
		 * @return the selected
		 */
		public boolean isSelected() {
			return selected;
		}

		@Override
		public String toString() {
			return shortName;
		}
	}


}
