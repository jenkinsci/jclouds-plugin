package hudson.plugins.jclouds;

import hudson.Extension;
import hudson.model.Label;
import hudson.slaves.NodeProvisioner;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;
import java.util.List;

public class CloudServersCloud extends JCloudsCloud {

  @DataBoundConstructor
  public CloudServersCloud(String identity, String credential, String privateKey,
                           String instanceCapStr, List<JCloudTemplate> templates) {
    // cloudserver-us here needs to come from jelly
    super("cloudservers-us", identity, credential, privateKey, instanceCapStr, templates);
  }

  @Extension
  public static class DescriptorImpl extends JCloudsCloud.DescriptorImpl {

    @Override
    public String getDisplayName() {
      return "JClouds - Rackspace CloudServers";
    }
  }
}
