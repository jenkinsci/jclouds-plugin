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
package jenkins.plugins.jclouds.compute;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;

import hudson.console.ModelHyperlinkNote;

import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import hudson.slaves.OfflineCause;

import hudson.security.Permission;

import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

import org.jenkinsci.Symbol;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class JCloudsOfflineStep extends Recorder implements SimpleBuildStep {

    private final String message;
    private final String condition;

    @DataBoundConstructor
    public JCloudsOfflineStep(String message, String condition) {
        message = Util.fixEmptyAndTrim(message);
        if (message == null) {
            throw new IllegalArgumentException("A non-empty message is required");
        }
        this.message = message;
        condition = Util.fixEmptyAndTrim(condition);
        if (condition == null) {
            throw new IllegalArgumentException("A non-empty condition is required");
        }
        this.condition = condition;
    }

    public String getMessage() {
        return message;
    }

    public String getCondition() {
        return condition;
    }

    private Result getConditionInternal() {
        return Result.fromString(condition);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
  
    @Override
    public boolean requiresWorkspace() {
        return false;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull EnvVars env, @NonNull TaskListener tl) throws InterruptedException, IOException {
        Result result = run.getResult();
        if (null == result) {
            tl.getLogger().append("NOTICE: ").println("Build has not (yet) a result");
            return;
        }
        if (result.isBetterThan(getConditionInternal())) {
            tl.getLogger().println("Skipping action, build result is " + result.toString());
            return;
        }
        String nodename = env.get("NODE_NAME", "");
        String err = env.expand("Unable to set node ${NODE_NAME} offline: ");
        String causemsg = String.format("In build %s: %s", run.getExternalizableId(), env.expand(this.message));
        if (!nodename.isEmpty()) {
            Node node = Jenkins.get().getNode(nodename);
            if (null != node) {
                err = "Unable to set node " + ModelHyperlinkNote.encodeTo(node) + " offline: ";
                Computer c = node.toComputer();
                if (null != c) {
                    if (JCloudsComputer.class.isInstance(c)) {
                        tl.getLogger().append("NOTICE: ").println("Setting node " + ModelHyperlinkNote.encodeTo(node) + " offline");
                        OfflineCause oc = new OfflineCause.UserCause(null, causemsg);
                        c.setTemporaryOfflineCause(oc);
                        return;
                    }
                    tl.getLogger().append("WARNING: ").println(err + "Not a jclouds instance");
                    return;
                }
                tl.getLogger().append("WARNING: ").println(err + "Node has no associated Computer");
                return;
            }
            tl.getLogger().append("WARNING: ").println(err + "Node not found");
            return;
        }
        tl.getLogger().append("WARNING: ").println("Unable to set empty node offline");
    }

    @Extension @Symbol("jcloudsTakeOffline")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @NonNull @Override
        public String getDisplayName() {
            return "Take current JClouds agent offline conditionally";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // Show only, if at least one cloud with a SlaveTemplate exists
            for (String name : JCloudsCloud.getCloudNames()) {
                JCloudsCloud c = JCloudsCloud.getByName(name);
                if (c != null && c.getTemplates().size() > 0) {
                    return true;
                }
            }
            return false;
        }

        @RequirePOST
        public FormValidation doCheckMessage(@AncestorInPath AbstractProject project, @QueryParameter String message) {
            if (null == project) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                project.checkPermission(Permission.CONFIGURE);
            }
            Jenkins.get().checkPermission(Computer.DISCONNECT);
            if (Util.fixEmptyAndTrim(message) == null) {
                return FormValidation.error("Message must be non-empty");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public ListBoxModel doFillConditionItems(@AncestorInPath AbstractProject project) {
            if (null == project) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                project.checkPermission(Permission.CONFIGURE);
            }
            Jenkins.get().checkPermission(Computer.DISCONNECT);
            ListBoxModel items = new ListBoxModel();
            items.add(Result.UNSTABLE.toString(), Result.UNSTABLE.toString());
            items.add(Result.FAILURE.toString(), Result.FAILURE.toString());
            items.add(Result.ABORTED.toString(), Result.ABORTED.toString());
            return items;
        }
    }
}
