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
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;
import hudson.XmlFile;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.compute.JCloudsCloud;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;

public class TerminateNodes implements Function<Iterable<RunningNode>, Void>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(TerminateNodes.class.getName());

    public static class Persistent {
        private static final Logger LOGGER = Logger.getLogger(Persistent.class.getName());

        private final transient File f;
        private Multimap<String, String> nodesToSuspend;
        private Multimap<String, String> nodesToDestroy;

        public Persistent(
                final String name, final Multimap<String, String> toSuspend, final Multimap<String, String> toDestroy) {
            nodesToSuspend = toSuspend;
            nodesToDestroy = toDestroy;
            f = new File(Jenkins.get().getRootDir(), name + ".xml");
            XmlFile xf = new XmlFile(f);
            try {
                xf.write(this);
            } catch (IOException x) {
                LOGGER.warning(String.format("Failed to persist %s: %s", f.getAbsolutePath(), x.getMessage()));
            }
        }

        public Persistent(final File src) {
            f = src;
            XmlFile xf = new XmlFile(f);
            try {
                xf.unmarshal(this);
            } catch (IOException x) {
                nodesToSuspend = ArrayListMultimap.create();
                nodesToDestroy = ArrayListMultimap.create();
                LOGGER.warning(String.format("Failed to unmarshal %s: %s", f.getAbsolutePath(), x.getMessage()));
            }
        }

        public void remove() {
            if (!f.delete()) {
                LOGGER.warning("Could not delete " + f.getAbsolutePath());
            }
        }

        public Multimap<String, String> getNodesToSuspend() {
            return nodesToSuspend;
        }

        public Multimap<String, String> getNodesToDestroy() {
            return nodesToDestroy;
        }
    }

    public TerminateNodes() {}

    private static ComputeService getCloudCompute(String cloud) {
        return ((JCloudsCloud) Jenkins.get().clouds.getByName(cloud)).getCompute();
    }

    public Void apply(Iterable<RunningNode> runningNodes) {
        if (null != runningNodes) {
            Builder<String, String> cloudNodesToSuspendBuilder = ImmutableMultimap.<String, String>builder();
            Builder<String, String> cloudNodesToDestroyBuilder = ImmutableMultimap.<String, String>builder();
            for (RunningNode node : runningNodes) {
                String id = node.getNodeId();
                String name = node.getCloudName();
                if (node.getShouldSuspend()) {
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
            LOGGER.info("Destroying supplemental nodes: " + nodesToDestroy);
            getCloudCompute(cloudToDestroy).destroyNodesMatching(new Predicate<NodeMetadata>() {
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
                LOGGER.info("Suspending supplemental nodes: " + nodesToSuspend);
                getCloudCompute(cloudToSuspend).suspendNodesMatching(new Predicate<NodeMetadata>() {

                    public boolean apply(NodeMetadata input) {
                        return null != input && nodesToSuspend.contains(input.getId());
                    }
                });
            } catch (UnsupportedOperationException e) {
                LOGGER.warning("Suspend unsupported on cloud: " + cloudToSuspend + "; affected nodes: " + nodesToSuspend
                        + ": " + e);
            }
        }
    }
}
