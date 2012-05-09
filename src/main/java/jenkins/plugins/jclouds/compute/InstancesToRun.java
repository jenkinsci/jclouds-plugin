package jenkins.plugins.jclouds.compute;

import java.io.Serializable;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

public final class InstancesToRun extends AbstractDescribableImpl<InstancesToRun> {
    public final String cloudName;
    public final String templateName;
    public final int count;
    public final boolean suspendOrTerminate;
    
    @DataBoundConstructor
    public InstancesToRun(String cloudName, String templateName, int count, boolean suspendOrTerminate) {
        this.cloudName = Util.fixEmptyAndTrim(cloudName);
        this.templateName = Util.fixEmptyAndTrim(templateName);
        this.count = count;
        this.suspendOrTerminate = suspendOrTerminate;
    }        

    @Extension
    public static class DescriptorImpl extends Descriptor<InstancesToRun> {
        public ListBoxModel doFillCloudNameItems() {
            ListBoxModel m = new ListBoxModel();
            for (String cloudName : JCloudsCloud.getCloudNames()) {
                m.add(cloudName, cloudName);
            }

            return m;
        }

        public ListBoxModel doFillTemplateNameItems(@QueryParameter String cloudName) {
            ListBoxModel m = new ListBoxModel();
            JCloudsCloud c = JCloudsCloud.getByName(cloudName);
            if (c != null) {
                for (JCloudsSlaveTemplate t : c.getTemplates()) {
                    m.add(String.format("%s in cloud %s", t.name, cloudName), t.name);
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