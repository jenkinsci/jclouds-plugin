package hudson.plugins.jclouds;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

public class CloudServersUSCloud extends JCloudsCloud {

  @DataBoundConstructor
  public CloudServersUSCloud(String identity, String credential, String privateKey,
                           String instanceCapStr, List<JCloudTemplate> templates) {
    super("cloudservers-us", identity, credential, privateKey, instanceCapStr, templates);
  }

  @Extension
  public static class DescriptorImpl extends JCloudsCloud.DescriptorImpl {

    @Override
    public String getDisplayName() {
      return "JClouds - Rackspace CloudServers US";
    }
  }
}
