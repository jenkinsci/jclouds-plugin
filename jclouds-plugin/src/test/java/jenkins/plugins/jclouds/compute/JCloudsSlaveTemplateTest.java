package jenkins.plugins.jclouds.compute;

import java.util.ArrayList;
import java.util.List;

import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Test;
import org.junit.Rule;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlaveTemplateTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void testConfigRoundtrip() throws Exception {
        final String name = "testSlave";
        final JCloudsSlaveTemplate beforeTemplate = new JCloudsSlaveTemplate(name, "imageId", null, "hardwareId",
                1, 512, "osFamily", "osVersion", "locationId", "jclouds-slave-type1 jclouds-type2",
                "Description", "initScript", null /* userData */ , 1 /* numExecutors */, false /* stopOnTerminate */,
                "jvmOptions", false /* preExistingJenkinsUser */, null /* fsRoot */, false /* allowSudo */,
                false /* installPrivateKey */, 5 /* overrideRetentionTime */, 0 /* spoolDelayMs */,
                true /* assignFloatingIp */, false /* waitPhoneHome */, 0 /* waitPhoneHomeTimeout */,
                null /* keyPairName */, true /* assignPublicIp */, "network1_id,network2_id",
                "security_group1,security_group2", null /* credentialsId */,
                null /* adminCredentialsId */, "NORMAL" /* mode */);

        final List<JCloudsSlaveTemplate> templates = new ArrayList<>();
        templates.add(beforeTemplate);

        final JCloudsCloud beforeCloud = new JCloudsCloud("aws-profile",
                "aws-ec2", "cloudCredentialsId", "cloudGlobalKeyId",
                "http://localhost", 1, 30, 600 * 1000, 600 * 1000, null, templates);

        j.jenkins.clouds.add(beforeCloud);
        j.submit(j.createWebClient().goTo("configure").getFormByName("config"));

        final JCloudsCloud afterCloud = JCloudsCloud.getByName("aws-profile");
        final JCloudsSlaveTemplate afterTemplate = afterCloud.getTemplate(name);

        j.assertEqualBeans(beforeCloud, afterCloud,
                "profile,providerName,endPointUrl");
        j.assertEqualBeans(beforeTemplate, afterTemplate,
                "name,cores,ram,osFamily,osVersion,labelString,description,initScript,numExecutors,stopOnTerminate,mode");
    }

}
