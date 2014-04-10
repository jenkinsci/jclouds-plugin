package jenkins.plugins.jclouds.compute.internal;

import org.jclouds.compute.domain.NodeMetadata;

public class RunningNode {

  private final String cloud;
  private final String template;
  private final String actionOnBuildFinish;
  private final NodeMetadata node;
  
  public static final String ACTION_TERMINATE = "terminate";
  public static final String ACTION_SUSPEND = "suspend";
  public static final String ACTION_LEAVE = "leave";

  public RunningNode(final String cloud, final String template, final String actionOnBuildFinish,
                     final NodeMetadata node) {
    this.cloud = cloud;
    this.template = template;
    this.actionOnBuildFinish = actionOnBuildFinish;
    this.node = node;
  }

  public String getCloudName() {
    return cloud;
  }

  public String getTemplateName() {
    return template;
  }

  public boolean shouldTerminate() {
    return actionOnBuildFinish.equals(ACTION_TERMINATE);
  }
  
  public boolean shouldSuspend() {
    return actionOnBuildFinish.equals(ACTION_SUSPEND);
  }
  
  public boolean shouldLeave() {
    return actionOnBuildFinish.equals(ACTION_LEAVE);
  }

  public NodeMetadata getNode() {
    return node;
  }
}