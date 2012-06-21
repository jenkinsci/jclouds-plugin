package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.slaves.OfflineCause;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import jenkins.plugins.jclouds.compute.internal.NodePlan;
import jenkins.plugins.jclouds.compute.internal.ProvisionPlannedInstancesAndDestroyAllOnError;
import jenkins.plugins.jclouds.compute.internal.RunningNode;
import jenkins.plugins.jclouds.compute.internal.TerminateNodes;
import jenkins.plugins.jclouds.internal.BuildListenerLogger;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.logging.Logger;
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

public class JCloudsOneOffSlave extends BuildWrapper {
   @DataBoundConstructor
   public JCloudsOneOffSlave() {
   }

    //   
    // convert Jenkins staticy stuff into pojos; performing as little critical stuff here as
    // possible, as this method is very hard to test due to static usage, etc.
    //   
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, final BuildListener listener) {
        if (JCloudsComputer.class.isInstance(build.getExecutor().getOwner())) {
            final JCloudsComputer c = (JCloudsComputer)build.getExecutor().getOwner();
            return new Environment() {
                @Override
                public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException,
                                                                                                  InterruptedException {
                    
                    c.setTemporarilyOffline(true, OfflineCause.create(Messages._OneOffCause()));
                    return true;
                }
            };
        } else {
            return new Environment() {
            };
        }
       
   }

   @Extension
   public static final class DescriptorImpl extends BuildWrapperDescriptor {
      @Override
      public String getDisplayName() {
         return "JClouds Single-Use Slave";
      }

      @Override
      public boolean isApplicable(AbstractProject item) {
         return true;
      }

   }
}
