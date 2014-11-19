package jenkins.plugins.jclouds.blobstore;

import static shaded.com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.jclouds.apis.BaseViewLiveTest;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.util.Maps2;

import shaded.com.google.common.base.Function;
import shaded.com.google.common.base.Predicates;
import shaded.com.google.common.collect.Maps;
import shaded.com.google.common.reflect.TypeToken;

@SuppressWarnings("unchecked")
public class BlobStoreTestFixture extends BaseViewLiveTest<BlobStoreContext> {
    public static String PROVIDER;

    /**
     * base jclouds tests expect properties to arrive in a different naming convention, based on provider name.
     *
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
        PROVIDER = checkNotNull(System.getProperty("test.jenkins.blobstore.provider"), "test.blobstore.provider variable must be set!");
        Map<String, String> filtered = Maps.filterKeys(Map.class.cast(System.getProperties()), Predicates.containsPattern("^test\\.jenkins\\.blobstore"));
        Map<String, String> transformed = Maps2.transformKeys(filtered, new Function<String, String>() {

            public String apply(String arg0) {
                return arg0.replaceAll("test.jenkins.blobstore", "test." + PROVIDER);
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

    public void setUp() {
        super.setupContext();
    }

    public void tearDown() {
        super.tearDownContext();
    }

    @Override
    protected TypeToken<BlobStoreContext> viewType() {
        return TypeToken.of(BlobStoreContext.class);
    }

}
