package jenkins.plugins.jclouds.compute;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.cli.CLICommandInvoker;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Fritz Elfert
 */
@WithJenkins
class ProvisionCommandTest {

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
    void testProvisionParams(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-provision").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Argument \"TEMPLATE\" is required"));
    }

    @Test
    void testProvisionNonexistingTemplate(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-provision").invokeWithArgs("foo");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("No such template \"foo\" exists."));
    }

    @Test
    void testProvisionPermission(JenkinsRule j) throws Exception {
        setUp(j);
        TestHelper.createTestCloudWithTemplate(j, "foo");
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-provision").asUser(READER).invokeWithArgs("FooTemplate");
        assertThat(res, failedWith(6));
        assertThat(res.stderr(), containsString("reader is missing the Agent/Provision permission"));
    }

    @Test
    void testrovisionAmbiguousTemplate(JenkinsRule j) throws Exception {
        TestHelper.createTestCloudWithTemplate(j, "foo");
        TestHelper.createTestCloudWithTemplate(j, "bar");
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-provision").invokeWithArgs("FooTemplate");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Template \"FooTemplate\" is ambiguous."));
    }

    /* TODO real live test against some cloud provider */

}
