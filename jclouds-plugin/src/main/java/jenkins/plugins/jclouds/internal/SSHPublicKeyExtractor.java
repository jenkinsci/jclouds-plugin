package jenkins.plugins.jclouds.internal;

import java.io.IOException;
import java.util.Arrays;
import javax.xml.bind.DatatypeConverter;

import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.signature.DSAPrivateKey;
import com.trilead.ssh2.signature.DSASHA1Verify;
import com.trilead.ssh2.signature.RSAPrivateKey;
import com.trilead.ssh2.signature.RSASHA1Verify;

/**
 * Extracts a SSH public key from a SSH private key.
 * @author Fritz Elfert
 */
public final class SSHPublicKeyExtractor {
    /**
     * Extracts a SSH public key from a PEM-encoded SSH private key.
     * @param pem The PEM-encoded string (either RSA or DSA).
     * @param passPhrase The passphrase to decrypt the private key (may be null, if the key is not encrypted).
     * @return A public key string in the form "<pubkey-type> <pubkey-base64>"
     * @throws IOException, if pem could not be decoded properly.
     */
    public static final String extract(final String pem, final String passPhrase) throws IOException {
        final Object priv = PEMDecoder.decode(pem.toCharArray(), passPhrase);
        if (priv instanceof RSAPrivateKey) {
            return "ssh-rsa " + DatatypeConverter.printBase64Binary(RSASHA1Verify.encodeSSHRSAPublicKey(((RSAPrivateKey)priv).getPublicKey()));
        }
        if (priv instanceof DSAPrivateKey) {
            return "ssh-dss " + DatatypeConverter.printBase64Binary(DSASHA1Verify.encodeSSHDSAPublicKey(((DSAPrivateKey)priv).getPublicKey()));
        }
        throw new IOException("should never happen");
    }
}
