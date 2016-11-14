package jenkins.plugins.jclouds.compute.internal;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class TerminateNodes implements Function<Iterable<RunningNode>, Void> {

    private final Logger logger;
    private final LoadingCache<String, ComputeService> computeCache;

    @SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
    public static class Persistent implements Serializable {
        private static final long serialVersionUID = 3970810124738772984L;
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
        Builder<String, String> cloudNodesToSuspendBuilder = ImmutableMultimap.<String, String>builder();
        Builder<String, String> cloudNodesToDestroyBuilder = ImmutableMultimap.<String, String>builder();
        for (RunningNode cloudTemplateNode : runningNode) {
            if (cloudTemplateNode.isSuspendOrTerminate()) {
                cloudNodesToSuspendBuilder.put(cloudTemplateNode.getCloudName(), cloudTemplateNode.getNode().getId());
            } else {
                cloudNodesToDestroyBuilder.put(cloudTemplateNode.getCloudName(), cloudTemplateNode.getNode().getId());
            }
        }
        Multimap<String, String> toSuspend = cloudNodesToSuspendBuilder.build();
        Multimap<String, String> toDestroy = cloudNodesToDestroyBuilder.build();

        Persistent p = new Persistent(this.toString(), toSuspend, toDestroy);
        suspendIfSupported(toSuspend);
        destroy(toDestroy);
        p.remove();
        return null;
    }

    private void destroy(Multimap<String, String> cloudNodesToDestroy) {
        for (final String cloudToDestroy : cloudNodesToDestroy.keySet()) {
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
