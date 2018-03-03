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
package jenkins.plugins.jclouds.compute.internal;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.logging.Logger;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

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

                ListenableFuture<NodeMetadata> provisionTemplate = executor.submit(new RetryOnExceptionSupplier(nodePlan.getNodeSupplier(), logger));

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
