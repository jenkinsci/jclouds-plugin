package hudson.plugins.jclouds;

import hudson.Extension;

import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

public class StubCloud extends JCloudsCloud {
	
	
	@DataBoundConstructor
    public StubCloud(String identity, String credential,
			String instanceCapStr, List<JCloudTemplate> templates) {
		super("stub", identity, credential, instanceCapStr, templates);
    }

    @Extension
    public static class DescriptorImpl extends JCloudsCloud.DescriptorImpl {

    	@Override
    	public String getDisplayName() {
    		return "Stub for testing";
    	}
    }

}
