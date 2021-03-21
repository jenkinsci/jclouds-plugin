package jenkins.plugins.jclouds.compute;

import shaded.com.google.common.collect.ImmutableSortedSet;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This test ensures that no Blob Store Providers are dropped or added unintentionally.
 */
public class JCloudsProfileTest {

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
        "oneandone",
        "openhosting-east1",
        "openstack-nova",
        "openstack-nova-ec2",
        "packet",
        "profitbricks",
        "profitbricks-rest",
        "rackspace-cloudservers-uk",
        "rackspace-cloudservers-us",
        "serverlove-z1-man",
        "skalicloud-sdg-my",
        "softlayer",
        "stub"
    };

    @Test
    public void testGetAllProviders() {
        JCloudsCloud.DescriptorImpl desc = new JCloudsCloud.DescriptorImpl();
        ImmutableSortedSet<String> providers = desc.getAllProviders();

        assertTrue("Some previously available providers are missing. Available Providers: "
                + providers.toString() + ", Expected: " + Arrays.toString(ALL_COMPUTE_PROVIDERS),
            providers.containsAll(Arrays.asList(ALL_COMPUTE_PROVIDERS)));

        assertEquals("The number of known Blob Store Providers has changed. Most likely new providers were added.",
            ALL_COMPUTE_PROVIDERS.length, providers.size());
    }
}
