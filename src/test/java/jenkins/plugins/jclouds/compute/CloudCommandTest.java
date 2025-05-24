package jenkins.plugins.jclouds.compute;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.cli.CLICommandInvoker;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.internal.CredentialsHelper;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Fritz Elfert
 */
@WithJenkins
class CloudCommandTest {

    private static final String ADMIN = "admin";
    private static final String READER = "reader";

    // Why does this not work with @BeforeEach?
    public void setUp(JenkinsRule j) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to(ADMIN)
                .grant(Jenkins.READ)
                .everywhere()
                .to(READER));
    }

    @Test
    void testCopyNoParams(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-cloud").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Argument \"FROM-CLOUD\" is required"));
    }

    @Test
    void testCopyOneParam(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-cloud").invokeWithArgs("foo");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Argument \"TO-CLOUD\" is required"));
    }

    @Test
    void testCopyTwoParams(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-cloud").invokeWithArgs("foo", "bar");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("No such profile \"foo\" exists."));
    }

    @Test
    void testCopyPermission(JenkinsRule j) throws Exception {
        setUp(j);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-copy-cloud").asUser(READER).invokeWithArgs("foo", "bar");
        assertThat(res, failedWith(6));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("ERROR: reader is missing the Overall/Administer permission"));
    }

    @Test
    void testCopyFoo(JenkinsRule j) throws Exception {

        JCloudsCloud cloud = new JCloudsCloud(
                "foo",
                "aws-ec2",
                "",
                "",
                "http://localhost",
                1,
                CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES,
                600 * 1000,
                600 * 1000,
                null,
                "foobar",
                true,
                Collections.emptyList());
        j.jenkins.clouds.add(cloud);

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-cloud").invokeWithArgs("foo", "bar");
        assertThat(res, succeededSilently());

        res = new CLICommandInvoker(j, "jclouds-copy-cloud").invokeWithArgs("foo", "bar");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Cloud 'bar' already exists"));
    }

    @Test
    void testCopyFooWithTemplate(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-copy-cloud").invokeWithArgs("foo", "bar");
        assertThat(res, succeededSilently());

        res = new CLICommandInvoker(j, "jclouds-copy-cloud").invokeWithArgs("foo", "bar");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Cloud 'bar' already exists"));
    }

    @Test
    void testGetCloudDoesntExist(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-cloud").invokeWithArgs("foo");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("No such profile \"foo\" exists."));
    }

    @Test
    void testGetCloudPermission(JenkinsRule j) throws Exception {
        setUp(j);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-cloud").asUser(READER).invokeWithArgs("foo");
        assertThat(res, failedWith(6));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("ERROR: reader is missing the Overall/Administer permission"));
    }

    @Test
    void testGetCloud(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-cloud").invokeWithArgs("foo");
        assertThat(res, succeeded());
        assertThat(
                res.stdout(),
                containsString(
                        "<cloudGlobalKeyId sha256=\"db3976ffcd0f6cbce4f764285a7686106b50e347aa42299d4dfa4d42f37d5779\">test-rsa-key"));
        assertThat(
                res.stdout(),
                containsString(
                        "<cloudCredentialsId sha256=\"6ee3634c24bbd89de81712c476a58a233a4251507a7671ac9fabae5079d3e5ca\">"));
    }

    @Test
    void testGetCloudFull(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-cloud").invokeWithArgs("foo", "--full");
        assertThat(res, succeeded());
        assertThat(
                res.stdout(),
                containsString(
                        "<cloudGlobalKeyId sha256=\"db3976ffcd0f6cbce4f764285a7686106b50e347aa42299d4dfa4d42f37d5779\">test-rsa-key"));
        assertThat(
                res.stdout(),
                containsString(
                        "<cloudCredentialsId sha256=\"6ee3634c24bbd89de81712c476a58a233a4251507a7671ac9fabae5079d3e5ca\">"));
        assertThat(res.stdout(), containsString("FooTemplate"));
        assertThat(
                res.stdout(),
                containsString(
                        "<credentialsId sha256=\"6ee3634c24bbd89de81712c476a58a233a4251507a7671ac9fabae5079d3e5ca\">"));
    }

    @Test
    void testGetCloudFullReplacing(JenkinsRule j) throws Exception {
        String id = TestHelper.createTestCloudWithTemplate(j, "foo");
        String id2 = UUID.randomUUID().toString();
        String xml = String.format("<replacements><replacement from=\"%s\" to=\"%s\"/></replacements>", id, id2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo", "--full", "--replace");
        assertThat(res, succeeded());
        String tag = String.format("<%%s>%s", id2);
        assertThat(
                res.stdout(),
                containsString(String.format(
                        "<%s sha256=\"db3976ffcd0f6cbce4f764285a7686106b50e347aa42299d4dfa4d42f37d5779\">test-rsa-key",
                        "cloudGlobalKeyId")));
        assertThat(res.stdout(), containsString(String.format(tag, "cloudCredentialsId")));
        assertThat(res.stdout(), containsString("FooTemplate"));
        assertThat(res.stdout(), containsString(String.format(tag, "credentialsId")));
    }

    @Test
    void testCreateCloudNoParameter(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-cloud").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("ERROR: Argument \"NAME\" is required"));
    }

    @Test
    void testCreateCloudPermission(JenkinsRule j) throws Exception {
        setUp(j);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-create-cloud").asUser(READER).invokeWithArgs("foo");
        assertThat(res, failedWith(6));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("ERROR: reader is missing the Overall/Administer permission"));
    }

    @Test
    void testCreateCloud(JenkinsRule j) throws Exception {
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("create-cloud-cmd.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1)
                .replace("_ID2_", cid2)
                .replace("_HASH1_", hash1)
                .replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo");
        assertThat(res, succeededSilently());

        stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        res = new CLICommandInvoker(j, "jclouds-create-cloud").withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Cloud 'foo' already exists"));
    }

    @Test
    void testCreateCloudNoHash(JenkinsRule j) throws Exception {
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("create-cloud-cmd-nohash.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", cid2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo");
        assertThat(res, succeededSilently());

        stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        res = new CLICommandInvoker(j, "jclouds-create-cloud").withStdin(stdin).invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Cloud 'foo' already exists"));
    }

    @Test
    void testCreateCloudNoHashVerbose(JenkinsRule j) throws Exception {
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("create-cloud-cmd-nohash.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1).replace("_ID2_", cid2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo", "--verbose");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString(String.format("Found cloudGlobalKeyId %s of cloud foo", cid1)));
        assertThat(res.stdout(), containsString(String.format("Found cloudCredentialsId %s of cloud foo", cid2)));
    }

    @Test
    void testCreateCloudVerbose(JenkinsRule j) throws Exception {
        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("create-cloud-cmd.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1)
                .replace("_ID2_", cid2)
                .replace("_HASH1_", hash1)
                .replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-create-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo", "--verbose");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString(String.format("Validated cloudGlobalKeyId %s of cloud foo", cid1)));
        assertThat(res.stdout(), containsString(String.format("Validated cloudCredentialsId %s of cloud foo", cid2)));
    }

    @Test
    void testUpdateCloudNoParameter(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-cloud").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("ERROR: Argument \"NAME\" is required"));
    }

    @Test
    void testUpdateNotExisting(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-cloud").invokeWithArgs("foo");
        assertThat(res, failedWith(2));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("No such profile \"foo\" exists."));
    }

    @Test
    void testUpdatePermissions(JenkinsRule j) throws Exception {
        setUp(j);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-update-cloud").asUser(READER).invokeWithArgs("foo");
        assertThat(res, failedWith(6));
        assertThat(res, hasNoStandardOutput());
        assertThat(res.stderr(), containsString("ERROR: reader is missing the Overall/Administer permission"));
    }

    @Test
    void testUpdateCloudNoOptions(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("update-cloud-cmd.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1)
                .replace("_ID2_", cid2)
                .replace("_HASH1_", hash1)
                .replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Unable to update foo: Need --delete-templates or --keep-templates"));
    }

    @Test
    void testUpdateCloudDeleteTemplates(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("update-cloud-cmd.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1)
                .replace("_ID2_", cid2)
                .replace("_HASH1_", hash1)
                .replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo", "--delete-templates");
        assertThat(res, succeededSilently());

        assertThat(j.jenkins.clouds.getByName("foo"), nullValue());
        assertThat(j.jenkins.clouds.getByName("graustack"), notNullValue());
        JCloudsCloud c = (JCloudsCloud) j.jenkins.clouds.getByName("graustack");
        assertThat(c.getTemplates(), emptyCollectionOf(JCloudsSlaveTemplate.class));
    }

    @Test
    void testUpdateCloudKeepTemplates(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("update-cloud-cmd.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1)
                .replace("_ID2_", cid2)
                .replace("_HASH1_", hash1)
                .replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo", "--keep-templates");
        assertThat(res, succeededSilently());

        assertThat(j.jenkins.clouds.getByName("foo"), nullValue());
        assertThat(j.jenkins.clouds.getByName("graustack"), notNullValue());
        JCloudsCloud c = (JCloudsCloud) j.jenkins.clouds.getByName("graustack");
        assertThat(c.getTemplates().size(), equalTo(1));
    }

    @Test
    void testUpdateCloudRenameToExisting(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");
        TestHelper.createTestCloudWithTemplate(j, "graustack");

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("update-cloud-cmd.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1)
                .replace("_ID2_", cid2)
                .replace("_HASH1_", hash1)
                .replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo", "--keep-templates");
        assertThat(res, failedWith(4));
        assertThat(
                res.stderr(),
                containsString("Unable to rename cloud profile: A cloud with the name graustack already exists"));
    }

    @Test
    void testUpdateCloudVerbose(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser1", "secretPassword1");
        String cid1 = CredentialsHelper.storeCredentials(suc);
        String hash1 = CredentialsHelper.getCredentialsHash(cid1);

        suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription2", "CredUser2", "secretPassword2");
        String cid2 = CredentialsHelper.storeCredentials(suc);
        String hash2 = CredentialsHelper.getCredentialsHash(cid2);

        String xml = new String(
                getClass().getResourceAsStream("update-cloud-cmd.xml").readAllBytes(), StandardCharsets.UTF_8);
        xml = xml.replace("_ID1_", cid1)
                .replace("_ID2_", cid2)
                .replace("_HASH1_", hash1)
                .replace("_HASH2_", hash2);
        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-update-cloud")
                .withStdin(stdin)
                .invokeWithArgs("foo", "--keep-templates", "--verbose");
        assertThat(res, succeeded());
        assertThat(
                res.stdout(), containsString(String.format("Validated cloudGlobalKeyId %s of cloud graustack", cid1)));
        assertThat(
                res.stdout(),
                containsString(String.format("Validated cloudCredentialsId %s of cloud graustack", cid2)));
    }
}
