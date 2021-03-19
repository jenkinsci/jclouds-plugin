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

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import jenkins.model.Jenkins;
import hudson.XmlFile;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.logging.Logger;

import shaded.com.google.common.base.Function;
import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.cache.LoadingCache;
import shaded.com.google.common.collect.ImmutableMultimap;
import shaded.com.google.common.collect.Multimap;
import shaded.com.google.common.collect.ArrayListMultimap;
import shaded.com.google.common.collect.ImmutableMultimap.Builder;

public class TerminateNodes implements Function<Iterable<RunningNode>, Void> {

    private final Logger logger;
    private final LoadingCache<String, ComputeService> computeCache;

    public static class Persistent {
        private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(TerminateNodes.class.getName());

        private final transient File f;
        private Multimap<String, String> nodesToSuspend;
        private Multimap<String, String> nodesToDestroy;

        public Persistent(final String name, final Multimap<String, String> toSuspend, final Multimap<String, String> toDestroy) {
            nodesToSuspend = toSuspend;
            nodesToDestroy = toDestroy;
            f = new File(Jenkins.getInstance().getRootDir(), name + ".xml");
            XmlFile xf = new XmlFile(f);
            try {
                xf.write(this);
            } catch (IOException x) {
                LOGGER.warning("Failed to persist");
            }
        }

        public Persistent(final File src) {
            f = src;
            XmlFile xf = new XmlFile(f);
            try {
                xf.unmarshal(this);
            } catch (IOException x) {
                nodesToSuspend =  ArrayListMultimap.create();
                nodesToDestroy =  ArrayListMultimap.create();
                LOGGER.warning("Failed to unmarshal");
            }
        }

        public void remove() {
            if (!f.delete()) {
                LOGGER.warning("Could not delete " + f.getPath());
            }
        }

        public Multimap<String, String> getNodesToSuspend() {
            return nodesToSuspend;
        }

        public Multimap<String, String> getNodesToDestroy() {
            return nodesToDestroy;
        }
    }


    public TerminateNodes(Logger logger, LoadingCache<String, ComputeService> computeCache) {
        this.logger = logger;
        this.computeCache = computeCache;
    }

    public Void apply(Iterable<RunningNode> runningNode) {
        if (null != runningNode) {
            Builder<String, String> cloudNodesToSuspendBuilder = ImmutableMultimap.<String, String>builder();
            Builder<String, String> cloudNodesToDestroyBuilder = ImmutableMultimap.<String, String>builder();
            for (RunningNode cloudTemplateNode : runningNode) {
                String id = cloudTemplateNode.getNode().getId();
                String name = cloudTemplateNode.getCloudName();
                if (cloudTemplateNode.isSuspendOrTerminate()) {
                    cloudNodesToSuspendBuilder.put(name, id);
                } else {
                    cloudNodesToDestroyBuilder.put(name, id);
                }
            }
            Multimap<String, String> toSuspend = cloudNodesToSuspendBuilder.build();
            Multimap<String, String> toDestroy = cloudNodesToDestroyBuilder.build();

            Persistent p = new Persistent(this.toString(), toSuspend, toDestroy);
            suspendIfSupported(toSuspend);
            destroy(toDestroy);
            p.remove();
        }
        return null;
    }

    private void destroy(Multimap<String, String> cloudNodesToDestroy) {
        for (final String cloudToDestroy : cloudNodesToDestroy.keySet()) {
            final Collection<String> nodesToDestroy = cloudNodesToDestroy.get(cloudToDestroy);
            logger.info("Destroying nodes: " + nodesToDestroy);
            computeCache.getUnchecked(cloudToDestroy).destroyNodesMatching(new Predicate<NodeMetadata>() {
                public boolean apply(NodeMetadata input) {
                    return null != input && nodesToDestroy.contains(input.getId());
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
                        return null != input && nodesToSuspend.contains(input.getId());
                    }

                });
            } catch (UnsupportedOperationException e) {
                logger.info("Suspending unsupported on cloud: " + cloudToSuspend + "; nodes: " + nodesToSuspend + ": " + e);
            }
        }
    }
}
