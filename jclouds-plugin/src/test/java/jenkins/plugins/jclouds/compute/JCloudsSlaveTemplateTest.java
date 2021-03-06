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
                "Description", "initScriptId", 1 /* numExecutors */, false /* stopOnTerminate */,
                "jvmOptions", false /* preExistingJenkinsUser */, null /* fsRoot */, false /* allowSudo */,
                false /* installPrivateKey */, 5 /* overrideRetentionTime */, true /* hasOverrideRetentionTime */,
                0 /* spoolDelayMs */, true /* assignFloatingIp */, false /* waitPhoneHome */, 0 /* waitPhoneHomeTimeout */,
                null /* keyPairName */, true /* assignPublicIp */, "network1_id,network2_id",
                "security_group1,security_group2", null /* credentialsId */,
                null /* adminCredentialsId */, "NORMAL" /* mode */, true /* useConfigDrive */,
                false /* preemptible */, null /* configDataIds */, "192.168.1.0/24" /* preferredAddress */,
                false /* useJnlp */, false /* jnlpProvisioning */ );

        final List<JCloudsSlaveTemplate> templates = new ArrayList<>();
        templates.add(beforeTemplate);

        final JCloudsCloud beforeCloud = new JCloudsCloud("aws-profile",
                "aws-ec2", "cloudCredentialsId", "cloudGlobalKeyId",
                "http://localhost", 1, 30, 5, 600 * 1000, 600 * 1000, null, "foobar", true, templates);

        j.jenkins.clouds.add(beforeCloud);
        j.submit(j.createWebClient().goTo("configure").getFormByName("config"));

        final JCloudsCloud afterCloud = JCloudsCloud.getByName("aws-profile");
        final JCloudsSlaveTemplate afterTemplate = afterCloud.getTemplate(name);

        j.assertEqualBeans(beforeCloud, afterCloud,
                "profile,providerName,endPointUrl,trustAll,groupPrefix");
        j.assertEqualBeans(beforeTemplate, afterTemplate,
                "name,cores,ram,osFamily,osVersion,labelString,description,numExecutors,stopOnTerminate,mode,useConfigDrive,isPreemptible,preferredAddress,useJnlp");
    }

}
