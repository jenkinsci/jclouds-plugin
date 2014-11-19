package jenkins.plugins.jclouds.compute.internal;

import org.jclouds.compute.domain.NodeMetadata;

public class RunningNode {
    private final String cloud;
    private final String template;
    private final boolean suspendOrTerminate;
    private final NodeMetadata node;

    public RunningNode(String cloud, String template, boolean suspendOrTerminate, NodeMetadata node) {
        this.cloud = cloud;
        this.template = template;
        this.suspendOrTerminate = suspendOrTerminate;
        this.node = node;
    }

    public String getCloudName() {
        return cloud;
    }

    public String getTemplateName() {
        return template;
    }

    public boolean isSuspendOrTerminate() {
        return suspendOrTerminate;
    }

    public NodeMetadata getNode() {
        return node;
    }
}
