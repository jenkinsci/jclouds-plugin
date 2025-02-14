package jenkins.plugins.jclouds.blobstore;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import hudson.util.Secret;
import jenkins.plugins.jclouds.internal.CredentialsHelper;
import org.jclouds.apis.BaseViewLiveTest;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.util.Maps2;

import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

@SuppressWarnings("unchecked")
public class BlobStoreTestFixture extends BaseViewLiveTest<BlobStoreContext> {
    private static final String PROVIDER;
    private static final boolean SKIPIT;

    /**
     * base jclouds tests expect properties to arrive in a different naming convention, based on provider name.
     * <p>
     * ex.
     *
     * <pre>
     *  test.jenkins.blobstore.provider=aws-s3
     *  test.jenkins.blobstore.identity=access
     *  test.jenkins.blobstore.credential=secret
     * </pre>
     *
     * should turn into
     *
     * <pre>
     *  test.aws-s3.identity=access
     *  test.aws-s3.credential=secret
     * </pre>
     */
    static {
        PROVIDER = System.getProperty("test.jenkins.blobstore.provider");
        SKIPIT = Strings.isNullOrEmpty(PROVIDER);
        Map<String, String> filtered = Maps.filterKeys((Map) System.getProperties(),
                Predicates.containsPattern("^test\\.jenkins\\.blobstore"));
        Map<String, String> transformed = Maps2.transformKeys(filtered, new Function<>() {

            public String apply(String arg0) {
                return arg0.replaceAll("test.jenkins.blobstore", "test." + (SKIPIT ? "" : PROVIDER));
            }

        });
        System.getProperties().putAll(transformed);
    }

    public BlobStoreTestFixture() {
        provider = PROVIDER;
    }

    public BlobStore getBlobStore() {
        return view.getBlobStore();
    }

    public String getProvider() {
        return provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getIdentity() {
        return identity;
    }

    public String getCredential() {
        return credential;
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

    @Override
    protected TypeToken<BlobStoreContext> viewType() {
        return TypeToken.of(BlobStoreContext.class);
    }

}
