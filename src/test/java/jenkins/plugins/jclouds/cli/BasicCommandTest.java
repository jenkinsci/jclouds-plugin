package jenkins.plugins.jclouds.cli;

import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.cli.CLICommandInvoker;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Fritz Elfert
 */
@WithJenkins
class BasicCommandTest {

    @Test
    void testJCloudsCLI(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "help").invoke();
        assertThat(res, succeeded());
        assertThat(res.stderr(), containsString("jclouds-copy-cloud"));
        assertThat(res.stderr(), containsString("jclouds-copy-template"));
        assertThat(res.stderr(), containsString("jclouds-create-cloud"));
        assertThat(res.stderr(), containsString("jclouds-create-template"));
        assertThat(res.stderr(), containsString("jclouds-create-userdata"));
        assertThat(res.stderr(), containsString("jclouds-expire"));
        assertThat(res.stderr(), containsString("jclouds-get-cloud"));
        assertThat(res.stderr(), containsString("jclouds-get-template"));
        assertThat(res.stderr(), containsString("jclouds-get-userdata"));
        assertThat(res.stderr(), containsString("jclouds-provision"));
        assertThat(res.stderr(), containsString("jclouds-templates"));
    }
}
