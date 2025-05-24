package jenkins.plugins.jclouds.compute;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlDialog;
import org.htmlunit.html.HtmlDivision;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Vijay Kiran
 */
@WithJenkins
class JCloudsSlaveTemplateTest {

    private static final String SSH_PROV_DEPRECATED = "Using SSH-based provisioning is deprecated";

    @Test
    void testCreate(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "foo");
        String ecdsaId = TestHelper.createEcdsaCredential();

        HtmlPage p = j.createWebClient().goTo("manage/cloud/foo/new");
        // Cloud has no templates, therfore the copy option should not exist
        HtmlDivision div = p.querySelector("div.optionalBlock-container");
        assertThat(div, nullValue());

        // There should be a FormValidation error, stating that the name is required
        assertThat(TestHelper.getFormError(p), equalTo("Required"));

        HtmlForm f = p.getFormByName("config");
        HtmlInput inp = f.getInputByName("cloudName");
        assertThat(inp.getValue(), equalTo("foo"));

        inp = f.getInputByName("name");
        inp.setValue("whatever");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        String purl = p.getUrl().toString();
        p = (HtmlPage) HtmlFormUtil.submit(f);
        String p2url = p.getUrl().toString();
        assertThat(purl.replace("new", "create"), equalTo(p2url));

        f = p.getFormByName("config");
        inp = f.getInputByName("_.name");
        assertThat(inp.getValue(), equalTo("whatever"));
        inp.setValue("what_ever");
        TestHelper.triggerValidation(inp);
        assertThat(
                TestHelper.getFormError(p), containsString("Object 'what_ever' doesn't match dns naming constraints."));
        inp.setValue("whatever");
        TestHelper.triggerValidation(inp);

        // At this moment, credentialsId has still an "Required" error
        assertThat(TestHelper.getFormError(p), equalTo("Required"));

