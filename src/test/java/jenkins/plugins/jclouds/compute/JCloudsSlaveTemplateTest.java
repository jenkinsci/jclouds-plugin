package jenkins.plugins.jclouds.compute;

import org.jvnet.hudson.test.HudsonTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlaveTemplateTest extends HudsonTestCase {

   public void testConfigRoundtrip() throws Exception {
      String name = "testSlave";
      JCloudsSlaveTemplate originalTemplate = new JCloudsSlaveTemplate(name, "imageId", "hardwareId", 1, 512, "osFamily",
                                                                       "osVersion", "jclouds-slave-type1 jclouds-type2", "Description",
                                                                       "initScript", "1", false);

      List<JCloudsSlaveTemplate> templates = new ArrayList<JCloudsSlaveTemplate>();
      templates.add(originalTemplate);

      JCloudsCloud originalCloud = new JCloudsCloud("aws-profile", "aws-ec2", "identity", "credential", "privateKey", "publicKey",
            "endPointUrl", 1, templates);

      hudson.clouds.add(originalCloud);
      submit(createWebClient().goTo("configure").getFormByName("config"));

      assertEqualBeans(originalCloud,
                       JCloudsCloud.getByName("aws-profile"),
                       "profile,providerName,identity,credential,privateKey,publicKey,endPointUrl");

      assertEqualBeans(originalTemplate,
                       JCloudsCloud.getByName("aws-profile").getTemplate(name),
                       "name,cores,ram,osFamily,osVersion,labelString,description,initScript,numExecutors,stopOnTerminate");

   }

}
