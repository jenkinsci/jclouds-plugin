package jenkins.plugins.jclouds.compute.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.List;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.NodeMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TerminateNodesTest {

    private static ComputeService compute;

    @BeforeAll
    static void setUp() {
        boolean heavy = Boolean.getBoolean("heavyLoadTests");
        assumeTrue(heavy);
        try {
            compute = ContextBuilder.newBuilder("stub")
                    .buildView(ComputeServiceContext.class)
                    .getComputeService();
        } catch (NoClassDefFoundError e) {
            // Thrown on jenkins.ci.cloudbees.com (probably due to a classloader isolation prob with
            // guice-assistedinject)
        }
    }

    @AfterAll
    static void tearDown() {
        if (null != compute) {
            compute.getContext().close();
        }
    }

    @Test
    void testSuspendOnlySuspendsNodesInQuestion() throws Exception {
        if (null != compute) {
            List<NodeMetadata> nodes = ImmutableList.copyOf(compute.createNodesInGroup("suspend", 10));
            List<List<NodeMetadata>> split = Lists.partition(nodes, 5);

            Iterable<RunningNode> runningNodesToSuspend = Iterables.transform(
                    split.get(0),
                    input ->
                            new RunningNode("stub", "template", true, JCloudsNodeMetadata.fromNodeMetadata(input, "")));

            newTerminateNodes(compute).apply(runningNodesToSuspend);

            for (NodeMetadata node : split.get(0))
                assertEquals(
                        NodeMetadata.Status.SUSPENDED,
                        compute.getNodeMetadata(node.getId()).getStatus());
            for (NodeMetadata node : split.get(1))
                assertEquals(
                        NodeMetadata.Status.RUNNING,
                        compute.getNodeMetadata(node.getId()).getStatus());
        }
    }

    @Test
    void testDestroyOnlyDestroysNodesInQuestion() throws Exception {
        if (null != compute) {
            List<NodeMetadata> nodes = ImmutableList.copyOf(compute.createNodesInGroup("destroy", 10));
            List<List<NodeMetadata>> split = Lists.partition(nodes, 5);

            Iterable<RunningNode> runningNodesToDestroy = Iterables.transform(
                    split.get(0),
                    input -> new RunningNode(
                            "stub", "template", false, JCloudsNodeMetadata.fromNodeMetadata(input, "")));

            newTerminateNodes(compute).apply(runningNodesToDestroy);

            for (NodeMetadata node : split.get(0)) assertNull(compute.getNodeMetadata(node.getId()));
            for (NodeMetadata node : split.get(1))
                assertEquals(
                        NodeMetadata.Status.RUNNING,
                        compute.getNodeMetadata(node.getId()).getStatus());
        }
    }

    @Test
    void testSuspendAndDestroy() throws Exception {
        if (null != compute) {
            List<NodeMetadata> nodes = ImmutableList.copyOf(compute.createNodesInGroup("suspenddestroy", 10));
            List<List<NodeMetadata>> split = Lists.partition(nodes, 5);

            Iterable<RunningNode> runningNodesToSuspend = Iterables.transform(
                    split.get(0),
                    input ->
                            new RunningNode("stub", "template", true, JCloudsNodeMetadata.fromNodeMetadata(input, "")));

            Iterable<RunningNode> runningNodesToDestroy = Iterables.transform(
                    split.get(1),
                    input -> new RunningNode(
                            "stub", "template", false, JCloudsNodeMetadata.fromNodeMetadata(input, "")));

            newTerminateNodes(compute).apply(Iterables.concat(runningNodesToSuspend, runningNodesToDestroy));

            for (NodeMetadata node : split.get(0))
                assertEquals(
                        NodeMetadata.Status.SUSPENDED,
                        compute.getNodeMetadata(node.getId()).getStatus());
            for (NodeMetadata node : split.get(1)) assertNull(compute.getNodeMetadata(node.getId()));
        }
    }

    private static TerminateNodes newTerminateNodes(ComputeService compute) {
        return new TerminateNodes();
    }
}
