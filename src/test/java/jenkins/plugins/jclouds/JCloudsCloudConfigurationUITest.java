package jenkins.plugins.jclouds;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * @author Vijay Kiran
 */
public class JCloudsCloudConfigurationUITest extends HudsonTestCase {

   public void testConfigurationUI() throws IOException, SAXException {
      HtmlPage page = new WebClient().goTo("configure");
      final String pageText = page.asText();
      assertTrue("Cloud Section must be present in the global configuration ", pageText.contains("Cloud"));

      final HtmlForm configForm = page.getFormByName("config");
      final HtmlButton buttonByCaption = configForm.getButtonByCaption("Add a new cloud");
      HtmlPage page1 = buttonByCaption.click();
      WebAssert.assertLinkPresentWithText(page1, "Cloud (JClouds)");

      HtmlPage page2 = page.getAnchorByText("Cloud (JClouds)").click();
      WebAssert.assertInputPresent(page2, "_.profile");
      WebAssert.assertInputPresent(page2, "_.providerName");
      WebAssert.assertInputPresent(page2, "_.endPointUrl");
      WebAssert.assertInputPresent(page2, "_.identity");
      WebAssert.assertInputPresent(page2, "_.credential");
      WebAssert.assertInputPresent(page2, "_.osFamily");
      WebAssert.assertInputContainsValue(page2, "_.ram", "512");
      WebAssert.assertInputContainsValue(page2, "_.cores", "1");


      HtmlForm configForm2 = page2.getFormByName("config");
      assertNotNull(configForm2.getTextAreaByName("_.privateKey"));
      assertNotNull(configForm2.getTextAreaByName("_.publicKey"));
      HtmlButton generateKeyPairButton = configForm2.getButtonByCaption("Generate Key Pair");
      HtmlButton testConnectionButton = configForm2.getButtonByCaption("Test Connection");
      HtmlButton deleteCloudButton = configForm2.getButtonByCaption("Delete cloud");
      assertNotNull(generateKeyPairButton);
      assertNotNull(testConnectionButton);
      assertNotNull(deleteCloudButton);

   }
}
