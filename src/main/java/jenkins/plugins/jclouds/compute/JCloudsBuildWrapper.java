package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeState;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Callable;

public class JCloudsBuildWrapper extends BuildWrapper {
    public List<InstancesToRun> instancesToRun;
    public final int MAX_ATTEMPTS = 5;

    @DataBoundConstructor
    public JCloudsBuildWrapper(List<InstancesToRun> instancesToRun) {
        this.instancesToRun = instancesToRun;
    }
    
    public InstancesToRun getMatchingInstanceToRun(String cloudName, String templateName) {
        for (InstancesToRun i : instancesToRun) {
            if (i.cloudName.equals(cloudName) && i.templateName.equals(templateName)) {
                return i;
            }
        }
        return null;
    }
    
    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {

        final Map<String,Map<String,List<NodeMetadata>>> instances;
        Map<String,Map<String,List<NodeMetadata>>> spawnedInstances = new HashMap<String,Map<String,List<NodeMetadata>>>();
        List<PlannedInstance> plannedInstances = new ArrayList<PlannedInstance>();
        
        for (InstancesToRun instance : instancesToRun) {
            final JCloudsCloud cloud = JCloudsCloud.getByName(instance.cloudName);
            final JCloudsSlaveTemplate template = cloud.getTemplate(instance.templateName);

            for (int i=0; i < instance.count; i++) {
                            
                plannedInstances.add(new PlannedInstance(instance.cloudName,
                                                         instance.templateName,
                                                         i,
                                                         Computer.threadPoolForRemoting.submit(new Callable<NodeMetadata>() {
                                                                 public NodeMetadata call() throws Exception {
                                                                     int attempts = 0;
                                                                     
                                                                     while (attempts < MAX_ATTEMPTS) {
                                                                         attempts++;
                                                                         try {
                                                                             NodeMetadata n = template.provision();
                                                                             if (n != null) {
                                                                                 return n;
                                                                             }
                                                                         } catch (RuntimeException e) {
                                                                             // Something to log the e.getCause() which should be a RunNodesException
                                                                         }
                                                                     }

                                                                     return null;
                                                                 }
                                                             })));
                listener.getLogger().println("Queuing cloud instance: #" + i + " of " + instance.count + ", " + instance.cloudName + " " + instance.templateName);
            }
        }

        int failedLaunches = 0;

        while (plannedInstances.size() > 0) {
            for (Iterator<PlannedInstance> itr = plannedInstances.iterator(); itr.hasNext();) {
                PlannedInstance f = itr.next();
                if (f.future.isDone()) {
                    try {
                        Map<String,List<NodeMetadata>> cloudMap = spawnedInstances.get(f.cloudName);
                        if (cloudMap==null) {
                            spawnedInstances.put(f.cloudName, cloudMap = new HashMap<String,List<NodeMetadata>>());
                        }
                        List<NodeMetadata> templateList = cloudMap.get(f.templateName);
                        if (templateList==null) {
                            cloudMap.put(f.templateName, templateList = new ArrayList<NodeMetadata>());
                        }
                        
                        NodeMetadata n = f.future.get();
                        
                        if (n != null) {
                            templateList.add(n);
                        } else {
                            failedLaunches++;
                        }
                    } catch (InterruptedException e) {
                        failedLaunches++;
                        listener.error("Interruption while launching instance " + f.index + " of " + f.cloudName + "/" + f.templateName + ": " + e);
                    } catch (ExecutionException e) {
                        failedLaunches++;
                        listener.error("Error while launching instance " + f.index + " of " + f.cloudName + "/" + f.templateName + ": " + e.getCause());
                    }
                    
                    itr.remove();
                }
            }
        }

        instances = Collections.unmodifiableMap(spawnedInstances);
        
        if (failedLaunches > 0) {
            terminateNodes(instances, listener.getLogger());
            throw new IOException("One or more instances failed to launch.");
        }

            
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String,String> env) {
                List<String> ips = getInstanceIPs(instances, listener.getLogger());
                env.put("CLOUD_IPS", Util.join(ips, ","));
            }                
                
            @Override
            public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
                terminateNodes(instances, listener.getLogger());

                return true;
                
            }
                
        };
            
    }

    public List<String> getInstanceIPs(Map<String,Map<String,List<NodeMetadata>>> instances, PrintStream logger) {
        List<String> ips = new ArrayList<String>();

        for (String cloudName : instances.keySet()) {
            for (String templateName : instances.get(cloudName).keySet()) {
                for (NodeMetadata n : instances.get(cloudName).get(templateName)) {
                    String[] possibleIPs = JCloudsLauncher.getConnectionAddresses(n, logger);
                    if (possibleIPs[0] != null) {
                        ips.add(possibleIPs[0]);
                    }
                }
            }
        }

        return ips;
    }

        

    public void terminateNodes(Map<String,Map<String,List<NodeMetadata>>> instances, PrintStream logger) {
        for (String cloudName : instances.keySet()) {
            for (String templateName : instances.get(cloudName).keySet()) {
                InstancesToRun i = getMatchingInstanceToRun(cloudName, templateName);
                for (NodeMetadata n : instances.get(cloudName).get(templateName)) {
                    terminateNode(cloudName, n.getId(), i.suspendOrTerminate, logger);
                }
            }
        }
    }

    
    /**
     * Destroy the node calls {@link ComputeService#destroyNode}
     *
     */
    public void terminateNode(String cloudName, String nodeId, boolean suspendOrTerminate, PrintStream logger) {
        final ComputeService compute = JCloudsCloud.getByName(cloudName).getCompute();
        if (compute.getNodeMetadata(nodeId) != null &&
            compute.getNodeMetadata(nodeId).getState().equals(NodeState.RUNNING)) {
            if (suspendOrTerminate) {
                logger.println("Suspending the Node : " + nodeId);
                compute.suspendNode(nodeId);
            } else {
                logger.println("Terminating the Node : " + nodeId);
                compute.destroyNode(nodeId);
            }
        } else {
            logger.println("Node " + nodeId + " is already not running.");
        }
    }


            
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return "JClouds Instance Creation";
        }

        @Override
        public boolean isApplicable(AbstractProject item) {
            return true;
        }
                     
    }


    public static final class PlannedInstance {
        public final String cloudName;
        public final String templateName;
        public final Future<NodeMetadata> future;
        public final int index;
        
        public PlannedInstance(String cloudName, String templateName, int index, Future<NodeMetadata> future) {
            this.cloudName = cloudName;
            this.templateName = templateName;
            this.index = index;
            this.future = future;
        }
    }
    
    
}
