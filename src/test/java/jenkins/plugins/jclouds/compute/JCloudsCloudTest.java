package jenkins.plugins.jclouds.compute;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.WebAssert;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlDialog;
import org.htmlunit.html.HtmlDivision;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlParagraph;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlTableBody;
import org.htmlunit.html.HtmlTable;
import org.htmlunit.html.HtmlTableCell;
import org.htmlunit.html.HtmlTableRow;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jenkins.model.Jenkins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author Vijay Kiran
 */
@WithJenkins
class JCloudsCloudTest {

    private static final String ADMIN = "admin";
    private static final String READER = "reader";

    // Why does this not work with @BeforeEach?
    public void setUp(JenkinsRule j) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to(ADMIN).
            grant(Jenkins.READ).everywhere().to(READER));
    }

    @Test
    void testCloudEntryPagePermission(JenkinsRule j) throws Exception {
        setUp(j);
        FailingHttpStatusCodeException thrown =
                assertThrows(FailingHttpStatusCodeException.class, () ->
                    j.createWebClient().login(READER). goTo("manage/cloud/aws-profile/"));
        assertThat(thrown.getMessage(), containsString("403 Forbidden"));
    }

    @Test
    void testCloudNew2TemplatePagePermission(JenkinsRule j) throws Exception {
        setUp(j);
        FailingHttpStatusCodeException thrown =
                assertThrows(FailingHttpStatusCodeException.class, () ->
                    j.createWebClient().login(READER). goTo("manage/cloud/aws-profile/_new"));
        assertThat(thrown.getMessage(), containsString("403 Forbidden"));
    }

    @Test
    void testCloudNewTemplatePagePermission(JenkinsRule j) throws Exception {
        setUp(j);
        FailingHttpStatusCodeException thrown =
                assertThrows(FailingHttpStatusCodeException.class, () ->
                    j.createWebClient().login(READER). goTo("manage/cloud/aws-profile/new"));
        assertThat(thrown.getMessage(), containsString("403 Forbidden"));
    }

    @Test
    void testTemplatesIndexPermission(JenkinsRule j) throws Exception {
        setUp(j);
        FailingHttpStatusCodeException thrown =
                assertThrows(FailingHttpStatusCodeException.class, () ->
                    j.createWebClient().login(READER). goTo("manage/cloud/aws-profile/templates"));
        assertThat(thrown.getMessage(), containsString("403 Forbidden"));
    }

    @Test
    void testCloudEntryPage(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "aws-profile");
        HtmlPage p = j.createWebClient().goTo("manage/cloud/aws-profile/");
        List<HtmlAnchor> tasks = p.getByXPath("//a[contains(@class, 'task-link')]");
        assertThat(tasks, hasSize(5));
        assertThat(tasks.get(0).getHrefAttribute(), equalTo("."));
        assertThat(tasks.get(1).getHrefAttribute(), equalTo("templates"));
        assertThat(tasks.get(2).getHrefAttribute(), equalTo("configure"));
        assertThat(tasks.get(3).getHrefAttribute(), equalTo("#")); // Delete
        assertThat(tasks.get(4).getHrefAttribute(), equalTo("/jenkins/cloud-stats/"));
    }

    @Test
    void testCloudDelete(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "aws-profile");
        HtmlPage p = j.createWebClient().goTo("manage/cloud/aws-profile/");
        List<HtmlAnchor> tasks = p.getByXPath("//a[contains(@class, 'task-link')]");
        assertThat(tasks, hasSize(5));
        // click delete
        tasks.get(3).fireEvent("click");
        DomElement dialog = p.querySelector("dialog.jenkins-dialog");
        assertThat(dialog, notNullValue());
        DomElement ok = dialog.querySelector("button.jenkins-button[data-id='ok']");
        // confirm deletion
        ok.fireEvent("click");
        assertThat(j.jenkins.clouds, hasSize(0));
    }

    @Test
    void testTemplatesNewNoTemplatesBadRequest(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "aws-profile");

        HtmlPage p = j.createWebClient().goTo("manage/cloud/aws-profile/new");
        // Cloud has no templates, therfore the copy option should not exist
        HtmlDivision div = p.querySelector("div.optionalBlock-container");
        assertThat(div, nullValue());

        // There should be a FormValidation error, stating that the name is required
        assertThat(TestHelper.getFormError(p), equalTo("Required"));

        HtmlForm f = p.getFormByName("config");
        HtmlInput inp = f.getInputByName("cloudName");
        assertThat(inp.getValue(), equalTo("aws-profile"));

        // After setting name, error should be gone
        inp = f.getInputByName("name");
        inp.setValue("whatever");
        TestHelper.triggerValidation(inp);
        div = p.querySelector("div.error");
        assertThat(div, nullValue());

        inp.setValue("");
        FailingHttpStatusCodeException thrown =
                assertThrows(FailingHttpStatusCodeException.class, () ->
                    HtmlFormUtil.submit(f));
        assertThat(thrown.getMessage(), containsString("400 Bad Request"));
     }

    @Test
    void testTemplatesNewNoTemplates(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "aws-profile");

        HtmlPage p = j.createWebClient().goTo("manage/cloud/aws-profile/new");
        // Cloud has no templates, therfore the copy option should not exist
        HtmlDivision div = p.querySelector("div.optionalBlock-container");
        assertThat(div, nullValue());

        // There should be a FormValidation error, stating that the name is required
        assertThat(TestHelper.getFormError(p), equalTo("Required"));

        HtmlForm f = p.getFormByName("config");
        HtmlInput inp = f.getInputByName("cloudName");
        assertThat(inp.getValue(), equalTo("aws-profile"));

        inp = f.getInputByName("name");
        inp.setValue("whatever");
        String purl = p.getUrl().toString();
        HtmlPage p2 = (HtmlPage) HtmlFormUtil.submit(f);
        String p2url = p2.getUrl().toString();
        assertThat(purl.replace("new", "create"), equalTo(p2url));
     }

    @Test
    void testTemplatesNewDuplicate(JenkinsRule j) throws Exception {
        String cid = TestHelper.createTestCloudWithTemplate(j, "foo");
        TestHelper.addTemplateToCloud(j, "foo", "bar", cid);

        HtmlPage p = j.createWebClient().goTo("manage/cloud/foo/new");
        // Cloud has templates, therfore the copy option should exist
        HtmlDivision div = p.querySelector("div.optionalBlock-container");
        assertThat(div, notNullValue());

        // There should be a FormValidation error, stating that the name is required
        assertThat(TestHelper.getFormError(p), equalTo("Required"));

        HtmlForm f = p.getFormByName("config");
        HtmlInput inp = f.getInputByName("cloudName");
        assertThat(inp.getValue(), equalTo("foo"));

        // After setting the the new name to alreadey existing bar, the error message shoulk change appropriately
        inp = f.getInputByName("name");
        inp.setValue("bar");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("A template named bar already exists"));

        FailingHttpStatusCodeException thrown =
                assertThrows(FailingHttpStatusCodeException.class, () ->
                    HtmlFormUtil.submit(f));
        assertThat(thrown.getMessage(), containsString("400 Bad Request"));
     }

    @Test
    void testTemplatesIndexEmpty(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "aws-profile");
        HtmlPage p = j.createWebClient().goTo("manage/cloud/aws-profile/templates");
        List<HtmlDivision> notice = p.getByXPath("//div[contains(@class, 'jenkins-!-padding-bottom-3')]");
        assertThat(notice, hasSize(1));
        assertThat(notice.get(0).getTextContent(), equalTo("No agent template added yet."));
        HtmlAnchor a = p.querySelector("a.jenkins-button");
        assertThat(a, notNullValue());
        assertThat(a.getHrefAttribute(), equalTo("new"));
    }

    @Test
    void testTemplatesIndex(JenkinsRule j) throws Exception {
        String cid = TestHelper.createTestCloudWithTemplate(j, "foo");
        TestHelper.addTemplateToCloud(j, "foo", "bar", cid);

        HtmlPage p = j.createWebClient().goTo("manage/cloud/foo/templates");
        List<HtmlDivision> notice = p.getByXPath("//div[contains(@class, 'jenkins-!-padding-bottom-3')]");
        assertThat(notice, hasSize(0));
        HtmlAnchor a = p.querySelector("a.jenkins-button");
        assertThat(a, notNullValue());
        assertThat(a.getHrefAttribute(), equalTo("new"));
        HtmlParagraph description = p.querySelector("p.description");
        assertThat(description.getTextContent(),
                equalTo("During node provisioning, templates are tried in the order they appear in this table."));
        HtmlTable tbl = p.querySelector("table.jenkins-table");
        assertThat(tbl, notNullValue());
        List<HtmlTableRow> rows = tbl.getRows();
        assertThat(rows, hasSize(3)); // Header row included
        List<HtmlTableCell> cells = rows.get(1).getCells();
        assertThat(cells, hasSize(5));
        assertThat(rows.get(1).getCell(1).getTextContent(), equalTo("FooTemplate"));
        assertThat(rows.get(2).getCell(1).getTextContent(), equalTo("bar"));

        HtmlButton reorder = p.querySelector("button#saveButton");
        assertThat(reorder, notNullValue());
        assertThat(reorder.getAttribute("class") , containsString("jenkins-hidden"));

        // reorder and submit
        HtmlForm f = p.getFormByName("config");
        List<HtmlInput> inputs = f.getInputsByName("name");
        assertThat(inputs, hasSize(2));
        inputs.get(0).setValue("bar");
        inputs.get(1).setValue("FooTemplate");
        HtmlFormUtil.submit(f);

        // Check, that templates order has changed
        JCloudsCloud c = (JCloudsCloud)j.jenkins.clouds.getByName("foo");
        assertThat(c, notNullValue());
        List<JCloudsSlaveTemplate> templates = c.getTemplates();
        assertThat(templates, hasSize(2));
        assertThat(templates.get(0).name, equalTo("bar"));
        assertThat(templates.get(1).name, equalTo("FooTemplate"));
    }

    @Test
    void testFormValidation(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");
        TestHelper.createTestCloudWithTemplate(j, "bar");
        String ecdsaId = TestHelper.createEcdsaCredential();

        HtmlPage p = j.createWebClient().goTo("manage/cloud/foo/configure");
        HtmlForm f = p.getFormByName("config");
        HtmlInput inp = p.getElementByName("_.profile");
        inp.setValue("");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("No name is specified"));
        inp.setValue("bar");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("A cloud named bar already exists"));
        inp.setValue("foo");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = p.getElementByName("_.instanceCap");
        inp.setValue("");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Not a number"));
        inp.setValue("-2");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Not a positive number"));
        inp.setValue("3");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = p.getElementByName("_.retentionTime");
        inp.setValue("-1");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));
        inp.setValue("-2");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Number must be >= -1"));
        inp.setValue("30");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = p.getElementByName("_.scriptTimeout");
        inp.setValue("-1");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Not a positive number"));
        inp.setValue("10000");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = p.getElementByName("_.startTimeout");
        inp.setValue("-1");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("Not a positive number"));
        inp.setValue("10000");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = p.getElementByName("_.endPointUrl");
        inp.setValue("ftp://foo.com");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("The endpoint must be a http(s) URL"));
        inp.setValue("http://whatever.com");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        inp = p.getElementByName("_.groupPrefix");
        inp.setValue("Fritz");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo("The group prefix may contain lowercase letters and numbers only."));
        inp.setValue("abc1234");
        TestHelper.triggerValidation(inp);
        assertThat(TestHelper.getFormError(p), equalTo(""));

        HtmlSelect sel = p.getElementByName("_.cloudGlobalKeyId");
        sel.setSelectedAttribute("", true);
        TestHelper.triggerValidation(sel);
        assertThat(TestHelper.getFormError(p), equalTo("Required"));
        sel.setSelectedAttribute(ecdsaId, true);
        TestHelper.triggerValidation(sel);
        assertThat(TestHelper.getFormError(p), equalTo("Not an RSA SSH key credential"));
        sel.setSelectedAttribute("test-rsa-key", true);
        TestHelper.triggerValidation(sel);
        assertThat(TestHelper.getFormError(p), equalTo(""));
    }

    @Test
    void testConfig(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "aws-profile");
        HtmlPage p = j.createWebClient().goTo("manage/cloud/aws-profile/configure");
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
        assertThat(f.getInputByName("_.profile").getValue(), equalTo("aws-profile"));
        assertThat(f.getInputByName("_.endPointUrl").getValue(), equalTo("http://localhost"));
        f.getInputByName("_.endPointUrl").setValue("http://some.where.else");
        assertThat(f.getInputByName("_.instanceCap").getValue(), equalTo("1"));
        f.getInputByName("_.instanceCap").setValue("99");
        assertThat(f.getInputByName("_.retentionTime").getValue(), equalTo("30"));
        f.getInputByName("_.retentionTime").setValue("123");
        assertThat(f.getInputByName("_.errorRetentionTime").getValue(), equalTo("0"));
        f.getInputByName("_.errorRetentionTime").setValue("456");
        assertThat(f.getInputByName("_.trustAll").isCheckable(), equalTo(true));
        assertThat(f.getInputByName("_.trustAll").isChecked(), equalTo(true));
        f.getInputByName("_.trustAll").setChecked(false);
        assertThat(f.getInputByName("_.scriptTimeout").getValue(), equalTo("600000"));
        f.getInputByName("_.scriptTimeout").setValue("789");
        assertThat(f.getInputByName("_.startTimeout").getValue(), equalTo("600000"));
        f.getInputByName("_.startTimeout").setValue("99999");
        assertThat(f.getInputByName("_.zones").getValue(), equalTo(""));
        f.getInputByName("_.zones").setValue("zone1 zone2");
        assertThat(f.getInputByName("_.groupPrefix").getValue(), equalTo("foobar"));
        f.getInputByName("_.groupPrefix").setValue("nosuffix");

        HtmlButton b = HtmlFormUtil.getButtonByCaption(f, "Test Connection");

        b = HtmlFormUtil.getButtonByCaption(f, "Apply");
        HtmlPage p2 = b.click();
        assertThat(p2, equalTo(p));
        JCloudsCloud c = (JCloudsCloud)j.jenkins.clouds.getByName("aws-profile");
        assertThat(c, notNullValue());
        assertThat(c.endPointUrl, equalTo("http://some.where.else"));

        // rename
        f.getInputByName("_.profile").setValue("aws-profile2");
        b = HtmlFormUtil.getButtonByCaption(f, "Save");
        p2 = b.click();
        assertThat(p2, not(p));

        c = (JCloudsCloud)j.jenkins.clouds.getByName("aws-profile");
        assertThat(c, nullValue());
        c = (JCloudsCloud)j.jenkins.clouds.getByName("aws-profile2");
        assertThat(c, notNullValue());

        assertThat(c.instanceCap, equalTo(99));
        assertThat(c.getErrorRetentionTime(), equalTo(456));
        assertThat(c.getTrustAll(), equalTo(false));
        assertThat(c.scriptTimeout, equalTo(789));
        assertThat(c.startTimeout, equalTo(99999));
        assertThat(c.zones, equalTo("zone1 zone2"));
        assertThat(c.getGroupPrefix(), equalTo("nosuffix"));
    }

    @Test
    void testConfigRoundtrip(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "foo");

        final JCloudsCloud original = JCloudsCloud.getByName("foo");

        j.submit(j.createWebClient().goTo("manage/cloud/foo/configure").getFormByName("config"));
        j.assertEqualBeans(original, j.getInstance().clouds.getByName("foo"),
                "profile,providerName,cloudCredentialsId,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime,errorRetentionTime,groupPrefix");
        j.assertEqualBeans(original, JCloudsCloud.getByName("foo"),
                "profile,providerName,cloudCredentialsId,cloudGlobalKeyId,endPointUrl,instanceCap,retentionTime,errorRetentionTime,groupPrefix");
    }

    @Test
    void testSetTemplates(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "foo");
        JCloudsCloud c = JCloudsCloud.getByName("foo");
        c.setTemplates(null);
        assertThat(c.getTemplates(), notNullValue());
        assertThat(c.getTemplates(), hasSize(0));
    }

    @Test
    void testAllowGzippedUserData(JenkinsRule j) throws Exception {
        TestHelper.createTestCloud(j, "foo");
        assertTrue(JCloudsCloud.getByName("foo").allowGzippedUserData());
    }

    private static void mySelectPresent(final HtmlPage p, final String name) {
        final String xpath = "//select[@name='" + name + "']";
        final List<?> list = p.getByXPath(xpath);
        assertFalse(list.isEmpty(), "Unable to find an select element named '" + name + "'.");
    }

}
