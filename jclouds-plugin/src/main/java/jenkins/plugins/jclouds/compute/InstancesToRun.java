package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public final class InstancesToRun extends AbstractDescribableImpl<InstancesToRun> {

    public final String cloudName;
    public final String templateName;
    public final String manualTemplateName;
    public final int count;
    public final boolean suspendOrTerminate;

    @DataBoundConstructor
    public InstancesToRun(String cloudName, String templateName, String manualTemplateName, int count, boolean suspendOrTerminate) {
        this.cloudName = Util.fixEmptyAndTrim(cloudName);
        this.templateName = Util.fixEmptyAndTrim(templateName);
        this.manualTemplateName = Util.fixEmptyAndTrim(manualTemplateName);
        this.count = count;
        this.suspendOrTerminate = suspendOrTerminate;
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

    @Extension
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

        public ListBoxModel doFillCloudNameItems() {
            ListBoxModel m = new ListBoxModel();
            for (String name : JCloudsCloud.getCloudNames()) {
                JCloudsCloud c = JCloudsCloud.getByName(name);
                if (c != null && c.getTemplates().size() > 0) {
                    m.add(name, name);
                }
            }
            return m;
        }

        public ListBoxModel doFillTemplateNameItems(@QueryParameter("cloudName") String cname) {
            ListBoxModel m = new ListBoxModel();
            JCloudsCloud c = JCloudsCloud.getByName(cname);
            if (c != null) {
                for (JCloudsSlaveTemplate t : c.getTemplates()) {
                    m.add(String.format("%s in cloud %s", t.name, cname), t.name);
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
