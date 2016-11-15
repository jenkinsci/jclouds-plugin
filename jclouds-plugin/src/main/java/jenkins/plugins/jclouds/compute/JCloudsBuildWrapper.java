package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.slaves.Cloud;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.compute.internal.NodePlan;
import jenkins.plugins.jclouds.compute.internal.ProvisionPlannedInstancesAndDestroyAllOnError;
import jenkins.plugins.jclouds.compute.internal.RunningNode;
import jenkins.plugins.jclouds.compute.internal.TerminateNodes;
import jenkins.plugins.jclouds.internal.BuildListenerLogger;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

import shaded.com.google.common.base.Function;
import shaded.com.google.common.base.Supplier;
import shaded.com.google.common.cache.CacheBuilder;
import shaded.com.google.common.cache.CacheLoader;
import shaded.com.google.common.cache.LoadingCache;
import shaded.com.google.common.collect.ImmutableList;
import shaded.com.google.common.collect.Iterables;
import shaded.com.google.common.collect.ImmutableList.Builder;
import shaded.com.google.common.util.concurrent.MoreExecutors;

public class JCloudsBuildWrapper extends BuildWrapper {
    private final List<InstancesToRun> instancesToRun;

    @DataBoundConstructor
    public JCloudsBuildWrapper(List<InstancesToRun> instancesToRun) {
        this.instancesToRun = instancesToRun;
    }

    public List<InstancesToRun> getInstancesToRun() {
        return instancesToRun;
    }

    private boolean isBeyondInstanceCap(final String cloudName, int numOfNewInstances) {
        final Jenkins.CloudList cl = Jenkins.getInstance().clouds;
        final Cloud c = cl.getByName(cloudName);
        if (null != c && c instanceof JCloudsCloud) {
            JCloudsCloud jc = (JCloudsCloud)c;
            return jc.getRunningNodesCount() + numOfNewInstances >= jc.instanceCap;
        }
        return false;
    }

    private String validateInstanceCaps() throws IOException {
        Map<String, Integer> startPerCloud = new HashMap<>();
        for (final InstancesToRun inst : instancesToRun) {
            Integer old = startPerCloud.put(inst.cloudName, Integer.valueOf(inst.count));
            if (null != old) {
                startPerCloud.put(inst.cloudName, old + Integer.valueOf(inst.count));
            }
        }
        for (final Map.Entry<String,Integer> entry : startPerCloud.entrySet()) {
            final String cname = entry.getKey();
            if (isBeyondInstanceCap(cname, entry.getValue().intValue())) {
                return cname;
            }
        }
        return null;
    }

    //
    // convert Jenkins staticy stuff into pojos; performing as little critical stuff here as
    // possible, as this method is very hard to test due to static usage, etc.
    //
    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, final BuildListener listener) throws IOException {
        final String failedCloud = validateInstanceCaps();
        if (null != failedCloud) {
            listener.fatalError("Unable to launch supplemental JClouds instances:");
            throw new IOException(String.format("Instance cap for cloud %s reached.", failedCloud));
        }

        final LoadingCache<String, ComputeService> computeCache = CacheBuilder.newBuilder().build(new CacheLoader<String, ComputeService>() {

            @Override
            public ComputeService load(String arg0) throws Exception {
                return JCloudsCloud.getByName(arg0).getCompute();
            }
        });

        // eagerly lookup node supplier so that errors occur before we attempt to provision things
        Iterable<NodePlan> nodePlans = Iterables.transform(instancesToRun, new Function<InstancesToRun, NodePlan>() {

            public NodePlan apply(InstancesToRun instance) {
                String cloudName = instance.cloudName;
                String templateName = Util.replaceMacro(instance.getActualTemplateName(), build.getBuildVariableResolver());
                Supplier<NodeMetadata> nodeSupplier = JCloudsCloud.getByName(cloudName).getTemplate(templateName);
                // take the hit here, as opposed to later
                computeCache.getUnchecked(cloudName);
                return new NodePlan(cloudName, templateName, instance.count, instance.suspendOrTerminate, nodeSupplier);
            }

        });

        // converting to a logger as it is an interface and easier to test
        final Logger logger = new BuildListenerLogger(listener);

        final TerminateNodes terminateNodes = new TerminateNodes(logger, computeCache);

        ProvisionPlannedInstancesAndDestroyAllOnError provisioner = new ProvisionPlannedInstancesAndDestroyAllOnError(
                MoreExecutors.listeningDecorator(Computer.threadPoolForRemoting), logger, terminateNodes);

        final Iterable<RunningNode> runningNodes = provisioner.apply(nodePlans);

        return new Environment() {
            private JCloudsCloud waitCloud = null;

            @Override
            public void buildEnvVars(Map<String, String> env) {
                List<String> ips = getInstanceIPs(runningNodes, listener.getLogger());
                env.put("JCLOUDS_IPS", Util.join(ips, ","));
                waitCloud = waitPhoneHome(runningNodes, listener.getLogger());
                if (null != waitCloud) {
                    waitCloud.phoneHomeWaitAll();
                }
            }

            @Override
            public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
                if (null != waitCloud) {
                    waitCloud.phoneHomeAbort();
                }
                terminateNodes.apply(runningNodes);
                return true;
            }

        };

    }

    private JCloudsCloud waitPhoneHome(final Iterable<RunningNode> runningNodes, PrintStream logger) {
        Integer wto = null;
        JCloudsCloud ret = null;
        Map<Integer, List<String>> waitMap = new HashMap<>();
        for (RunningNode rn : runningNodes) {
            JCloudsCloud c = JCloudsCloud.getByName(rn.getCloudName());
            if (null != c) {
                JCloudsSlaveTemplate t = c.getTemplate(rn.getTemplateName());
                if (null != t && t.waitPhoneHome && t.waitPhoneHomeTimeout > 0) {
                    if (null == wto || wto.intValue() != t.waitPhoneHomeTimeout) {
                        wto = Integer.valueOf(t.waitPhoneHomeTimeout);
                    }
                    if (null == ret) {
                       ret = c;
                    }
                    List<String> tmp = new ArrayList<>();
                    tmp.add(rn.getNode().getName());
                    List<String> hosts = waitMap.put(wto, tmp);
                    if (null != hosts) {
                        waitMap.get(wto).addAll(hosts);
                    }
                }
            }
        }
        for (Map.Entry<Integer, List<String>> entry : waitMap.entrySet()) {
            final PhoneHomeMonitor phm = new PhoneHomeMonitor(true, entry.getKey().intValue());
            phm.waitForPhoneHome(entry.getValue(), logger);
            ret.registerPhoneHomeMonitor(phm);
        }
        return ret;
    }

    public List<String> getInstanceIPs(Iterable<RunningNode> runningNodes, PrintStream logger) {
        Builder<String> ips = ImmutableList.<String>builder();

        for (RunningNode runningNode : runningNodes) {
            String[] possibleIPs = JCloudsLauncher.getConnectionAddresses(runningNode.getNode(), logger);
            if (possibleIPs[0] != null) {
                ips.add(possibleIPs[0]);
            }
        }

        return ips.build();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return "Create supplemental instances";
        }

        @Override
        public boolean isApplicable(AbstractProject item) {
            // Show only, if at least one cloud with a SlaveTemplate exists
            for (String name : JCloudsCloud.getCloudNames()) {
                JCloudsCloud c = JCloudsCloud.getByName(name);
                if (c != null && c.getTemplates().size() > 0) {
                    return true;
                }
            }
            return false;
        }

    }
}
