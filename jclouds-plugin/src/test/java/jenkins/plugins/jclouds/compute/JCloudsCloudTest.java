package jenkins.plugins.jclouds.compute;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Vijay Kiran
 */
public class JCloudsCloudTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private void savePage(final HtmlPage p, final String name) {
        try {
            final java.io.PrintStream ps = new java.io.PrintStream(new java.io.FileOutputStream(name));
            ps.println(p.asXml());
            ps.close();
        } catch (java.io.IOException e) {
        }
    }

    private void mySelectPresent(final HtmlPage p, final String name) {
        final String xpath = "//select[@name='" + name + "']";
        final List<?> list = p.getByXPath(xpath);
        if (list.isEmpty()) {
            throw new AssertionError("Unable to find an select element named '" + name + "'.");
        }
    }

    @Test
    public void testConfigurationUI() throws Exception {
        j.recipeLoadCurrentPlugin();
        j.configRoundtrip();

        HtmlPage p = j.createWebClient().goTo("configure");

        final String pageText = p.asText();
        assertTrue("Cloud Section must be present in the global configuration ", pageText.contains("Cloud"));

        // savePage(p, "page0.html");
        HtmlForm f = p.getFormByName("config");
        HtmlButton b = HtmlFormUtil.getButtonByCaption(f, "Add a new cloud");
        p = b.click();
        // savePage(p, "page1.html");
        WebAssert.assertLinkPresentWithText(p, "Cloud (JClouds)");
        /* new HtmlUnit somehow does not invoke the JClouds menu entry
         *
        savePage(p, "page2.html");
        HtmlAnchor a = p.getAnchorByText("Cloud (JClouds)");
        // p = a.click();
        p = (HtmlPage)a.mouseOver();
        a = p.getAnchorByText("Cloud (JClouds)");
        savePage(p, "page3.html");
        p = (HtmlPage)a.mouseDown();
        savePage(p, "page4.html");
        a = p.getAnchorByText("Cloud (JClouds)");
        p = (HtmlPage)a.mouseUp();
        savePage(p, "page5.html");

        WebAssert.assertInputPresent(p, "_.profile");
        WebAssert.assertInputPresent(p, "_.endPointUrl");
        // WebAssert does not recognize select as input ?!
        mySelectPresent(p, "_.cloudCredentialsId");
        WebAssert.assertInputPresent(p, "_.instanceCap");
        WebAssert.assertInputPresent(p, "_.retentionTime");
        // WebAssert does not recognize select as input ?!
        mySelectPresent(p, "_.cloudGlobalKeyId");
        WebAssert.assertInputPresent(p, "_.scriptTimeout");
        WebAssert.assertInputPresent(p, "_.startTimeout");
        WebAssert.assertInputPresent(p, "_.zones");
        WebAssert.assertInputPresent(p, "_.trustAll");

        f = p.getFormByName("config");
        b = HtmlFormUtil.getButtonByCaption(f, "Test Connection");
        assertNotNull(b);
        b = HtmlFormUtil.getButtonByCaption(f, "Delete cloud");
        assertNotNull(b);
        */
    }

    @Test
    public void testConfigRoundtrip() throws Exception {

        JCloudsCloud original = new JCloudsCloud("aws-profile", "aws-ec2", "",
                "", "http://localhost", 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                600 * 1000, 600 * 1000, null, true, Collections.<JCloudsSlaveTemplate>emptyList());

        j.getInstance().clouds.add(original);
        j.submit(j.createWebClient().goTo("configure").getFormByName("config"));

        j.assertEqualBeans(original, j.getInstance().clouds.getByName("aws-profile"),
                "profile,providerName,cloudCredentialsId,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime");

        j.assertEqualBeans(original, JCloudsCloud.getByName("aws-profile"),
                "profile,providerName,cloudCredentialsId,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime");
    }

}
