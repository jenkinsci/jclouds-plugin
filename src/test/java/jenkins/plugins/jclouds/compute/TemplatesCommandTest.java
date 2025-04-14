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

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.cli.CLICommandInvoker;
import jenkins.model.Jenkins;

import jenkins.plugins.jclouds.internal.CredentialsHelper;

/**
 * @author Fritz Elfert
 */
@WithJenkins
class TemplatesCommandTest {

    private static final String ADMIN = "admin";
    private static final String NOREADER = "noreader";

    // Why does this not work with @BeforeEach?
    public void setUp(JenkinsRule j) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.READ).everywhere().to(ADMIN));
    }

    @Test
    void testTemplatesNoClouds(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-templates").invoke();
        assertThat(res, succeededSilently());
    }

    @Test
    void testTemplatesPermission(JenkinsRule j) throws Exception {
        setUp(j);
        createTestCloudWithTemplate(j, "foo");
        createTestCloudWithTemplate(j, "bar");
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-templates")
            .asUser(NOREADER).invoke();
        assertThat(res, failedWith(6));
    }

    @Test
    void testTemplatesPermission2(JenkinsRule j) throws Exception {
        createTestCloudWithTemplate(j, "foo");
        createTestCloudWithTemplate(j, "bar");
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-templates")
            .invoke();
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString("foo     FooTemplate linux ub2204 single Description"));
        assertThat(res.stdout(), containsString("bar     FooTemplate linux ub2204 single Description"));
    }

    private void createTestCloudWithTemplate(JenkinsRule j, String name) throws Exception {

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription", "CredUser", "secretPassword");
        String cid = CredentialsHelper.storeCredentials(suc);

        final JCloudsSlaveTemplate tpl = new JCloudsSlaveTemplate("FooTemplate", "imageId", null, "hardwareId",
                1, 512, "osFamily", "osVersion", "locationId", "linux ub2204 single" /* labelsString */,
                "Description", null /* initScripId */, 1 /* numExecutors */, false /* stopOnTerminate */,
                "jvmOptions", false /* preExistingJenkinsUser */, null /* fsRoot */, false /* allowSudo */,
                false /* installPrivateKey */, 5 /* overrideRetentionTime */, true /* hasOverrideRetentionTime */,
                0 /* spoolDelayMs */, true /* assignFloatingIp */, false /* waitPhoneHome */, 0 /* waitPhoneHomeTimeout */,
                null /* keyPairName */, true /* assignPublicIp */, "network1_id,network2_id",
                "security_group1,security_group2", cid /* credentialsId */,
                null /* adminCredentialsId */, "NORMAL" /* mode */, true /* useConfigDrive */,
                false /* preemptible */, null /* configDataIds */, "192.168.1.0/24" /* preferredAddress */,
                false /* useJnlp */, false /* jnlpProvisioning */);

        List<JCloudsSlaveTemplate> templates = new ArrayList<>();
        templates.add(tpl);

        JCloudsCloud cloud = new JCloudsCloud(name, "aws-ec2", cid, cid,
                "http://localhost", 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                "foobar", true, templates);
        j.jenkins.clouds.add(cloud);
    }
}
