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

import java.io.Serializable;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public final class InstancesToRun extends AbstractDescribableImpl<InstancesToRun> implements Serializable {

    private static final long serialVersionUID = -1;

    public final String cloudName;
    public final String templateName;
    public final String manualTemplateName;
    public final int count;
    public final boolean shouldSuspend;

    @DataBoundConstructor
    public InstancesToRun(String cloudName, String templateName, String manualTemplateName, int count, boolean shouldSuspend) {
        this.cloudName = Util.fixEmptyAndTrim(cloudName);
        this.templateName = Util.fixEmptyAndTrim(templateName);
        this.manualTemplateName = Util.fixEmptyAndTrim(manualTemplateName);
        this.count = count;
        this.shouldSuspend = shouldSuspend;
    }

    public String getActualTemplateName() {
        if (isUsingManualTemplateName()) {
            return manualTemplateName;
        } else {
            return templateName;
        }
    }

    public boolean isUsingManualTemplateName() {
        if (manualTemplateName == null || manualTemplateName.equals("")) {
            return false;
        } else {
            return true;
        }
    }

    @Extension @Symbol("instances")
    public static class DescriptorImpl extends Descriptor<InstancesToRun> {
        public String defaultCloudName() {
            for (String name : JCloudsCloud.getCloudNames()) {
                JCloudsCloud c = JCloudsCloud.getByName(name);
                if (c != null && c.getTemplates().size() > 0) {
                    return name;
                }
            }
            return "";
        }

        @RequirePOST
        public ListBoxModel doFillCloudNameItems(@AncestorInPath AbstractProject project) {
            if (null == project) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                
                project.checkPermission(Permission.CONFIGURE);
            }
            Jenkins.get().checkPermission(Cloud.PROVISION);
            ListBoxModel m = new ListBoxModel();
            for (String name : JCloudsCloud.getCloudNames()) {
                JCloudsCloud c = JCloudsCloud.getByName(name);
                if (c != null && c.getTemplates().size() > 0) {
                    m.add(name, name);
                }
            }
            return m;
        }

        @RequirePOST
        public ListBoxModel doFillTemplateNameItems(@AncestorInPath AbstractProject project, @QueryParameter("cloudName") String cname) {
            if (null == project) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                project.checkPermission(Permission.CONFIGURE);
            }
            Jenkins.get().checkPermission(Cloud.PROVISION);
            ListBoxModel m = new ListBoxModel();
            JCloudsCloud c = JCloudsCloud.getByName(cname);
            if (c != null) {
                for (JCloudsSlaveTemplate t : c.getTemplates()) {
                    m.add(t.name, t.name);
                }
            }
            return m;
        }

        public FormValidation doCheckCount(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
