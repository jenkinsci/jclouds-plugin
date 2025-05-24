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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.trilead.ssh2.crypto.PEMDecoder;
import hudson.model.Descriptor.FormException;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.credentials.OpenstackKeystoneV3;
import org.jclouds.ContextBuilder;
import org.jclouds.openstack.keystone.config.KeystoneProperties;

/**
 * Helper for dealing with credentials.
 * @author Fritz Elfert
 */
public final class CredentialsHelper {

    static final Logger LOGGER = Logger.getLogger(CredentialsHelper.class.getName());

    /**
     * Stores a new credentials record (Used only during migration).
     * @param u The new credentials to store;
     * @return The Id of the new record or {@code null} on failure.
     * @throws IOException on error.
     */
    public static String storeCredentials(final StandardUsernameCredentials u) throws IOException {
        if (null != u) {
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) { // NOPMD - unused local variable
                final CredentialsStore s = CredentialsProvider.lookupStores(Jenkins.get())
                        .iterator()
                        .next();
                s.addCredentials(Domain.global(), u);
                return u.getId();
            }
        }
        return null;
    }

    public static boolean isRSACredential(final String id) {
        try {
            new CryptoHelper(id);
        } catch (IllegalStateException x) {
            return false;
        }
        return true;
    }

    /**
     * Use the ssh-slaves-plugin to retrieve a credentials object by its Id.
     *
     * @param id The Id of the credentials object.
     * @return The StandardUsernameCredentials or null if not found.
     */
    public static StandardUsernameCredentials getCredentialsById(final String id) {
        if (null == id || id.isEmpty()) {
            return null;
        }
        return SSHLauncher.lookupSystemCredentials(id);
    }

    /**
     * Converts old identity/credential new UsernamePassword credential-plugin record.
     * @param description The for the credentials record.
     * @param identity The old identity (AKA username).
     * @param credential The old credential (AKA password).
     * @return The Id of the newly created  credential-plugin record.
     */
    public static String convertCredentials(final String description, final String identity, final Secret credential) {
        try {
            StandardUsernameCredentials u = new UsernamePasswordCredentialsImpl(
                    CredentialsScope.SYSTEM, null, description, identity, Secret.toString(credential));
            return storeCredentials(u);
        } catch (IOException | FormException e) {
            LOGGER.warning(String.format("Error while migrating identity/credentials: %s", e.getMessage()));
        }
        return null;
    }

    /**
     * Handles new OpenstackKeystoneV3 credentials and sets jclouds overrides accordingly.
     */
    public static void setProject(final String id, Properties overrides) {
        StandardUsernameCredentials u = getCredentialsById(id);
        if (null != u) {
            if (u instanceof OpenstackKeystoneV3) {
                overrides.put(KeystoneProperties.KEYSTONE_VERSION, "3");
                OpenstackKeystoneV3 ok3 = (OpenstackKeystoneV3) u;
                if (!ok3.getProject().isEmpty()) {
                    overrides.put(KeystoneProperties.SCOPE, "project:" + ok3.getProject());
                }
                return;
            } else if (u instanceof StandardUsernamePasswordCredentials) {
                return;
            } else if (u instanceof SSHUserPrivateKey) {
                return;
            }
            throw new RuntimeException("invalid credentials type");
        }
        throw new RuntimeException("Could not retrieve credentials");
    }

    /**
     * Populates the credential of a JClouds ContextBuilder from a credentials record.
     * @param cb The {@link org.jclouds.ContextBuilder} which should get the credential.
     * @param id The Id of the credentials object.
     * @return The modified {@link org.jclouds.ContextBuilder}
     */
    public static ContextBuilder setCredentials(final ContextBuilder cb, final String id) {
        StandardUsernameCredentials u = getCredentialsById(id);
        if (null != u) {
            if (u instanceof OpenstackKeystoneV3) {
                OpenstackKeystoneV3 ok3 = (OpenstackKeystoneV3) u;
                String domainname = ok3.getDomain().isEmpty() ? "default" : ok3.getDomain();
                return cb.credentials(domainname + ":" + ok3.getUsername(), getPassword(ok3.getPassword()));
            } else if (u instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials up = (StandardUsernamePasswordCredentials) u;
                return cb.credentials(up.getUsername(), getPassword(up.getPassword()));
            } else if (u instanceof SSHUserPrivateKey) {
                SSHUserPrivateKey up = (SSHUserPrivateKey) u;
                return cb.credentials(up.getUsername(), getPrivateKey(up));
            }
            throw new RuntimeException("invalid credentials type");
        }
        throw new RuntimeException("Could not retrieve credentials");
    }

    /**
     * Calculates a SHA-256 hash of the credential with the specified Id.
     *
     * This hash will be used for comparing credentials.
     *
     * @param id The Id of the credentials object.
     * @return String containig a hex representation of the calculated hash.
     *
     * <p>The following credential types are supported:</p>
     *
     *  <ul>
     *    <li><b>SSHUserPrivateKey</b>: The hash will be calculated from
     *    <ul>
     *      <li>The passphrase
     *      <li>The username
     *      <li>The private key content
     *    </ul>
     *    <li><b>StandardUsernamePasswordCredentials</b>: The hash will be calculated from
     *    <ul>
     *      <li>The username
     *      <li>The password
     *    </ul>
     *    <li><b>OpenstackKeystoneV3</b>: The hash will be calculated from
     *    <ul>
     *      <li>The domain
     *      <li>The project
     *      <li>The username
     *      <li>The password
     *    </ul>
     *  </ul>
     *
     */
    public static String getCredentialsHash(final String id) throws NoSuchAlgorithmException {
        StandardUsernameCredentials u = getCredentialsById(id);
        if (null != u) {
            HexFormat hex = HexFormat.of();
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            if (u instanceof OpenstackKeystoneV3) {
                OpenstackKeystoneV3 ok3 = (OpenstackKeystoneV3) u;
                md.update(ok3.getDomain().getBytes(StandardCharsets.UTF_8));
                md.update(ok3.getProject().getBytes(StandardCharsets.UTF_8));
                md.update(ok3.getUsername().getBytes(StandardCharsets.UTF_8));
                return hex.formatHex(
                        md.digest(getPasswordOrEmpty(ok3.getPassword()).getBytes(StandardCharsets.UTF_8)));
            } else if (u instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials up = (StandardUsernamePasswordCredentials) u;
                md.update(up.getUsername().getBytes(StandardCharsets.UTF_8));
                return hex.formatHex(
                        md.digest(getPasswordOrEmpty(up.getPassword()).getBytes(StandardCharsets.UTF_8)));
            } else if (u instanceof SSHUserPrivateKey) {
                SSHUserPrivateKey up = (SSHUserPrivateKey) u;
                md.update(getPasswordOrEmpty(up.getPassphrase()).getBytes(StandardCharsets.UTF_8));
                md.update(up.getUsername().getBytes(StandardCharsets.UTF_8));
                return hex.formatHex(
                        md.digest(String.join("", up.getPrivateKeys()).getBytes(StandardCharsets.UTF_8)));
            }
            throw new RuntimeException("invalid credentials type");
        }
        throw new RuntimeException("Could not retrieve credentials with id " + id);
    }

    public static String getPrivateKey(final SSHUserPrivateKey supk) {
        if (null == supk) {
            return "";
        }
        List<String> privateKeys = supk.getPrivateKeys();
        return privateKeys.isEmpty() ? "" : privateKeys.get(0);
    }

    public static String getPassword(final Secret s) {
        // We explicitely DO want a possible null, because in jclouds
        // this means: No password set (vs. an empty password).
        return null == s ? null : s.getPlainText();
    }

    private static String getPasswordOrEmpty(final Secret s) {
        return null == s ? "" : s.getPlainText();
    }

    public static KeyPair getKeyPairFromCredential(final String id) throws IOException {
        if (null != id && !id.isEmpty()) {
            SSHUserPrivateKey supk = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItemGroup(SSHUserPrivateKey.class, null, null),
                    CredentialsMatchers.withId(id));
            if (null == supk) {
                throw new IOException("Credential " + id + " is not available");
            }
            String pem = getPrivateKey(supk);
            String passPhrase = getPassword(supk.getPassphrase());
            return PEMDecoder.decodeKeyPair(pem.toCharArray(), passPhrase);
        }
        return null;
    }
}
