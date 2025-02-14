package jenkins.plugins.jclouds.blobstore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class BlobStoreProfileInsideJenkinsLiveTest {

    private static final String CONTAINER_PREFIX = System.getProperty("user.name") + "-blobstore-profile";

    private BlobStoreTestFixture fixture;
    private BlobStoreProfile profile;

    @BeforeEach
    void setUp() {
        fixture = new BlobStoreTestFixture();
        fixture.setUp();

        // TODO: this may need to vary per test
        profile = new BlobStoreProfile(fixture.getProvider() + "-profile", fixture.getProvider(),
                fixture.getCredentialsId(), fixture.getEndpoint(), null, true);
    }

    @AfterEach
    void tearDown() {
        if (fixture != null) {
            fixture.tearDown();
        }
    }

    @Test
    void testUpload() {
        String container = CONTAINER_PREFIX + "-upload";
        try {
            fixture.getBlobStore().createContainerInLocation(null, container);
            // filePath == ??
            // profile.upload(container, filePath)
            // assertTrue(fixture.getBlobStore().blobExists(container, filename));
        } finally {
            fixture.getBlobStore().deleteContainer(container);
        }
    }


}
