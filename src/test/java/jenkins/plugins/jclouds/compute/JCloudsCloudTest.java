package jenkins.plugins.jclouds.compute;

import java.util.Collections;

import org.jvnet.hudson.test.HudsonTestCase;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * @author Vijay Kiran
 */
public class JCloudsCloudTest extends HudsonTestCase {

	public void testConfigurationUI() throws Exception {
		HtmlPage page = new WebClient().goTo("configure");
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

		HtmlForm configForm2 = page2.getFormByName("config");
		assertNotNull("\"Private Key\" area should be present.", configForm2.getTextAreaByName("_.privateKey"));
		assertNotNull("\"Public Key\" should be present.", configForm2.getTextAreaByName("_.publicKey"));
		HtmlButton generateKeyPairButton = configForm2.getButtonByCaption("Generate Key Pair");
		HtmlButton testConnectionButton = configForm2.getButtonByCaption("Test Connection");
		HtmlButton deleteCloudButton = configForm2.getButtonByCaption("Delete cloud");
		assertNotNull("\"Generate Keypair\" button should be present.", generateKeyPairButton);
		assertNotNull("\"Test Connection\" button should be present.", testConnectionButton);
		assertNotNull("\"Delete Cloud\" button should be present.", deleteCloudButton);

	}

	public void testConfigRoundtrip() throws Exception {

		JCloudsCloud original = new JCloudsCloud("aws-profile", "aws-ec2", "identity", "credential", "privateKey", "publicKey", "endPointUrl", 1, 30,
				600 * 1000, 600 * 1000, null, Collections.<JCloudsSlaveTemplate> emptyList());

		hudson.clouds.add(original);
		submit(createWebClient().goTo("configure").getFormByName("config"));

		assertEqualBeans(original, hudson.clouds.getByName("aws-profile"),
				"profile,providerName,identity,credential,privateKey,publicKey,endPointUrl,instanceCap,retentionTime");

		assertEqualBeans(original, JCloudsCloud.getByName("aws-profile"),
				"profile,providerName,identity,credential,privateKey,publicKey,endPointUrl,instanceCap,retentionTime");
	}

}
