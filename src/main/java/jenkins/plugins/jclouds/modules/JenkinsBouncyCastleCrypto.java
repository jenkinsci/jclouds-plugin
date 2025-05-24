package jenkins.plugins.jclouds.modules;

import jakarta.inject.Singleton;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jclouds.crypto.Crypto;
import org.jclouds.encryption.bouncycastle.BouncyCastleCrypto;
import org.jclouds.encryption.internal.JCECrypto;

/**
 * A JCloud <code>{@link Crypto}</code> implementation, similar to <code>{@link BouncyCastleCrypto}</code>, but which
 * always reuse the same <code>{@link BouncyCastleProvider}</code> instance, to avoid a memory leak.
 */
@Singleton
public class JenkinsBouncyCastleCrypto extends JCECrypto {

    /* The only instance of BouncyCastleProvider we'll ever use in JClouds contexts.
     * It may even be an already registered instance (Jenkins adds one on startup,
     * when initializing the bouncycastle-api plugin). */
    private static final BouncyCastleProvider BC_PROVIDER;

    static {
        final BouncyCastleProvider myBCProvider = new BouncyCastleProvider();
        final Provider installedProvider = Security.getProvider(myBCProvider.getName());
        if (installedProvider != null && installedProvider.getClass().equals(BouncyCastleProvider.class)) {
            BC_PROVIDER = (BouncyCastleProvider) installedProvider;
        } else {
            BC_PROVIDER = myBCProvider;
        }
    }

    public JenkinsBouncyCastleCrypto() throws NoSuchAlgorithmException, CertificateException {
        super(BC_PROVIDER);
    }

    /**
     * @see org.jclouds.encryption.bouncycastle.BouncyCastleCrypto
     */
    @Override
    public Cipher cipher(String algorithm) throws NoSuchAlgorithmException, NoSuchPaddingException {
        return super.cipher("RSA".equals(algorithm) ? "RSA/NONE/PKCS1Padding" : algorithm);
    }
}
