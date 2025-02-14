package jenkins.plugins.jclouds.compute;

import org.htmlunit.WebAssert;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Vijay Kiran
 */
@WithJenkins
class JCloudsCloudTest {

    @Test
    void testConfigurationUI(JenkinsRule j) throws Exception {
        JCloudsCloud cloud = new JCloudsCloud("aws-profile", "aws-ec2", "",
                "", "http://localhost", 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                "foobar", true, Collections.emptyList());
        j.getInstance().clouds.add(cloud);

        //HtmlPage p = j.createWebClient().goTo("configureClouds");
        HtmlPage p = j.createWebClient().goTo("cloud/aws-profile/configure");
        WebAssert.assertInputPresent(p, "_.profile");
        mySelectPresent(p, "_.providerName");
        WebAssert.assertInputPresent(p, "_.endPointUrl");
        WebAssert.assertInputPresent(p, "_.instanceCap");
        WebAssert.assertInputPresent(p, "_.retentionTime");
        WebAssert.assertInputPresent(p, "_.errorRetentionTime");
        mySelectPresent(p, "_.cloudCredentialsId");
        WebAssert.assertInputPresent(p, "_.trustAll");
        mySelectPresent(p, "_.cloudGlobalKeyId");
        WebAssert.assertInputPresent(p, "_.scriptTimeout");
        WebAssert.assertInputPresent(p, "_.startTimeout");
        WebAssert.assertInputPresent(p, "_.zones");
        WebAssert.assertInputPresent(p, "_.groupPrefix");
        HtmlForm f = p.getFormByName("config");
        assertNotNull(f);
        HtmlButton b = HtmlFormUtil.getButtonByCaption(f, "Test Connection");
        assertNotNull(b);
        b = HtmlFormUtil.getButtonByCaption(f, "Add template");
        assertNotNull(b);
    }

    @Test
    void testConfigRoundtrip(JenkinsRule j) throws Exception {
        JCloudsCloud original = new JCloudsCloud("aws-profile", "aws-ec2", "",
                "", "http://localhost", 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                "foobar", true, Collections.emptyList());

        j.getInstance().clouds.add(original);
        j.submit(j.createWebClient().goTo("configure").getFormByName("config"));

        j.assertEqualBeans(original, j.getInstance().clouds.getByName("aws-profile"),
                "profile,providerName,cloudCredentialsId,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime,errorRetentionTime,groupPrefix");

        j.assertEqualBeans(original, JCloudsCloud.getByName("aws-profile"),
                "profile,providerName,cloudCredentialsId,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime,errorRetentionTime,groupPrefix");
    }

    private static void mySelectPresent(final HtmlPage p, final String name) {
        final String xpath = "//select[@name='" + name + "']";
        final List<?> list = p.getByXPath(xpath);
        assertFalse(list.isEmpty(), "Unable to find an select element named '" + name + "'.");
    }

}
