package jenkins.plugins.jclouds.compute;

import static shaded.com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.internal.BaseComputeServiceContextLiveTest;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.jclouds.util.Maps2;

import shaded.com.google.common.base.Function;
import shaded.com.google.common.base.Predicates;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.collect.Maps;
import com.google.inject.Module;

import jenkins.plugins.jclouds.internal.CredentialsHelper;

import hudson.util.Secret;

import org.junit.Assume;

@SuppressWarnings("unchecked")
public class ComputeTestFixture extends BaseComputeServiceContextLiveTest {
    public static String PROVIDER;
    public static boolean SKIPIT = false;

    /**
     * base jclouds tests expect properties to arrive in a different naming convention, based on provider name.
     *
     * ex.
     *
     * <pre>
     *  test.jenkins.compute.provider=aws-ec2
     *  test.jenkins.compute.identity=access
     *  test.jenkins.compute.credential=secret
     * </pre>
     *
     * should turn into
     *
     * <pre>
     *  test.aws-ec2.identity=access
     *  test.aws-ec2.credential=secret
     * </pre>
     */
    static {
        PROVIDER = System.getProperty("test.jenkins.compute.provider");
        SKIPIT = Strings.isNullOrEmpty(PROVIDER);
        Map<String, String> filtered = Maps.filterKeys(Map.class.cast(System.getProperties()), Predicates.containsPattern("^test\\.jenkins\\.compute"));
        Map<String, String> transformed = Maps2.transformKeys(filtered, new Function<String, String>() {

            public String apply(String arg0) {
                return arg0.replaceAll("test.jenkins.compute", "test." + (SKIPIT ? "" : PROVIDER));
            }

        });
        System.getProperties().putAll(transformed);
    }

    public ComputeTestFixture() {
        provider = PROVIDER;
    }

    @Override
    protected Module getSshModule() {
        return new SshjSshClientModule();
    }

    public ComputeService getComputeService() {
        return view.getComputeService();
    }

    public String getProvider() {
        return provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getCredentialsId() {
        return CredentialsHelper.convertCloudCredentials(provider, identity, Secret.fromString(credential));
    }

    public void setUp() {
        Assume.assumeFalse(SKIPIT);
        if (!SKIPIT) {
            super.setupContext();
        }
    }

    public void tearDown() {
        if (!SKIPIT) {
            super.tearDownContext();
        }
    }

}
