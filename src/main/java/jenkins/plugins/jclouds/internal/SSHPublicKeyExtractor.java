/*
 * Copyright 2010-2016 Adrian Cole, Andrew Bayer, Fritz Elfert, Marat Mavlyutov, Monty Taylor, Vijay Kiran et. al.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jenkins.plugins.jclouds.internal;

import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.signature.DSAKeyAlgorithm;
import com.trilead.ssh2.signature.ECDSAKeyAlgorithm.ECDSASha2Nistp256;
import com.trilead.ssh2.signature.KeyAlgorithm;
import com.trilead.ssh2.signature.KeyAlgorithmManager;
import com.trilead.ssh2.signature.RSAKeyAlgorithm;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;

/**
 * Extracts a SSH public key from a SSH private key.
 * @author Fritz Elfert
 */
public final class SSHPublicKeyExtractor {
    /**
     * Extracts a SSH public key from a PEM-encoded SSH private key.
     * @param pem The PEM-encoded string (either RSA or DSA).
     * @param passPhrase The passphrase to decrypt the private key (may be null, if the key is not encrypted).
     * @return A public key string in the form "&lt;pubkey-type&gt; &lt;pubkey-base64&gt;"
     * @throws IOException if pem could not be decoded properly.
     */
    public static String extract(final String pem, final String passPhrase) throws IOException {
        final KeyPair kp = PEMDecoder.decodeKeyPair(pem.toCharArray(), passPhrase);
        if (kp.getPrivate() instanceof RSAPrivateKey) {
            return "ssh-rsa "
                    + Base64.getEncoder()
                            .encodeToString(new RSAKeyAlgorithm().encodePublicKey((RSAPublicKey) kp.getPublic()));
        }
        if (kp.getPrivate() instanceof DSAPrivateKey) {
            return "ssh-dss "
                    + Base64.getEncoder()
                            .encodeToString(new DSAKeyAlgorithm().encodePublicKey((DSAPublicKey) kp.getPublic()));
        }
        if (kp.getPrivate() instanceof ECPrivateKey) {
            return "ecdsa-sha2-nistp256 "
                    + Base64.getEncoder()
                            .encodeToString(new ECDSASha2Nistp256().encodePublicKey((ECPublicKey) kp.getPublic()));
        }
        if (kp.getPrivate() instanceof EdDSAPrivateKey) {
            for (KeyAlgorithm<PublicKey, PrivateKey> ka : KeyAlgorithmManager.getSupportedAlgorithms()) {
                if (ka.getKeyFormat().equals("ssh-ed25519")) {
                    return "ssh-ed25519 " + Base64.getEncoder().encodeToString(ka.encodePublicKey(kp.getPublic()));
                }
            }
        }
        throw new IOException("unsupported key format: " + kp.getPrivate().toString());
    }
}
