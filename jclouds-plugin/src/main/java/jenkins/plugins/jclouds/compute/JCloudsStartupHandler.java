package jenkins.plugins.jclouds.compute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;

import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.collect.Multimap;

import hudson.Extension;
import hudson.model.listeners.ItemListener;
import jenkins.model.Jenkins;

import jenkins.plugins.jclouds.compute.internal.TerminateNodes.Persistent;

/**
 * Startup handler for JClouds.
 * The sole purpose of this handler is to delete/suspend stale supplemental
 * instances which might have been left running when jenkins was forcibly restarted or
 * shutdown while running a job that uses JCloudsBuildWrapper.
 *
 * In order to do so, {@link jenkins.plugins.jclouds.compute.internal.TerminateNodes}
 * persists a list of nodes to shutdown before it is attempting the actual delete/suspend.
 * After successfully handling all nodes, the persisting xml file is removed. During a
 * hard shutdown/restart, this process is usually aborted prematurely and the xml file
 * remains. This handler then picks those files up at the next jenkins startup and
 * completes the operation.
 */
@Extension
public class JCloudsStartupHandler extends ItemListener {
    private static final Logger LOGGER = Logger.getLogger(JCloudsStartupHandler.class.getName());
    private final static String STALE_PATTERN = "jenkins.plugins.jclouds.compute.internal.TerminateNodes@*.xml";

    private final AtomicBoolean initial = new AtomicBoolean(true);

    @Override
    public void onLoaded() {
        if (initial.compareAndSet(true, false)) {
            for (Path path : listStaleNodeLists()) {
                Persistent p = new Persistent(path.toFile());
                try {
                    Multimap<String, String> work = p.getNodesToSuspend();
                    for (final String cloud : work.keySet()) {
                        JCloudsCloud c = JCloudsCloud.getByName(cloud);
                        if (null != c) {
                            final Collection<String> nodes = work.get(cloud);
                            final ComputeService cs = c.newCompute();
                            if (null != cs) {
                                try {
                                    LOGGER.info("Suspending stale nodes in cloud " + cloud + ": " + nodes);
                                    cs.suspendNodesMatching(new Predicate<NodeMetadata>() {
                                        public boolean apply(final NodeMetadata input) {
                                            return nodes.contains(input.getId());
                                        }
                                    });
                                } catch (Exception e) {
                                    LOGGER.info("Suspending on cloud: " + cloud + "; nodes: " + nodes + ": " + e);
                                }
                                cs.getContext().close();
                            }
                        }
                    }
                    work = p.getNodesToDestroy();
                    for (final String cloud : work.keySet()) {
                        JCloudsCloud c = JCloudsCloud.getByName(cloud);
                        if (null != c) {
                            final Collection<String> nodes = work.get(cloud);
                            final ComputeService cs = c.newCompute();
                            if (null != cs) {
                                try {
                                    LOGGER.info("Destroying stale nodes in cloud " + cloud + ": " + nodes);
                                    cs.destroyNodesMatching(new Predicate<NodeMetadata>() {
                                        public boolean apply(final NodeMetadata input) {
                                            return nodes.contains(input.getId());
                                        }
                                    });
                                } catch (Exception e) {
                                    LOGGER.info("Destroying on cloud: " + cloud + "; nodes: " + nodes + ": " + e);
                                }
                                cs.getContext().close();
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Stale node cleanup", e);
                }
                p.remove();
            }
        }
    }

    private List<Path> listStaleNodeLists() {
        List<Path> ret = new ArrayList<>();
        Path jroot = Jenkins.getInstance().getRootDir().toPath();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(jroot, STALE_PATTERN)) {
            for (Path entry: ds) {
                ret.add(entry);
            }
        } catch (Exception ex) {
            if (ex instanceof DirectoryIteratorException) {
                LOGGER.warning("Could not iterate jenkins root: " + ex.getCause());
            } else {
                LOGGER.warning("Could not iterate jenkins root: " + ex);
            }
        }
        return ret;
    }
}
