package jenkins.plugins.jclouds.compute;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
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

    @Test
    public void testConfigurationUI() throws Exception {
        j.recipeLoadCurrentPlugin();
        j.configRoundtrip();
        HtmlPage page = j.createWebClient().goTo("configure");
        final String pageText = page.asText();
        assertTrue("Cloud Section must be present in the global configuration ", pageText.contains("Cloud"));

        final HtmlForm configForm = page.getFormByName("config");
        final HtmlButton buttonByCaption = configForm.getButtonByCaption("Add a new cloud");
        HtmlPage page1 = buttonByCaption.click();
        WebAssert.assertLinkPresentWithText(page1, "Cloud (JClouds)");

        HtmlPage page2 = page.getAnchorByText("Cloud (JClouds)").click();
        WebAssert.assertInputPresent(page2, "_.profile");
        WebAssert.assertInputPresent(page2, "_.endPointUrl");
        WebAssert.assertInputPresent(page2, "_.identity");
        WebAssert.assertInputPresent(page2, "_.credential");
        WebAssert.assertInputPresent(page2, "_.instanceCap");
        WebAssert.assertInputPresent(page2, "_.retentionTime");
        // WebAssert does not recognize select as input ?!
        //WebAssert.assertInputPresent(page2, "_.cloudGlobalKeyId");

        HtmlForm configForm2 = page2.getFormByName("config");
        HtmlButton testConnectionButton = configForm2.getButtonByCaption("Test Connection");
        HtmlButton deleteCloudButton = configForm2.getButtonByCaption("Delete cloud");
        assertNotNull(testConnectionButton);
        assertNotNull(deleteCloudButton);

    }

    @Test
    public void testConfigRoundtrip() throws Exception {

        JCloudsCloud original = new JCloudsCloud("aws-profile", "aws-ec2", "identity", "credential", "", "endPointUrl", 1, 30,
                600 * 1000, 600 * 1000, null, Collections.<JCloudsSlaveTemplate>emptyList());

        j.getInstance().clouds.add(original);
        j.submit(j.createWebClient().goTo("configure").getFormByName("config"));

        j.assertEqualBeans(original, j.getInstance().clouds.getByName("aws-profile"),
                "profile,providerName,identity,credential,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime");

        j.assertEqualBeans(original, JCloudsCloud.getByName("aws-profile"),
                "profile,providerName,identity,credential,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime");
    }

}
