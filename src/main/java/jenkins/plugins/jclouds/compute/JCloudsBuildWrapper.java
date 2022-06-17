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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.Symbol;

import jenkins.plugins.jclouds.compute.internal.JCloudsNodeMetadata;
import jenkins.plugins.jclouds.compute.internal.NodePlan;
import jenkins.plugins.jclouds.compute.internal.ProvisionPlannedInstancesAndDestroyAllOnError;
import jenkins.plugins.jclouds.compute.internal.RunningNode;
import jenkins.plugins.jclouds.compute.internal.TerminateNodes;
import jenkins.plugins.jclouds.internal.TaskListenerLogger;

import org.jclouds.compute.ComputeService;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.MoreExecutors;

public class JCloudsBuildWrapper extends SimpleBuildWrapper implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<InstancesToRun> instancesToRun;

    @DataBoundConstructor
    public JCloudsBuildWrapper(List<InstancesToRun> instancesToRun) {
        this.instancesToRun = instancesToRun;
    }

    public List<InstancesToRun> getInstancesToRun() {
        return instancesToRun;
    }

    @Override
    public boolean requiresWorkspace() {
        return false;
    }

    //
    // convert Jenkins static stuff into pojos; performing as little critical stuff here as
    // possible, as this method is very hard to test due to static usage, etc.
    //
    @Override
    public void setUp(Context context, Run<?,?> build, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {

        final String failedCloud = validateInstanceCaps();
        if (null != failedCloud) {
            listener.fatalError("Unable to launch supplemental nodes:");
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
                String templateName = initialEnvironment.expand(instance.getActualTemplateName());
                Supplier<JCloudsNodeMetadata> nodeSupplier = JCloudsCloud.getByName(cloudName).getTemplate(templateName);
                // take the hit here, as opposed to later
                computeCache.getUnchecked(cloudName);
                return new NodePlan(cloudName, templateName, instance.count, instance.shouldSuspend, nodeSupplier);
            }

        });

        // converting to a logger as it is an interface and easier to test
        final TaskListenerLogger logger = new TaskListenerLogger(listener);

        final TerminateNodes terminateNodes = new TerminateNodes(logger, computeCache);

        ProvisionPlannedInstancesAndDestroyAllOnError provisioner = new ProvisionPlannedInstancesAndDestroyAllOnError(
                MoreExecutors.listeningDecorator(Computer.threadPoolForRemoting), logger, terminateNodes);

        final Iterable<RunningNode> runningNodes = provisioner.apply(nodePlans);
        final Set<JCloudsCloud> waitClouds = waitPhoneHomeSetup(runningNodes, listener.getLogger());
        if (!waitClouds.isEmpty()) {
          for (JCloudsCloud waitCloud : waitClouds) {
            waitCloud.phoneHomeWaitAll();
          }
        }
        List<String> ips = getInstanceIPs(runningNodes, listener.getLogger());
        context.env("JCLOUDS_IPS", ips.size() > 0 ? String.join(",", ips) : " ");
        context.setDisposer(new Disposer() {
          @Override
          public void tearDown(Run<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
            for (JCloudsCloud waitCloud : waitClouds) {
              waitCloud.phoneHomeAbort();
            }
            terminateNodes.apply(runningNodes);
          }
        });
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

    private Set<JCloudsCloud> waitPhoneHomeSetup(final Iterable<RunningNode> runningNodes, PrintStream logger) {
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
                phm.waitForPhoneHome(entry.getValue(), logger);
                cwMap.getKey().registerPhoneHomeMonitor(phm);
            }
        }
        return cloudWaitMap.keySet();
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
