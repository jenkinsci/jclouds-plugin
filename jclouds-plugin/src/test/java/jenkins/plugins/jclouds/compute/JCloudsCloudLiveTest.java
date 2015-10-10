package jenkins.plugins.jclouds.compute;

import hudson.util.FormValidation;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;

import org.jclouds.ssh.SshKeys;

public class JCloudsCloudLiveTest extends TestCase {

    private ComputeTestFixture fixture;
    private JCloudsCloud cloud;
    private Map<String, String> generatedKeys;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        fixture = new ComputeTestFixture();
        fixture.setUp();
        generatedKeys = SshKeys.generate();

        // TODO: this may need to vary per test
        cloud = new JCloudsCloud(fixture.getProvider() + "-profile", fixture.getProvider(), fixture.getCredentialsId(),
                null, fixture.getEndpoint(), 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                Collections.<JCloudsSlaveTemplate>emptyList());
    }

    public void testDoTestConnectionCorrectCredentialsEtc() throws IOException {
        FormValidation result = new JCloudsCloud.DescriptorImpl().doTestConnection(fixture.getProvider(), fixture.getCredentialsId(),
                generatedKeys.get("private"), fixture.getEndpoint(), null);
        assertEquals("Connection succeeded!", result.getMessage());
    }

    @Override
    public void tearDown() {
        if (fixture != null)
            fixture.tearDown();
    }
}
