package jenkins.plugins.jclouds.compute;

import hudson.util.FormValidation;
import org.jclouds.ssh.SshKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class JCloudsCloudInsideJenkinsLiveTest {

    private ComputeTestFixture fixture;
    private JCloudsCloud cloud;
    private Map<String, String> generatedKeys;

    @BeforeEach
    void setUp() {
        fixture = new ComputeTestFixture();
        fixture.setUp();
        generatedKeys = SshKeys.generate();

        // TODO: this may need to vary per test
        cloud = new JCloudsCloud(fixture.getProvider() + "-profile", fixture.getProvider(), fixture.getCredentialsId(),
                null, fixture.getEndpoint(), 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000,
                null, "foobar", true, Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        if (fixture != null)
            fixture.tearDown();
    }

    @Test
    void testDoTestConnectionCorrectCredentialsEtc() throws IOException {
        FormValidation result = new JCloudsCloud.DescriptorImpl().doTestConnection(fixture.getProvider(), fixture.getCredentialsId(),
                generatedKeys.get("private"), fixture.getEndpoint(), null, true);
        assertEquals("Connection succeeded!", result.getMessage());
    }
}