        HtmlSelect sel = p.getElementByName("_.credentialsId");
        TestHelper.triggerValidation(sel);
        sel.setSelectedAttribute(ecdsaId, true);
        TestHelper.triggerValidation(sel);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = f.getInputByName("_.cores");
        inp.setValue("-1");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Value must not be negative"));
        inp.setValue("abc");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Not a number"));
        inp.setValue("1.0");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = f.getInputByName("_.ram");
        inp.setValue("-1");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Number may not be negative"));
        inp.setValue("aaa");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Not a number"));
        inp.setValue("1024");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = f.getInputByName("_.numExecutors");
        inp.setValue("-1");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Number may not be negative"));
        inp.setValue("2");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = f.getInputByName("_.preferredAddress");
        inp.setValue("whatever");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Not a valid CIDR format!"));
        inp.setValue("192.168.2.0/24");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        assertThat(TestHelper.getFormWarning(p), containsString(SSH_PROV_DEPRECATED));
        inp = p.getElementByName("_.preExistingJenkinsUser");
        assertThat(inp.isCheckable(), equalTo(true));
        inp.setChecked(true);
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormWarning(p), equalTo(""));

        inp = p.getElementByName("_.installPrivateKey");
        assertThat(inp.isCheckable(), equalTo(true));
        inp.setChecked(true);
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormWarning(p), containsString(SSH_PROV_DEPRECATED));
        inp.setChecked(false);
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormWarning(p), equalTo(""));

        sel = p.getElementByName("_.adminCredentialsId");
        sel.setSelectedAttribute(ecdsaId, true);
        TestHelper.triggerValidation(sel);
        assertThat(TestHelper.getFormError(p), equalTo("Not an RSA SSH key credential"));
        sel.setSelectedAttribute("test-rsa-key", true);
        TestHelper.triggerValidation(sel);
        assertThat(TestHelper.getFormWarning(p), containsString(SSH_PROV_DEPRECATED));
        sel.setSelectedAttribute("", true);
        TestHelper.triggerValidation(sel);
        assertThat(TestHelper.getFormError(p), equalTo(""));
        assertThat(TestHelper.getFormWarning(p), equalTo(""));

        inp = f.getInputByName("_.fsRoot");
        inp.setValue("");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));
        assertThat(TestHelper.getFormWarning(p), equalTo(""));
        HtmlInput inp2 = p.getElementByName("_.preExistingJenkinsUser");
        inp2.setChecked(false);
        TestHelper.triggerValidation(inp2);
        assertThat(TestHelper.getFormError(p), equalTo("Required"));
        assertThat(TestHelper.getFormWarning(p), containsString(SSH_PROV_DEPRECATED));
        inp.setValue("/jenkins");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));
        assertThat(TestHelper.getFormWarning(p), containsString(SSH_PROV_DEPRECATED));
        inp2.setChecked(true);
        TestHelper.triggerValidation(inp2);
        assertThat(TestHelper.getFormError(p), equalTo(""));
        assertThat(TestHelper.getFormWarning(p), equalTo(""));

        String oldUrl = p.getUrl().toString();
        p = (HtmlPage) HtmlFormUtil.submit(f);
        assertThat(p.getUrl().toString(), equalTo(oldUrl.replace("create", "templates")));
        List<JCloudsSlaveTemplate> tpls = JCloudsCloud.getByName("foo").getTemplates();
        assertThat(tpls, hasSize(1));
        assertThat(tpls.get(0).name, equalTo("whatever"));
    }

    @Test
    void testConfigRoundtrip(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        final JCloudsCloud beforeCloud = JCloudsCloud.getByName("foo");
        final JCloudsSlaveTemplate beforeTemplate = beforeCloud.getTemplate("FooTemplate");

        j.submit(j.createWebClient()
                .goTo("manage/cloud/foo/template/FooTemplate")
                .getFormByName("config"));

        final JCloudsCloud afterCloud = JCloudsCloud.getByName("foo");
        final JCloudsSlaveTemplate afterTemplate = afterCloud.getTemplate("FooTemplate");

        j.assertEqualBeans(beforeCloud, afterCloud, "profile,providerName,endPointUrl,trustAll,groupPrefix");
        j.assertEqualBeans(
                beforeTemplate,
                afterTemplate,
                "name,cores,ram,osFamily,osVersion,labelString,description,numExecutors,stopOnTerminate,mode,useConfigDrive,isPreemptible,preferredAddress,useJnlp");
    }

    private void deleteTemplate(HtmlPage p, JCloudsCloud cloud) {
        List<HtmlAnchor> delButton = p.getByXPath("//a[contains(@class, 'jenkins-!-destructive-color')]");
        assertThat(delButton, hasSize(1));

        delButton.get(0).fireEvent("click");
        HtmlDialog dlg = p.querySelector("dialog.jenkins-dialog");
        assertThat(dlg, notNullValue());
        HtmlButton cancel = dlg.querySelector("button.jenkins-button[data-id='cancel']");
        assertThat(cancel, notNullValue());
        cancel.fireEvent("click");
        dlg = p.querySelector("dialog.jenkins-dialog");
        assertThat(dlg, nullValue());
        // After cancel, template should still exist
        assertThat(cloud.getTemplates(), hasSize(1));

        delButton.get(0).fireEvent("click");
        dlg = p.querySelector("dialog.jenkins-dialog");
        assertThat(dlg, notNullValue());
        HtmlButton ok = dlg.querySelector("button.jenkins-button[data-id='ok']");
        assertThat(ok, notNullValue());
        ok.fireEvent("click");
        dlg = p.querySelector("dialog.jenkins-dialog");
        assertThat(dlg, nullValue());
        // After ok, template should be gone
        assertThat(cloud.getTemplates(), hasSize(0));
    }

    @Test
    void testDeleteTemplateFromTemplatesPage(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        final JCloudsCloud cloud = JCloudsCloud.getByName("foo");
        assertThat(cloud.getTemplates(), hasSize(1));

        HtmlPage p = j.createWebClient().goTo("manage/cloud/foo/templates");
        deleteTemplate(p, cloud);
    }

    @Test
    void testDeleteTemplateFromConfigPage(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        final JCloudsCloud cloud = JCloudsCloud.getByName("foo");
        assertThat(cloud.getTemplates(), hasSize(1));

        HtmlPage p = j.createWebClient().goTo("manage/cloud/foo/template/FooTemplate");
        deleteTemplate(p, cloud);
    }

    @Test
    void testDirect(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");
        final JCloudsCloud c = JCloudsCloud.getByName("foo");
        final JCloudsSlaveTemplate t = c.getTemplate("FooTemplate");
        assertThat(t.getUrl(), equalTo("template/" + t.name + "/"));
    }
}
