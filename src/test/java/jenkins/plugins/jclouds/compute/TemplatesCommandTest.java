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
        TestHelper.createTestCloudWithTemplate(j, "foo");
        TestHelper.createTestCloudWithTemplate(j, "bar");
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-templates")
            .asUser(NOREADER).invoke();
        assertThat(res, failedWith(6));
    }

    @Test
    void testTemplatesPermission2(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");
        TestHelper.createTestCloudWithTemplate(j, "bar");
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-templates")
            .invoke();
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString("foo     FooTemplate jclouds-slave-type1 jclouds-type2 Description"));
        assertThat(res.stdout(), containsString("bar     FooTemplate jclouds-slave-type1 jclouds-type2 Description"));
    }

}
