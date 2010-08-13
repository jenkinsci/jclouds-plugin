package hudson.plugins.jclouds;

import hudson.Extension;

import java.util.List;

public class RackspaceCloud extends JCloudsCloud {
	
	
    public RackspaceCloud(String user, String secret,
			String instanceCapStr, List<JCloudTemplate> templates) {
		super("rackspace", user, secret, instanceCapStr, templates);
    }

    @Extension
    public static class DescriptorImpl extends JCloudsCloud.DescriptorImpl {

    	@Override
    	public String getDisplayName() {
    		return "Rackspace";
    	}
    }

}
