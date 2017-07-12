package jenkins.plugins.jclouds.blobstore;

import jenkins.plugins.jclouds.blobstore.BlobStoreProfile;
import org.junit.Test;
import shaded.com.google.common.collect.ImmutableSortedSet;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;


public class BlobStoreProfileTest {

    private static String[] ALL_BLOBSTORE_PROVIDERS = new String[] {
        "atmos",
        "aws-s3",
        "azureblob",
        "b2",
        "filesystem",
        "google-cloud-storage",
        "openstack-swift",
        "rackspace-cloudfiles",
        "rackspace-cloudfiles-uk",
        "rackspace-cloudfiles-us",
        "s3",
        "transient"
    };

    @Test
    public void testGetAllProviders() {
        BlobStoreProfile.DescriptorImpl desc = new BlobStoreProfile.DescriptorImpl();
        ImmutableSortedSet<String> providers = desc.getAllProviders();

        //System.out.println(providers.toString());

        assertTrue(providers.size() == ALL_BLOBSTORE_PROVIDERS.length); //make sure we are aware of provider count changes
        assertTrue(providers.containsAll(Arrays.asList(ALL_BLOBSTORE_PROVIDERS)));
  }
}
