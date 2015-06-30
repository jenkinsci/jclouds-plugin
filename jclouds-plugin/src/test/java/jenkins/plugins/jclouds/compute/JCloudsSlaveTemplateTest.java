package jenkins.plugins.jclouds.compute;

import java.util.ArrayList;
import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;

import static jenkins.plugins.jclouds.compute.CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlaveTemplateTest extends HudsonTestCase {

    public void testConfigRoundtrip() throws Exception {
        String name = "testSlave";
        JCloudsSlaveTemplate originalTemplate = new JCloudsSlaveTemplate(name, "imageId", null, "hardwareId", 1, 512, "osFamily", "osVersion", "locationId",
                "jclouds-slave-type1 jclouds-type2", "Description", "initScript", null, "1", false, null, null, true,
                "jvmOptions", false, null, false,
                false, 5, 0, true, false, 0, "jenkins", true, "network1_id,network2_id", "security_group1,security_group2", null);

        List<JCloudsSlaveTemplate> templates = new ArrayList<JCloudsSlaveTemplate>();
        templates.add(originalTemplate);

        JCloudsCloud originalCloud = new JCloudsCloud("aws-profile", "aws-ec2", "identity", "credential", "privateKey", "publicKey", "endPointUrl", 1, DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                600 * 1000, 600 * 1000, null, templates);

        hudson.clouds.add(originalCloud);
        submit(createWebClient().goTo("configure").getFormByName("config"));

        assertEqualBeans(originalCloud, JCloudsCloud.getByName("aws-profile"), "profile,providerName,identity,credential,privateKey,publicKey,endPointUrl");

        assertEqualBeans(originalTemplate, JCloudsCloud.getByName("aws-profile").getTemplate(name),
                "name,cores,ram,osFamily,osVersion,labelString,description,initScript,numExecutors,stopOnTerminate");

    }

}
