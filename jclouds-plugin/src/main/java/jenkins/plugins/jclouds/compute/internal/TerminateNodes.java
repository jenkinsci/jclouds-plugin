package jenkins.plugins.jclouds.compute.internal;

import java.util.Collection;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.logging.Logger;

import shaded.com.google.common.base.Function;
import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.cache.LoadingCache;
import shaded.com.google.common.collect.ImmutableMultimap;
import shaded.com.google.common.collect.Multimap;
import shaded.com.google.common.collect.ImmutableMultimap.Builder;

public class TerminateNodes implements Function<Iterable<RunningNode>, Void> {
    private final Logger logger;
    private final LoadingCache<String, ComputeService> computeCache;

    public TerminateNodes(Logger logger, LoadingCache<String, ComputeService> computeCache) {
        this.logger = logger;
        this.computeCache = computeCache;
    }

    public Void apply(Iterable<RunningNode> runningNode) {
        Builder<String, String> cloudNodesToSuspendBuilder = ImmutableMultimap.<String, String>builder();
        Builder<String, String> cloudNodesToDestroyBuilder = ImmutableMultimap.<String, String>builder();
        for (RunningNode cloudTemplateNode : runningNode) {
            if (cloudTemplateNode.isSuspendOrTerminate()) {
                cloudNodesToSuspendBuilder.put(cloudTemplateNode.getCloudName(), cloudTemplateNode.getNode().getId());
            } else {
                cloudNodesToDestroyBuilder.put(cloudTemplateNode.getCloudName(), cloudTemplateNode.getNode().getId());
            }
        }
        Multimap<String, String> cloudNodesToSuspend = cloudNodesToSuspendBuilder.build();
        Multimap<String, String> cloudNodesToDestroy = cloudNodesToDestroyBuilder.build();

        suspendIfSupported(cloudNodesToSuspend);
        destroy(cloudNodesToDestroy);
        return null;
    }

    private void destroy(Multimap<String, String> cloudNodesToDestroy) {
        for (String cloudToDestroy : cloudNodesToDestroy.keySet()) {
            final Collection<String> nodesToDestroy = cloudNodesToDestroy.get(cloudToDestroy);
            logger.info("Destroying nodes: " + nodesToDestroy);
            computeCache.getUnchecked(cloudToDestroy).destroyNodesMatching(new Predicate<NodeMetadata>() {

                public boolean apply(NodeMetadata input) {
                    return nodesToDestroy.contains(input.getId());
                }

            });
        }
    }

    private void suspendIfSupported(Multimap<String, String> cloudNodesToSuspend) {
        for (String cloudToSuspend : cloudNodesToSuspend.keySet()) {
            final Collection<String> nodesToSuspend = cloudNodesToSuspend.get(cloudToSuspend);
            try {
                logger.info("Suspending nodes: " + nodesToSuspend);
                computeCache.getUnchecked(cloudToSuspend).suspendNodesMatching(new Predicate<NodeMetadata>() {

                    public boolean apply(NodeMetadata input) {
                        return nodesToSuspend.contains(input.getId());
                    }

                });
            } catch (UnsupportedOperationException e) {
                logger.info("Suspending unsupported on cloud: " + cloudToSuspend + "; nodes: " + nodesToSuspend + ": " + e);
            }
        }
    }
}
