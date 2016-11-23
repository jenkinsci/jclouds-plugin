package jenkins.plugins.jclouds.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.apache.commons.fileupload.FileItem;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

/**
 * A simple wrapper for {@link com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey}
 * which allows import of a Google JSON key.
 */
public class JCloudsUserWithKey extends BasicSSHUserPrivateKey {

    private static final Logger LOGGER = Logger.getLogger(JCloudsUserWithKey.class.getName());

    @DataBoundConstructor
    public JCloudsUserWithKey(final CredentialsScope scope, final String id, final String description,
            final String username, final String privateKey, final String jsonFile) {
        super(scope, id, handleUser(jsonFile, username),
                handleKey(jsonFile, privateKey),
                null, handleDescription(jsonFile, description));
    }

    /**
     * {@inheritDoc}
     */
    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "JClouds Username with key";
        }
    }

    private static String getJsonString(final String fileUploadEntry, final String fieldName) {
        if (null != fileUploadEntry) {
            try {
                final FileItem fi = Stapler.getCurrentRequest().getFileItem(fileUploadEntry);
                if (null != fi) {
                    final String content = new String(fi.get(), StandardCharsets.UTF_8);
                    if (null != content && !content.isEmpty()) {
                        final JsonObject jo = new JsonParser().parse(content).getAsJsonObject();
                        final String value = jo.get(fieldName).getAsString();
                        return value;
                    }
                }
            } catch (Exception x) {
                LOGGER.warning(x.getMessage());
            }
        }
        return null;
    }

    private static DirectEntryPrivateKeySource handleKey(final String json, final String key) {
        String value = getJsonString(json, "private_key");
        if (null == value) {
            return new DirectEntryPrivateKeySource(key);
        }
        return new DirectEntryPrivateKeySource(value);
    }

    private static String handleUser(final String json, final String user) {
        String value = getJsonString(json, "client_email");
        if (null == value) {
            if (null == user || user.isEmpty()) {
                return "unknown";
            }
            return user;
        }
        return value;
    }

    private static String handleDescription(final String json, final String description) {
        String value = getJsonString(json, "project_id");
        if (null == value) {
            return description;
        }
        return String.format("Imported JSON key for %s", value);
    }
}
