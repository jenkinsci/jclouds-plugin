package jenkins.plugins.jclouds.blobstore;

import com.google.common.collect.ImmutableSortedSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test ensures that no Blob Store Providers are dropped or added unintentionally.
 */
class BlobStoreProfileTest {

    /**
     * Static array of known and expected Blob Store Providers.
     */
    private static final String[] ALL_BLOBSTORE_PROVIDERS = new String[]{
            "atmos",
            "aws-s3",
            "azureblob",
            "b2",
            "glacier",
            "google-cloud-storage",
            "openstack-swift",
            "rackspace-cloudfiles",
            "rackspace-cloudfiles-uk",
            "rackspace-cloudfiles-us",
            "s3",
            "transient"
    };

    @Test
    void testGetAllProviders() {
        BlobStoreProfile.DescriptorImpl desc = new BlobStoreProfile.DescriptorImpl();
        ImmutableSortedSet<String> providers = desc.getAllProviders();

        assertTrue(providers.containsAll(Arrays.asList(ALL_BLOBSTORE_PROVIDERS)),
                "Some previously available providers are missing. Available Providers: "
                        + providers + ", Expected: " + Arrays.toString(ALL_BLOBSTORE_PROVIDERS));

        assertEquals(ALL_BLOBSTORE_PROVIDERS.length, providers.size(), "The number of known Blob Store Providers has changed. Most likely new providers were added.");
    }
}
