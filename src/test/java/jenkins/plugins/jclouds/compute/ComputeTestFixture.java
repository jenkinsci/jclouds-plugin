package jenkins.plugins.jclouds.compute;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.inject.Module;
import hudson.util.Secret;
import java.util.Map;
import jenkins.plugins.jclouds.internal.CredentialsHelper;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.internal.BaseComputeServiceContextLiveTest;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.jclouds.util.Maps2;

@SuppressWarnings("unchecked")
public class ComputeTestFixture extends BaseComputeServiceContextLiveTest {
    private static final String PROVIDER;
    private static final boolean SKIPIT;

    /**
     * base jclouds tests expect properties to arrive in a different naming convention, based on provider name.
     * <p>
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
        Map<String, String> filtered =
                Maps.filterKeys((Map) System.getProperties(), Predicates.containsPattern("^test\\.jenkins\\.compute"));
        Map<String, String> transformed = Maps2.transformKeys(filtered, new Function<>() {

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
        return CredentialsHelper.convertCredentials(provider, identity, Secret.fromString(credential));
    }

    public void setUp() {
        assumeFalse(SKIPIT);
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
