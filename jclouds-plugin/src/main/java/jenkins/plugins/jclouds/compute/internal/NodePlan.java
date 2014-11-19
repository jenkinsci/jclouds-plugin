package jenkins.plugins.jclouds.compute.internal;

import org.jclouds.compute.domain.NodeMetadata;

import shaded.com.google.common.base.Supplier;

public class NodePlan {
    private final String cloudName;
    private final String templateName;
    private final int count;
    private final boolean suspendOrTerminate;
    private final Supplier<NodeMetadata> nodeSupplier;

    public NodePlan(String cloud, String template, int count, boolean suspendOrTerminate, Supplier<NodeMetadata> nodeSupplier) {
        this.cloudName = cloud;
        this.templateName = template;
        this.count = count;
        this.suspendOrTerminate = suspendOrTerminate;
        this.nodeSupplier = nodeSupplier;
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getTemplateName() {
        return templateName;
    }

    public int getCount() {
        return count;
    }

    public boolean isSuspendOrTerminate() {
        return suspendOrTerminate;
    }

    public Supplier<NodeMetadata> getNodeSupplier() {
        return nodeSupplier;
    }
}
