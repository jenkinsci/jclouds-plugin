package jenkins.plugins.jclouds;

import hudson.Extension;
import hudson.Plugin;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;

/**
 * @author Vijay Kiran
 */
@Extension
public class PluginImpl extends Plugin implements Describable<PluginImpl> {

    @Override
    public void start() throws Exception {
        load();
    }

    public Descriptor<PluginImpl> getDescriptor() {
        return (DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public static PluginImpl get() {
        return Hudson.getInstance().getPlugin(PluginImpl.class);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<PluginImpl> {
        @Override
        public String getDisplayName() {
            return "JClouds PluginImpl";
        }
    }
}
