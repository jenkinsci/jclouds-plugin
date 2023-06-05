package jenkins.plugins.jclouds.compute;

import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.List;

import org.htmlunit.WebAssert;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Vijay Kiran
 */
public class JCloudsCloudTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private void mySelectPresent(final HtmlPage p, final String name) {
        final String xpath = "//select[@name='" + name + "']";
        final List<?> list = p.getByXPath(xpath);
        if (list.isEmpty()) {
            throw new AssertionError("Unable to find an select element named '" + name + "'.");
        }
    }

    @Test
    public void testConfigurationUI() throws Exception {
        JCloudsCloud cloud = new JCloudsCloud("aws-profile", "aws-ec2", "",
                "", "http://localhost", 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                "foobar", true, Collections.<JCloudsSlaveTemplate>emptyList());
        j.getInstance().clouds.add(cloud);

        HtmlPage p = j.createWebClient().goTo("configureClouds");
        WebAssert.assertInputPresent(p, "_.profile");
        mySelectPresent(p, "_.providerName");
        WebAssert.assertInputPresent(p, "_.endPointUrl");
        WebAssert.assertInputPresent(p, "_.instanceCap");
        WebAssert.assertInputPresent(p, "_.retentionTime");
        mySelectPresent(p, "_.cloudCredentialsId");
        WebAssert.assertInputPresent(p, "_.trustAll");
        mySelectPresent(p, "_.cloudGlobalKeyId");
        WebAssert.assertInputPresent(p, "_.scriptTimeout");
        WebAssert.assertInputPresent(p, "_.startTimeout");
        WebAssert.assertInputPresent(p, "_.zones");
        WebAssert.assertInputPresent(p, "_.groupPrefix");
        HtmlForm f = p.getFormByName("config");
        HtmlButton b = HtmlFormUtil.getButtonByCaption(f, "Test Connection");
        assertNotNull(b);
        b = HtmlFormUtil.getButtonByCaption(f, "Delete cloud");
        assertNotNull(b);
    }

    @Test
    public void testConfigRoundtrip() throws Exception {

        JCloudsCloud original = new JCloudsCloud("aws-profile", "aws-ec2", "",
                "", "http://localhost", 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                 CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                 "foobar", true, Collections.<JCloudsSlaveTemplate>emptyList());

        j.getInstance().clouds.add(original);
        j.submit(j.createWebClient().goTo("configure").getFormByName("config"));

        j.assertEqualBeans(original, j.getInstance().clouds.getByName("aws-profile"),
                "profile,providerName,cloudCredentialsId,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime,groupPrefix");

        j.assertEqualBeans(original, JCloudsCloud.getByName("aws-profile"),
                "profile,providerName,cloudCredentialsId,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime,groupPrefix");
    }

}
