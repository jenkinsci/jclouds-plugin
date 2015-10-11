package jenkins.plugins.jclouds.compute;

import static org.junit.Assert.assertEquals;

import org.jvnet.hudson.test.JenkinsRule;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.jclouds.ssh.SshKeys;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;

public class JCloudsCloudInsideJenkinsLiveTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private ComputeTestFixture fixture;
    private JCloudsCloud cloud;
    private Map<String, String> generatedKeys;

    @Before
    public void setUp() throws Exception {
        fixture = new ComputeTestFixture();
        fixture.setUp();
        generatedKeys = SshKeys.generate();

        // TODO: this may need to vary per test
        cloud = new JCloudsCloud(fixture.getProvider() + "-profile", fixture.getProvider(), fixture.getCredentialsId(),
                null, fixture.getEndpoint(), 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                Collections.<JCloudsSlaveTemplate>emptyList());
    }

    @Test
    public void testDoTestConnectionCorrectCredentialsEtc() throws IOException {
        FormValidation result = new JCloudsCloud.DescriptorImpl().doTestConnection(fixture.getProvider(), fixture.getCredentialsId(),
                generatedKeys.get("private"), fixture.getEndpoint(), null);
        assertEquals("Connection succeeded!", result.getMessage());
    }

    @After
    public void tearDown() {
        if (fixture != null)
            fixture.tearDown();
    }
}
