package hudson.plugins.jclouds;

import hudson.Extension;

import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

public class EC2Cloud extends JCloudsCloud {

  @DataBoundConstructor
  public EC2Cloud(String identity, String credential, String privateKey,
                  String instanceCapStr, List<JCloudTemplate> templates) {
    super("ec2", identity, credential, privateKey, instanceCapStr, templates);
  }

  @Extension
  public static class DescriptorImpl extends JCloudsCloud.DescriptorImpl {

    @Override
    public String getDisplayName() {
      return "JClouds - EC2";
    }
  }
}
