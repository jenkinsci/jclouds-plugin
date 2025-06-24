package jenkins.plugins.jclouds.modules;

import org.jclouds.concurrent.config.ConfiguresExecutorService;
import org.jclouds.concurrent.config.ExecutorServiceModule;
import org.jclouds.crypto.Crypto;
import org.jclouds.date.joda.config.JodaDateServiceModule;

/**
 * Similar to <code>{@link org.jclouds.enterprise.config.EnterpriseConfigurationModule}</code>,
 * but enables our own <code>JenkinsBouncyCastleCrypto</code> module instead of upstream's
 * <code>BouncyCastleCrypto</code>. This is to avoid memory leak with new instances of
 * <code>BouncyCastleProvider</code> getting registered each time a JClouds context is built.
 */
@ConfiguresExecutorService
public class JenkinsConfigurationModule extends ExecutorServiceModule {

    @Override
    protected void configure() {
        bind(Crypto.class).to(JenkinsBouncyCastleCrypto.class);
        install(new JodaDateServiceModule());
    }
}
