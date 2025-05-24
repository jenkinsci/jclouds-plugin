package jenkins.plugins.jclouds.compute;

import com.google.common.collect.ImmutableSortedSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test ensures that no Blob Store Providers are dropped or added unintentionally.
 */
class JCloudsProfileTest {

    /**
     * Static array of known and expected Blob Store Providers.
     */
    private static final String[] ALL_COMPUTE_PROVIDERS = new String[]{
            "aws-ec2",
            "azurecompute-arm",
            "byon",
            "cloudstack",
            "digitalocean2",
            "docker",
            "ec2",
            "elastichosts-lon-b",
            "elastichosts-lon-p",
            "elastichosts-sat-p",
            "elasticstack",
            "go2cloud-jhb1",
            "gogrid",
            "google-compute-engine",
            "openhosting-east1",
            "openstack-nova",
            "openstack-nova-ec2",
            "packet",
            "profitbricks",
            "rackspace-cloudservers-uk",
            "rackspace-cloudservers-us",
            "serverlove-z1-man",
            "skalicloud-sdg-my",
            "softlayer",
            "stub"
    };

    @Test
    void testGetAllProviders() {
        JCloudsCloud.DescriptorImpl desc = new JCloudsCloud.DescriptorImpl();
        ImmutableSortedSet<String> providers = desc.getAllProviders();

        assertTrue(providers.containsAll(Arrays.asList(ALL_COMPUTE_PROVIDERS)),
                "Some previously available providers are missing. Available Providers: "
                        + providers + ", Expected: " + Arrays.toString(ALL_COMPUTE_PROVIDERS));

        assertEquals(ALL_COMPUTE_PROVIDERS.length, providers.size(), "The number of known Compute Providers has changed. Most likely new providers were added.");
    }
}
