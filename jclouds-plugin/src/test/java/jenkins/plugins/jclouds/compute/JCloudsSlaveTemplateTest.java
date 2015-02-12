package jenkins.plugins.jclouds.compute;

import java.util.ArrayList;
import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlaveTemplateTest extends HudsonTestCase {

    public void testConfigRoundtrip() throws Exception {
        String name = "testSlave";
        JCloudsSlaveTemplate originalTemplate = new JCloudsSlaveTemplate(name, "imageId", null, "hardwareId", 1, 512, "osFamily", "osVersion", "locationId",
                "jclouds-slave-type1 jclouds-type2", "Description", "initScript", null /* userData */ , "1" /* numExecutors */, false /* stopOnTerminate */,
                "jvmOptions", false /* preExistingJenkinsUser */, null /* fsRoot */, false /* allowSudo */, false /* installPrivateKey */,
                5 /* overrideRetentionTime */, 0 /* spoolDelayMs */, true /* assignFloatingIp */, false /* waitPhoneHome */, 0 /* waitPhoneHomeTimeout */,
                null /* keyPairName */, true /* assignPublicIp */, "network1_id,network2_id", "security_group1,security_group2", null /* credentialsId */,
                null /* adminCredentialsId */);

        List<JCloudsSlaveTemplate> templates = new ArrayList<JCloudsSlaveTemplate>();
        templates.add(originalTemplate);

        JCloudsCloud originalCloud = new JCloudsCloud("aws-profile", "aws-ec2", "identity", "credential", "cloudGlobalKeyId", "endPointUrl", 1, 30,
                600 * 1000, 600 * 1000, null, templates);

        hudson.clouds.add(originalCloud);
        submit(createWebClient().goTo("configure").getFormByName("config"));

        assertEqualBeans(originalCloud, JCloudsCloud.getByName("aws-profile"), "profile,providerName,identity,credential,cloudGlobalKeyId,endPointUrl");

        assertEqualBeans(originalTemplate, JCloudsCloud.getByName("aws-profile").getTemplate(name),
                "name,cores,ram,osFamily,osVersion,labelString,description,initScript,numExecutors,stopOnTerminate");

    }

}
