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

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;

import shaded.com.google.common.base.Strings;

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Secret;

import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.jclouds.ContextBuilder;

/**
 * Helper for dealing with credentials.
 * @author Fritz Elfert
 */
public final class CredentialsHelper {

    static final Logger LOGGER = Logger.getLogger(CredentialsHelper.class.getName());

    /**
     * Stores a new credentials record.
     * @param u The new credentials to store;
     * @return The Id of the new record or {@code null} on failure.
     * @throws IOException on error.
     */
    public static String storeCredentials(final StandardUsernameCredentials u) throws IOException {
        if (null != u) {
            try (final ACLContext ctx = ACL.as(ACL.SYSTEM)) {
                final CredentialsStore s = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
                s.addCredentials(Domain.global(), u);
                return u.getId();
            }
        }
        return null;
    }


    /**
     * Use the ssh-slaves-plugin to retrieve a credentials object by its Id.
     *
     * @param id The Id of the credentials object.
     * @return The StandardUsernameCredentials or null if not found.
     */
    public static StandardUsernameCredentials getCredentialsById(final String id) {
        if (Strings.isNullOrEmpty(id)) {
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
        StandardUsernameCredentials u = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, description, identity, Secret.toString(credential));
        try {
            return CredentialsHelper.storeCredentials(u);
        } catch (IOException e) {
            LOGGER.warning(String.format("Error while migrating identity/credentials: %s", e.getMessage()));
        } 
        return null;
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
            if (u instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials up = (StandardUsernamePasswordCredentials)u;
                return cb.credentials(up.getUsername(), getPassword(up.getPassword()));
            } else if (u instanceof SSHUserPrivateKey) {
                SSHUserPrivateKey up = (SSHUserPrivateKey)u;
                return cb.credentials(up.getUsername(), getPrivateKey(up));
            }
            throw new RuntimeException("invalid credentials type");
        }
        throw new RuntimeException("Could not retrieve credentials");
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
}

