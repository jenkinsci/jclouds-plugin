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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.jclouds.logging.Logger;

public class ProvisionPlannedInstancesAndDestroyAllOnError
        implements Function<Iterable<NodePlan>, Iterable<RunningNode>> {

    static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(ProvisionPlannedInstancesAndDestroyAllOnError.class.getName());

    private final ListeningExecutorService executor;
    private final Logger logger;
    private final Function<Iterable<RunningNode>, Void> terminateNodes;

    public ProvisionPlannedInstancesAndDestroyAllOnError(
            ListeningExecutorService executor, Logger logger, Function<Iterable<RunningNode>, Void> terminateNodes) {
        this.executor = executor;
        this.logger = logger;
        this.terminateNodes = terminateNodes;
    }

    public Iterable<RunningNode> apply(Iterable<NodePlan> nodePlans) {
        final ImmutableList.Builder<RunningNode> nodeBuilder = ImmutableList.<RunningNode>builder();

        if (null != nodePlans) {

            final AtomicInteger failedCount = new AtomicInteger();
            final AtomicInteger successCount = new AtomicInteger();
            final AtomicInteger totalCount = new AtomicInteger();

            for (final NodePlan nodePlan : nodePlans) {
                if (nodePlan.getCount() > 0) {
                    totalCount.addAndGet(nodePlan.getCount());
                    String plural = nodePlan.getCount() > 1 ? "s" : "";
                    LOGGER.info(String.format(
                            "Launching %d supplemental node%s from template %s in cloud %s",
                            nodePlan.getCount(), plural, nodePlan.getTemplateName(), nodePlan.getCloudName()));
                    logger.info(
                            "Launching %d supplemental node%s from template %s in cloud %s",
                            nodePlan.getCount(), plural, nodePlan.getTemplateName(), nodePlan.getCloudName());
                    for (int i = 0; i < nodePlan.getCount(); i++) {
                        final int index = i;
                        ListenableFuture<JCloudsNodeMetadata> provisionTemplate =
                                executor.submit(new RetryOnExceptionSupplier(nodePlan.getNodeSupplier(), logger));

                        Futures.addCallback(
                                provisionTemplate,
                                new FutureCallback<JCloudsNodeMetadata>() {
                                    public void onSuccess(JCloudsNodeMetadata result) {
                                        if (result != null) {
                                            synchronized (nodeBuilder) {
                                                RunningNode rn = new RunningNode(
                                                        nodePlan.getCloudName(),
                                                        nodePlan.getTemplateName(),
                                                        nodePlan.getShouldSuspend(),
                                                        result);
                                                nodeBuilder.add(rn);
                                                successCount.incrementAndGet();
                                            }
                                        } else {
                                            failedCount.incrementAndGet();
                                        }
                                    }

                                    public void onFailure(Throwable t) {
                                        failedCount.incrementAndGet();
                                        LOGGER.log(
                                                java.util.logging.Level.WARNING,
                                                String.format(
                                                        "Error launching supplemental node #%d of %d from template %s in cloud %s",
                                                        index,
                                                        nodePlan.getCount(),
                                                        plural,
                                                        nodePlan.getTemplateName(),
                                                        nodePlan.getCloudName()),
                                                t);
                                        logger.warn(
                                                t,
                                                "Error launching supplemental node #%d of %d from template %s in cloud %s",
                                                index,
                                                nodePlan.getCount(),
                                                plural,
                                                nodePlan.getTemplateName(),
                                                nodePlan.getCloudName());
                                    }
                                },
                                executor);
                    }
                }
            }

            if (0 < totalCount.get()) {
                // REALLY block until all complete
                while (successCount.get() + failedCount.get() < totalCount.get()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        LOGGER.warning("Interrupt while waiting for node launch");
                    }
                }

                final ImmutableList<RunningNode> runningNodes = nodeBuilder.build();
                if (failedCount.get() > 0) {
                    terminateNodes.apply(runningNodes);
                    throw new IllegalStateException("One or more nodes failed to launch.");
                }

                LOGGER.info(String.format("launched %d supplemental nodes", successCount.get()));
                logger.info("launched %d supplemental nodes", successCount.get());

                return runningNodes;
            }
        }
        return nodeBuilder.build();
    }
}
