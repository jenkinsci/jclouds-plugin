/*
 * Copyright 2016 Fritz Elfert
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 * An RSA blockcipher for arbitrary data lenght using an SSHPrivateKey credential.
 */
public class CryptoHelper {

    private static final byte[] LF = new byte[] {(byte) 0x0a};
    private final Cipher cipher;
    private final KeyPair keypair;
    private final int decryptBlockLen;

    public CryptoHelper(@CheckForNull String id) {
        if (null == id) {
            throw new IllegalStateException("Could not get NULL credential");
        }
        try {
            keypair = CredentialsHelper.getKeyPairFromCredential(id);
        } catch (IOException e) {
            throw new IllegalStateException("Could not get keypair from credential: " + e.toString());
        }
        if (keypair.getPrivate() instanceof RSAPrivateKey) {
            RSAKey k = (RSAKey) keypair.getPublic();
            int bitLen = k.getModulus().bitLength();
            decryptBlockLen = bitLen / 8 + (((bitLen % 8) != 0) ? 1 : 0);
            try {
                cipher = Cipher.getInstance("RSA");
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                throw new IllegalStateException("Could not get RSA cipher: " + e.toString());
            }
        } else {
            throw new IllegalStateException(
                    "Invalid key type " + keypair.getPrivate().toString());
        }
    }

    public String encrypt(String plaintext) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, keypair.getPublic());
            byte[] crypted = blockCipher(plaintext.getBytes(StandardCharsets.UTF_8), Cipher.ENCRYPT_MODE);
            return Base64.getMimeEncoder(80, LF).encodeToString(crypted);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("Could not encrypt: " + e.toString());
        }
    }

    public String decrypt(String base64) {
        try {
            cipher.init(Cipher.DECRYPT_MODE, keypair.getPrivate());
            byte[] crypted = Base64.getMimeDecoder().decode(base64);
            return new String(blockCipher(crypted, Cipher.DECRYPT_MODE), StandardCharsets.UTF_8);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("Could not decrypt: " + e.toString());
        }
    }

    private byte[] blockCipher(byte[] bytes, int mode) throws IllegalBlockSizeException, BadPaddingException {
        byte[] result = new byte[0];
        final int blocklen = (mode == Cipher.ENCRYPT_MODE) ? 100 : decryptBlockLen;
        byte[] buffer = new byte[blocklen];

        for (int i = 0; i < bytes.length; i++) {
            if ((i > 0) && (i % blocklen == 0)) {
                // encrypt/decrypt block
                result = concat(result, cipher.doFinal(buffer));
                int newlen = blocklen;
                if (i + blocklen > bytes.length) {
                    newlen = bytes.length - i;
                }
                buffer = new byte[newlen];
            }
            buffer[i % blocklen] = bytes[i];
        }
        // remaining buffer
        return concat(result, cipher.doFinal(buffer));
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
