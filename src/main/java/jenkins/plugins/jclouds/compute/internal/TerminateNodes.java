package jenkins.plugins.jclouds.compute.internal;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeState;
import org.jclouds.logging.Logger;

import com.google.common.base.Function;
import com.google.common.cache.LoadingCache;

public class TerminateNodes implements Function<Iterable<RunningNode>, Void> {
   private final Logger logger;
   private final LoadingCache<String, ComputeService> computeCache;

   public TerminateNodes(Logger logger, LoadingCache<String, ComputeService> computeCache) {
      this.logger = logger;
      this.computeCache = computeCache;
   }

   public Void apply(Iterable<RunningNode> runningNode) {
      for (RunningNode cloudTemplateNode : runningNode) {
         try {
            terminateNode(cloudTemplateNode.getCloudName(), cloudTemplateNode.getNode(), cloudTemplateNode
                     .isSuspendOrTerminate(), logger);
         } catch (UnsupportedOperationException e) {
            logger.info("Error terminating node " + cloudTemplateNode.getNode().getId() + ": " + e);
         }
      }
      return null;
   }

   /**
    * Destroy the node calls {@link ComputeService#destroyNode}
    * 
    */
   public void terminateNode(String cloudName, NodeMetadata n, boolean suspendOrTerminate, Logger logger)
            throws UnsupportedOperationException {
      final ComputeService compute = computeCache.getUnchecked(cloudName);
      if (n != null && n.getState().equals(NodeState.RUNNING)) {
         if (suspendOrTerminate) {
            logger.info("Suspending the Node : " + n.getId());
            compute.suspendNode(n.getId());
         } else {
            logger.info("Terminating the Node : " + n.getId());
            compute.destroyNode(n.getId());
         }
      } else {
         logger.info("Node " + n.getId() + " is already not running.");
      }
   }
}