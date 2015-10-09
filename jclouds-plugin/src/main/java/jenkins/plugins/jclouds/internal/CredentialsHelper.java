package jenkins.plugins.jclouds.internal;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import shaded.com.google.common.base.Strings;

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.util.Secret;

import jenkins.model.Jenkins;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import java.io.IOException;
import java.util.logging.Logger;

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
            final SecurityContext previousContext = ACL.impersonate(ACL.SYSTEM);
            try {
                final CredentialsStore s = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
                s.addCredentials(Domain.global(), u);
                return u.getId();
            } finally {
                SecurityContextHolder.setContext(previousContext);
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
     * @param name The name of the JCloudsCloud.
     * @param identity The old identity (AKA username).
     * @param credential The old credential (AKA password).
     * @return The Id of the newly created  credential-plugin record.
     */
    public static String convertCloudCredentials(final String name, final String identity, final Secret credential) {
        final String description = "JClouds cloud " + name + " - auto-migrated";
        StandardUsernameCredentials u = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, description, identity, Secret.toString(credential));
        try {
            return CredentialsHelper.storeCredentials(u);
        } catch (IOException e) {
            LOGGER.warning(String.format("Error while migrating identity/credentials: %s", e.getMessage()));
        } 
        return null;
    }

}

