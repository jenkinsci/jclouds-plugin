package jenkins.plugins.jclouds.compute.internal;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.logging.Logger;

import shaded.com.google.common.base.Function;
import shaded.com.google.common.collect.ImmutableList;
import shaded.com.google.common.util.concurrent.FutureCallback;
import shaded.com.google.common.util.concurrent.Futures;
import shaded.com.google.common.util.concurrent.ListenableFuture;
import shaded.com.google.common.util.concurrent.ListeningExecutorService;

public class ProvisionPlannedInstancesAndDestroyAllOnError implements Function<Iterable<NodePlan>, Iterable<RunningNode>> {
    private final ListeningExecutorService executor;
    private final Logger logger;
    private final Function<Iterable<RunningNode>, Void> terminateNodes;

    public ProvisionPlannedInstancesAndDestroyAllOnError(ListeningExecutorService executor, Logger logger, Function<Iterable<RunningNode>, Void> terminateNodes) {
        this.executor = executor;
        this.logger = logger;
        this.terminateNodes = terminateNodes;
    }

    public Iterable<RunningNode> apply(Iterable<NodePlan> nodePlans) {
        final ImmutableList.Builder<RunningNode> cloudTemplateNodeBuilder = ImmutableList.<RunningNode>builder();

        final ImmutableList.Builder<ListenableFuture<NodeMetadata>> plannedInstancesBuilder = ImmutableList.<ListenableFuture<NodeMetadata>>builder();

        final AtomicInteger failedLaunches = new AtomicInteger();

        for (final NodePlan nodePlan : nodePlans) {
            for (int i = 0; i < nodePlan.getCount(); i++) {
                final int index = i;
                logger.info("Queuing cloud instance: #%d %d, %s %s", index, nodePlan.getCount(), nodePlan.getCloudName(), nodePlan.getTemplateName());

                ListenableFuture<NodeMetadata> provisionTemplate = executor.submit(new RetrySupplierOnException(nodePlan.getNodeSupplier(), logger));

                Futures.addCallback(provisionTemplate, new FutureCallback<NodeMetadata>() {
                    public void onSuccess(NodeMetadata result) {
                        if (result != null) {
                            cloudTemplateNodeBuilder.add(new RunningNode(nodePlan.getCloudName(), nodePlan.getTemplateName(), nodePlan.isSuspendOrTerminate(),
                                    result));
                        } else {
                            failedLaunches.incrementAndGet();
                        }
                    }

                    public void onFailure(Throwable t) {
                        failedLaunches.incrementAndGet();
                        logger.warn(t, "Error while launching instance: #%d %d, %s %s", index, nodePlan.getCount(), nodePlan.getCloudName(),
                                nodePlan.getTemplateName());
                    }
                });

                plannedInstancesBuilder.add(provisionTemplate);

            }
        }

        // block until all complete
        List<NodeMetadata> nodesActuallyLaunched = Futures.getUnchecked(Futures.successfulAsList(plannedInstancesBuilder.build()));

        final ImmutableList<RunningNode> cloudTemplateNodes = cloudTemplateNodeBuilder.build();

        assert cloudTemplateNodes.size() == nodesActuallyLaunched.size() : String.format(
                "expected nodes from callbacks to be the same count as those from the list of futures!%n" + "fromCallbacks:%s%nfromFutures%s%n",
                cloudTemplateNodes, nodesActuallyLaunched);

        if (failedLaunches.get() > 0) {
            terminateNodes.apply(cloudTemplateNodes);
            throw new IllegalStateException("One or more instances failed to launch.");
        }
        return cloudTemplateNodes;
    }

}
