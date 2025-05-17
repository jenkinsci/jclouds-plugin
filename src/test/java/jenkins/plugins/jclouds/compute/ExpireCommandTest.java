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
import hudson.model.Node.Mode;
import hudson.slaves.NodeProperty;
import jenkins.model.Jenkins;

import jenkins.plugins.jclouds.internal.CredentialsHelper;

/**
 * @author Fritz Elfert
 */
@WithJenkins
class ExpireCommandTest {

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
    void testExpireParams(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-expire").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Argument \"NODENAME\" is required"));
    }

    @Test
    void testExpireNonexistingNode(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-expire").invokeWithArgs("foo");
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("No such node \"foo\" exists."));
    }

    @Test
    void testExpirePermission(JenkinsRule j) throws Exception {
        setUp(j);
        createNode(j, "foo");
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-expire")
                .asUser(READER).invokeWithArgs("foo");
        assertThat(res, failedWith(6));
        assertThat(res.stderr(), containsString("reader is missing the Agent/Configure permission"));
    }

    /* TODO real live test against some cloud provider */

    private void createNode(JenkinsRule j, String name) throws Exception {
        JCloudsSlave node = new JCloudsSlave("blubb", name, "nodeDescription", "/jenkins", "2",
            Mode.NORMAL, "labelString", null /* launcher */, null /* retentionStrategy */,
           Collections.<NodeProperty<?>>emptyList() /* nodeProperties */, false /* stopOnTerminate */,
           25 /* overrideRetentionTime */, "nobody" /* user */, "nothing" /* password */,
           null /* privateKey */, false /* authSudo */,
           null /* jvmOptions */, false /* waitPhoneHome */, 0 /* waitPhoneHomeTimeout */,
           null /* credentialsId */, null /* preferredAddress */, false /* useJnlp */,
           false /* jnlpProvisioning */, null /* jnlpProvisioningNonce */);
        j.jenkins.addNode(node);
    }
}
