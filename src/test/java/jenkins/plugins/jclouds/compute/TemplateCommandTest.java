package jenkins.plugins.jclouds.compute;

import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import hudson.cli.CLICommandInvoker;
import jenkins.model.Jenkins;

import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;

import jenkins.plugins.jclouds.config.ConfigHelper;
import jenkins.plugins.jclouds.config.UserDataYaml;
import jenkins.plugins.jclouds.internal.CredentialsHelper;

/**
 * @author Fritz Elfert
 */
@WithJenkins
class TemplateCommandTest {

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
    void testCopyNoParams(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-template").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Argument \"FROM-TEMPLATE\" is required"));
    }

    @Test
    void testCopyOneParam(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-template").invokeWithArgs("foo");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Argument \"TO-TEMPLATE\" is required"));
    }

    @Test
    void testCopyTwoParams(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-template").invokeWithArgs("FooTemplate", "bar");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("No such template \"FooTemplate\" exists."));
    }

    @Test
    void testCopyPermission(JenkinsRule j) throws Exception {
        setUp(j);
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-template")
                .asUser(READER).invokeWithArgs("foo", "bar");
        assertThat(res, failedWith(6));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("ERROR: reader is missing the Overall/Administer permission"));
    }

    @Test
    void testCopyFooTemplate(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-template").invokeWithArgs("FooTemplate", "bar");
        assertThat(res, succeededSilently());

        res = new CLICommandInvoker(j, "jclouds-copy-template").invokeWithArgs("FooTemplate", "bar");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Template 'bar' already exists"));
    }

    @Test
    void testGetTemplateDoesntExist(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-template").invokeWithArgs("foo");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("No such template \"foo\" exists."));
    }

    @Test
    void testGetTemplatePermission(JenkinsRule j) throws Exception {
        setUp(j);
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-template")
              .asUser(READER).invokeWithArgs("FooTemplate");
        assertThat(res, failedWith(6));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("ERROR: reader is missing the Overall/Administer permission"));
    }

    @Test
    void testGetTemplate(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-template").invokeWithArgs("FooTemplate");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString("FooTemplate"));
        assertThat(res.stdout(), containsString(
              "<credentialsId sha256=\"6ee3634c24bbd89de81712c476a58a233a4251507a7671ac9fabae5079d3e5ca\">"));
    }

    @Test
    void testGetTemplateAmbiguous(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        createTestCloudWithTemplate(j, "bar");

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-template").invokeWithArgs("FooTemplate");
        assertThat(res, failedWith(2));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("ERROR: Template \"FooTemplate\" is ambiguous."));
    }

    @Test
    void testGetTemplateFromProfile(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        createTestCloudWithTemplate(j, "bar");

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-template")
                .invokeWithArgs("FooTemplate", "bar");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString("FooTemplate"));
        assertThat(res.stdout(), containsString(
              "<credentialsId sha256=\"6ee3634c24bbd89de81712c476a58a233a4251507a7671ac9fabae5079d3e5ca\">"));
    }

    @Test
    void testGetTemplateReplacing(JenkinsRule j) throws Exception {
        String id = createTestCloudWithTemplate(j, "foo");
        String id2 = UUID.randomUUID().toString();
        String xml =
            String.format("<replacements><replacement from=\"%s\" to=\"%s\"/></replacements>", id, id2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-template")
                .withStdin(stdin).invokeWithArgs("FooTemplate", "--replace");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString("FooTemplate"));
        assertThat(res.stdout(), containsString(String.format(
              "<credentialsId>%s", id2)));
    }

    @Test
    void testCreateTemplateNoParameter(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("ERROR: Argument \"NAME\" is required"));
    }

    @Test
    void testCreateTemplatePermission(JenkinsRule j) throws Exception {
        setUp(j);
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
              .asUser(READER).invokeWithArgs("foo");
        assertThat(res, failedWith(6));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("ERROR: reader is missing the Overall/Administer permission"));
    }

    @Test
    void testCreateTemplateNoCloud(JenkinsRule j) throws Exception {
        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("At least one jclouds profile must exist to create a template"));
    }

    @Test
    void testCreateTemplateTwoClouds(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        createTestCloudWithTemplate(j, "bar");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_HASH1_", hash1);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("More than one JCloudsCloud exists. Please specify target profile"));
    }

    @Test
    void testCreateTemplateNoUserData(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_HASH1_", hash1);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("fileId _ID2_ in template foo does not resolve"));
    }

    @Test
    void testCreateTemplateHashMismatch(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2)
                .replace("_HASH1_", hash1).replace("_HASH2_", "0123456789abcdef");
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(),
                containsString(String.format("fileId %s in template foo resolves to a different config file", id2)));
    }

    @Test
    void testCreateTemplateNoHash(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, succeededSilently());
    }

    @Test
    void testCreateTemplateNoHashVerbose(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo", "--verbose");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString(
              String.format("Found credentialsId %s of template foo", cid1)));
        assertThat(res.stdout(), containsString(
              String.format("Found fileId %s of template foo", id2)));
    }

    @Test
    void testCreateTemplate(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2).replace("_HASH1_", hash1).replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, succeededSilently());

        stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Template 'foo' already exists"));
    }

    @Test
    void testCreateTemplateVerbose(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2).replace("_HASH1_", hash1).replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo", "--verbose");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString(
              String.format("Validated credentialsId %s of template foo", cid1)));
        assertThat(res.stdout(), containsString(
              String.format("Validated fileId %s of template foo", id2)));
    }

    @Test
    void testCreateTemplateVerboseTwoClouds(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        createTestCloudWithTemplate(j, "bar");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("create-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2).replace("_HASH1_", hash1).replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-template")
                .withStdin(stdin).invokeWithArgs("foo", "bar", "--verbose");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString(
              String.format("Validated credentialsId %s of template foo", cid1)));
        assertThat(res.stdout(), containsString(
              String.format("Validated fileId %s of template foo", id2)));
        JCloudsCloud c = JCloudsCloud.getByName("bar");
        assertThat(c, notNullValue());
        JCloudsSlaveTemplate tpl = c.getTemplate("foo");
        assertThat(tpl, notNullValue());
        assertThat(tpl.getCloud(), equalTo(c));
    }

    @Test
    void testUpdateTemplateNoParams(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-template").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Argument \"TEMPLATE\" is required"));
    }

    @Test
    void testUpdateTemplateNonExisting(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-template")
            .invokeWithArgs("blubb");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("No such template \"blubb\" exists."));
    }

    @Test
    void testUpdateTemplatePermission(JenkinsRule j) throws Exception {
        setUp(j);
        createTestCloudWithTemplate(j, "foo");
        InputStream stdin = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-template")
            .asUser(READER).invokeWithArgs("FooTemplate");
        assertThat(res, failedWith(6));
        assertThat(res.stderr(), containsString("reader is missing the Overall/Administer permission"));
    }

    @Test
    void testUpdateTemplateNoUserData(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String xml = new String(getClass().getResourceAsStream("update-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_HASH1_", hash1);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-template")
                .withStdin(stdin).invokeWithArgs("FooTemplate");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("fileId _ID2_ in template sxts-316 does not resolve"));
    }

    @Test
    void testUpdateTemplate(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("update-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2).replace("_HASH1_", hash1).replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-template")
                .withStdin(stdin).invokeWithArgs("FooTemplate");
        assertThat(res, succeededSilently());

        assertTrue(findTemplate(j, "foo", "sxts-316"), "Renamed template exists");
    }

    @Test
    void testUpdateTemplateRenameExists(JenkinsRule j) throws Exception {
        String cid = createTestCloudWithTemplate(j, "foo");
        addTemplateToCloud(j, "foo", "sxts-316", cid);
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("update-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2).replace("_HASH1_", hash1).replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-template")
                .withStdin(stdin).invokeWithArgs("FooTemplate");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Unable to rename template: A template with the name sxts-316 already exists"));
    }

    @Test
    void testUpdateTemplateVerbose(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        String id2 = UUID.randomUUID().toString();
        Config cfg = new UserDataYaml(id2, "myUserData", "SWome comment", "Some content");
        GlobalConfigFiles.get().save(cfg);
        Map<String, String> cfgHashes = ConfigHelper.getUserDataHashesFromConfigs(List.of(cfg));
        String hash2 = cfgHashes.get(id2);

        String xml = new String(getClass().getResourceAsStream("update-template-cmd.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", id2).replace("_HASH1_", hash1).replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-template")
                .withStdin(stdin).invokeWithArgs("FooTemplate", "--verbose");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString(
              String.format("Validated credentialsId %s of template sxts-316", cid1)));
        assertThat(res.stdout(), containsString(
              String.format("Validated fileId %s of template sxts-316", id2)));

        assertTrue(findTemplate(j, "foo", "sxts-316"), "Renamed template exists");
        assertFalse(findTemplate(j, "foo", "FooTemplate"), "Original template name is gone");
    }

    private boolean findTemplate(JenkinsRule j, String cloud, String name) {
        JCloudsCloud c = (JCloudsCloud)j.jenkins.clouds.getByName(cloud);
        if (null == c) {
            return false;
        }
        if (null == c.getTemplate(name)) {
            return false;
        }
        return true;
    }

    private void addTemplateToCloud(JenkinsRule j, String cloud, String name, String cid) {
        JCloudsCloud c = (JCloudsCloud)j.jenkins.clouds.getByName(cloud);
        if (null != c) {
            final JCloudsSlaveTemplate tpl = new JCloudsSlaveTemplate(name, "imageId", null, "hardwareId",
                1, 512, "osFamily", "osVersion", "locationId", "jclouds-slave-type1 jclouds-type2",
                "Description", null /* initScripId */, 1 /* numExecutors */, false /* stopOnTerminate */,
                "jvmOptions", false /* preExistingJenkinsUser */, null /* fsRoot */, false /* allowSudo */,
                false /* installPrivateKey */, 5 /* overrideRetentionTime */, true /* hasOverrideRetentionTime */,
                0 /* spoolDelayMs */, true /* assignFloatingIp */, false /* waitPhoneHome */, 0 /* waitPhoneHomeTimeout */,
                null /* keyPairName */, true /* assignPublicIp */, "network1_id,network2_id",
                "security_group1,security_group2", cid /* credentialsId */,
                null /* adminCredentialsId */, "NORMAL" /* mode */, true /* useConfigDrive */,
                false /* preemptible */, null /* configDataIds */, "192.168.1.0/24" /* preferredAddress */,
                false /* useJnlp */, false /* jnlpProvisioning */);
            c.addTemplate(tpl);
        }
    }

    private String createTestCloudWithTemplate(JenkinsRule j, String name) throws Exception {

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription", "CredUser", "secretPassword");
        String cid = CredentialsHelper.storeCredentials(suc);

        List<JCloudsSlaveTemplate> templates = new ArrayList<>();
        JCloudsCloud cloud = new JCloudsCloud(name, "aws-ec2", cid, cid,
                "http://localhost", 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                "foobar", true, templates);
        j.jenkins.clouds.add(cloud);
        addTemplateToCloud(j, name, "FooTemplate", cid);
        return cid;
    }

}
