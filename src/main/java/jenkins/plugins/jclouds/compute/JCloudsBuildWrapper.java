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

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.Util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.Symbol;

import jenkins.plugins.jclouds.compute.internal.JCloudsNodeMetadata;
import jenkins.plugins.jclouds.compute.internal.NodePlan;
import jenkins.plugins.jclouds.compute.internal.ProvisionPlannedInstancesAndDestroyAllOnError;
import jenkins.plugins.jclouds.compute.internal.RunningNode;
import jenkins.plugins.jclouds.compute.internal.TerminateNodes;
import jenkins.plugins.jclouds.internal.TaskListenerLogger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.MoreExecutors;

public class JCloudsBuildWrapper extends SimpleBuildWrapper implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_ENVVARNAME = "JCLOUDS_IPS";

    private final List<InstancesToRun> instancesToRun;
    private String envVarName = null;
    private String publishMeta = null;

    @DataBoundConstructor
    public JCloudsBuildWrapper(List<InstancesToRun> instancesToRun) {
        this.instancesToRun = instancesToRun;
    }

    @DataBoundSetter
    public void setEnvVarName(String value) {
        envVarName = Util.fixNull(Util.fixEmptyAndTrim(value), DEFAULT_ENVVARNAME);
    }

    public String getEnvVarName() {
        return envVarName;
    }

    @DataBoundSetter
    public void setPublishMeta(String value) {
        publishMeta = Util.fixEmptyAndTrim(value);
    }

    public String getPublishMeta() {
        return publishMeta;
    }

    private String getEnvVarNameWithDefault() {
        return Util.fixNull(envVarName, DEFAULT_ENVVARNAME);
    }

    public List<InstancesToRun> getInstancesToRun() {
        return instancesToRun;
    }

    @Override
    public boolean requiresWorkspace() {
        return false;
    }

    private static class JCloudsBuildWrapperDisposer extends Disposer {

        private static final long serialVersionUID = 1L;

        private static final Logger LOGGER = Logger.getLogger(JCloudsBuildWrapperDisposer.class.getName());

        private final Iterable<RunningNode> runningNodes;
        private final TerminateNodes terminateNodes;
        private final Set<String> waitingClouds;

        JCloudsBuildWrapperDisposer(Iterable<RunningNode> runningNodes, TerminateNodes terminateNodes, Set<String> waitingClouds) {
            this.runningNodes = runningNodes;
            this.waitingClouds = waitingClouds;
            this.terminateNodes = terminateNodes;
        }

        @Override
        public void tearDown(Run<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
            LOGGER.info("Terminating supplemental nodes");
            listener.getLogger().println("Terminating supplemental nodes");
            for (String cloud : waitingClouds) {
                for (RunningNode rn : runningNodes) {
                    JCloudsCloud.getByName(cloud).phoneHomeNotify(rn.getHostName());
                }
            }
            terminateNodes.apply(runningNodes);
            JCloudsCloud.unregisterSupplementalCleanup(build);
        }
    }

    //
    @Override
    public void setUp(Context context, Run<?,?> build, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {

        // Validate user-supplied input
        for (final InstancesToRun inst : instancesToRun) {
            String cn = inst.cloudName;
            Cloud c = Jenkins.get().clouds.getByName(cn);
            if (null == c) {
                throw new AbortException(String.format("A cloud named %s does not exist.", cn));
            }
            if (!JCloudsCloud.class.isInstance(c)) {
                throw new AbortException(String.format("The cloud named %s is not controlled by jclouds.", cn));
            }
            String tpln = initialEnvironment.expand(inst.getActualTemplateName());
            if (null == ((JCloudsCloud)c).getTemplate(tpln)) {
                throw new AbortException(String.format("The cloud named %s does not provide a template named %s.", cn, tpln));
            }
        }
        final String failedCloud = validateInstanceCaps();
        if (null != failedCloud) {
            throw new AbortException(String.format("Instance cap for cloud %s reached.", failedCloud));
        }

        Iterable<NodePlan> nodePlans = Iterables.transform(instancesToRun, new Function<InstancesToRun, NodePlan>() {

            public NodePlan apply(InstancesToRun instance) {
                String cloudName = instance.cloudName;
                String templateName = initialEnvironment.expand(instance.getActualTemplateName());
                Supplier<JCloudsNodeMetadata> nodeSupplier = JCloudsCloud.getByName(cloudName).getTemplate(templateName);
                return new NodePlan(cloudName, templateName, instance.count, instance.shouldSuspend, nodeSupplier);
            }

        });

        final TaskListenerLogger logger = new TaskListenerLogger(listener);
        final TerminateNodes terminateNodes = new TerminateNodes();

        ProvisionPlannedInstancesAndDestroyAllOnError provisioner = new ProvisionPlannedInstancesAndDestroyAllOnError(
                MoreExecutors.listeningDecorator(Computer.threadPoolForRemoting), logger, terminateNodes);

        // Start supplemental nodes. This blocks until all nodes are started or an error occurs.
        final Iterable<RunningNode> runningNodes = provisioner.apply(nodePlans);
        if (Iterables.size(runningNodes) > 0) {
            // register nodes for termination if build gets aborted
            JCloudsCloud.registerSupplementalCleanup(build, runningNodes);
            // publish env vars selected by publishMeta to supplemental nodes
            Map<String, String> metaData = new HashMap();
            for (String k : Util.fixNull(publishMeta).split(" +")) {
                if (initialEnvironment.containsKey(k)) {
                    String v = initialEnvironment.get(k);
                    metaData.put(k, v);
                }
            }
            // Each node gets also a JCLOUDS_SUPPLEMENTAL_INDEX published
            JCloudsCloud.publishMetadata(runningNodes, metaData);
            final Set<String> cloudsToPossiblyAbortWaiting = new HashSet<>();
            // Optionally, wait for phone-home, blocks until all nodes have reported back availability or timeout.
            try {
                final ConcurrentMap<JCloudsCloud, List<PhoneHomeMonitor>> waitParams = waitPhoneHomeSetup(runningNodes, listener.getLogger());
                if (!waitParams.isEmpty()) {
                    for (Map.Entry<JCloudsCloud, List<PhoneHomeMonitor>> entry : waitParams.entrySet()) {
                        cloudsToPossiblyAbortWaiting.add(entry.getKey().getName());
                        try {
                            for (PhoneHomeMonitor phm : entry.getValue()) {
                              phm.join();
                              entry.getKey().unregisterPhoneHomeMonitor(phm);
                          }
                        } catch (InterruptedException x) {
                            // abort all phone-home monitors that are still waiting
                            for (PhoneHomeMonitor phm : entry.getValue()) {
                                phm.ring();
                            }
                            throw x;
                        }
                    }
                }
            } catch (InterruptedException x) {
                throw new AbortException("Wait for phone-home aborted");
            }

            List<String> ips = getInstanceIPs(runningNodes, listener.getLogger());
            context.env(getEnvVarNameWithDefault(), ips.size() > 0 ? String.join(",", ips) : " ");
            context.setDisposer(new JCloudsBuildWrapperDisposer(runningNodes, terminateNodes, cloudsToPossiblyAbortWaiting));
        } else {
            context.env(getEnvVarNameWithDefault(), " ");
        }
    }

    private boolean isBeyondInstanceCap(final String cloudName, int numOfNewInstances) {
        final Jenkins.CloudList cl = Jenkins.get().clouds;
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

    private ConcurrentMap<JCloudsCloud, List<PhoneHomeMonitor>> waitPhoneHomeSetup(final Iterable<RunningNode> runningNodes, PrintStream logger) {
        ConcurrentMap<JCloudsCloud, List<PhoneHomeMonitor>> ret = new ConcurrentHashMap<>();
        ConcurrentMap<JCloudsCloud, ConcurrentMap<Integer, List<String>>> cloudWaitMap = new ConcurrentHashMap<>();
        for (RunningNode rn : runningNodes) {
            JCloudsCloud c = JCloudsCloud.getByName(rn.getCloudName());
            if (null != c) {
                JCloudsSlaveTemplate t = c.getTemplate(rn.getTemplateName());
                if (null != t && t.waitPhoneHome && t.waitPhoneHomeTimeout > 0) {
                    ConcurrentMap<Integer, List<String>> waitMap = cloudWaitMap.getOrDefault(c, new ConcurrentHashMap<>());
                    Integer wto = Integer.valueOf(t.waitPhoneHomeTimeout);
                    List<String> hosts = waitMap.getOrDefault(wto, new ArrayList<>());
                    hosts.add(rn.getNodeName());
                    waitMap.put(wto, hosts);
                    cloudWaitMap.put(c, waitMap);
                }
            }
        }
        for (Map.Entry<JCloudsCloud, ConcurrentMap<Integer, List<String>>> cwMap : cloudWaitMap.entrySet()) {
            for (Map.Entry<Integer, List<String>> entry : cwMap.getValue().entrySet()) {
                final PhoneHomeMonitor phm = new PhoneHomeMonitor(true, entry.getKey().intValue());
                phm.waitForPhoneHomeMultiple(entry.getValue(), logger);
                cwMap.getKey().registerPhoneHomeMonitor(phm);
                List<PhoneHomeMonitor> phmList = ret.getOrDefault(cwMap.getKey(), new ArrayList<>());
                phmList.add(phm);
                ret.put(cwMap.getKey(), phmList);
            }
        }
        return ret;
    }

    private List<String> getInstanceIPs(Iterable<RunningNode> runningNodes, PrintStream logger) {
        Builder<String> ips = ImmutableList.<String>builder();

        for (RunningNode rn : runningNodes) {
            String ip = rn.getNodeInstanceAddress(logger);
            if (null != ip) {
                ips.add(ip);
            }
        }

        return ips.build();
    }

    @Extension @Symbol("withJclouds")
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return "Create supplemental nodes";
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
