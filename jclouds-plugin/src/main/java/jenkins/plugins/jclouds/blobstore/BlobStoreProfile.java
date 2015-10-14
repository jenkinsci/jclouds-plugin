package jenkins.plugins.jclouds.blobstore;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import shaded.com.google.common.base.Strings;
import shaded.com.google.common.collect.ImmutableSet;
import shaded.com.google.common.collect.ImmutableSet.Builder;
import shaded.com.google.common.collect.ImmutableSortedSet;
import shaded.com.google.common.collect.Iterables;
import shaded.com.google.common.io.Closeables;
import com.google.inject.Module;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.util.XStream2;

import jenkins.model.Jenkins;

import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.apis.Apis;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.providers.Providers;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import com.thoughtworks.xstream.converters.UnmarshallingContext;

import jenkins.plugins.jclouds.internal.CredentialsHelper;

/**
 * Model class for Blobstore profile. User can configure multiple profiles to upload artifacts to different providers.
 *
 * @author Vijay Kiran
 */
public class BlobStoreProfile  extends AbstractDescribableImpl<BlobStoreProfile> {

    private static final Logger LOGGER = Logger.getLogger(BlobStoreProfile.class.getName());

    private final String profileName;
    private final String providerName;
    private final String endPointUrl;
    private String credentialsId;
    private final boolean trustAll;

    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private final transient String identity;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    private final transient String credential;

    @DataBoundConstructor
    public BlobStoreProfile(final String profileName, final String providerName, final String credentialsId, final String endPointUrl, final boolean trustAll) {
        this.profileName = profileName;
        this.providerName = providerName;
        this.credentialsId = credentialsId;
        this.endPointUrl = endPointUrl;
        this.trustAll = trustAll;
        this.identity = null;
        this.credential = null;
    }

    public boolean getTrustAll() {
        return trustAll;
    }

    /**
     * Configured profile.
     *
     * @return - name of the profile.
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Provider Name as per the JClouds Blobstore supported providers.
     *
     * @return - providerName String
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Provider endpoint.
     */
    public String getEndPointUrl() {
        return endPointUrl;
    }

    /**
     * credentials.
     *
     * @return
     */
    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(final String value) {
        credentialsId = value;
    }

    static final Iterable<Module> MODULES = ImmutableSet.<Module>of(new EnterpriseConfigurationModule());

    static BlobStoreContext ctx(String providerName, String credentialsId, Properties overrides) {
        StandardUsernameCredentials u = CredentialsHelper.getCredentialsById(credentialsId);
        if (null != u) {
            // correct the classloader so that extensions can be found
            Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
            if (u instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials up = (StandardUsernamePasswordCredentials)u;
                return ContextBuilder.newBuilder(providerName).credentials(up.getUsername(), up.getPassword().toString())
                    .overrides(overrides).modules(MODULES).buildView(BlobStoreContext.class);
            } else {
                throw new RuntimeException("Using keys as credential for google cloud is not (yet) supported");
            }
        }
        throw new RuntimeException("Could not retrieve credentials");
    }

    static BlobStoreContext ctx(String providerName, String credentialsId, String endPointUrl, boolean trustAll) {
        Properties overrides = new Properties();
        if (!Strings.isNullOrEmpty(endPointUrl)) {
            overrides.setProperty(Constants.PROPERTY_ENDPOINT, endPointUrl);
        }
        if (trustAll) {
            overrides.put(Constants.PROPERTY_TRUST_ALL_CERTS, "true");
            overrides.put(Constants.PROPERTY_RELAX_HOSTNAME, "true");
        }
        return ctx(providerName, credentialsId, overrides);
    }

    /**
     * Upload the specified file from the
     *
     * @param filePath  to container
     * @param container - The container where the file needs to be uploaded.
     * @param path      - The path in container where the file needs to be uploaded.
     * @param filePath  - the {@link FilePath} of the file which needs to be uploaded.
     * @throws IOException
     * @throws InterruptedException
     */
    public void upload(String container, String path, FilePath filePath) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }
        final BlobStoreContext context = ctx(providerName, credentialsId, endPointUrl, trustAll);
        try {
            final BlobStore blobStore = context.getBlobStore();
            if (!blobStore.containerExists(container)) {
                LOGGER.info("Creating container " + container);
                blobStore.createContainerInLocation(null, container);
            }
            if (!path.isEmpty() && !blobStore.directoryExists(container, path)) {
                LOGGER.info("Creating directory " + path + " in container " + container);
                blobStore.createDirectory(container, path);
            }
            String destPath;
            if (path.equals("")) {
                destPath = filePath.getName();
            } else {
                destPath = path + "/" + filePath.getName();
            }
            LOGGER.info("Publishing now to container: " + container + " path: " + destPath);
            Blob blob = context.getBlobStore().blobBuilder(destPath).payload(filePath.read()).build();
            context.getBlobStore().putBlob(container, blob);
            LOGGER.info("Published " + destPath + " to container " + container + " with profile " + profileName);
        } finally {
            context.close();
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<BlobStoreProfile> {

        @Override
        public String getDisplayName() {
            return "";
        }

        public FormValidation doCheckProfileName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckProviderName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckEndPointUrl(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getActiveInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM, null));
        }

        public ListBoxModel doFillProviderNameItems(@AncestorInPath ItemGroup context) {
            ListBoxModel m = new ListBoxModel();
            // correct the classloader so that extensions can be found
            Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
            // TODO: apis need endpoints, providers don't; do something smarter
            // with this stuff :)
            Builder<String> builder = ImmutableSet.<String> builder();
            builder.addAll(Iterables.transform(Apis.viewableAs(BlobStoreContext.class), Apis.idFunction()));
            builder.addAll(Iterables.transform(Providers.viewableAs(BlobStoreContext.class), Providers.idFunction()));
            Iterable<String> supportedProviders = ImmutableSortedSet.copyOf(builder.build());
            for (String supportedProvider : supportedProviders) {
                m.add(supportedProvider, supportedProvider);
            }
            return m;
        }

        public FormValidation doTestConnection(@QueryParameter String providerName, @QueryParameter String credentialsId,
                @QueryParameter String endPointUrl, @QueryParameter boolean trustAll) throws IOException {
            if (null == Util.fixEmptyAndTrim(credentialsId)) {
                return FormValidation.error("BlobStore credentials not specified.");
            }
            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);

            FormValidation result = FormValidation.ok("Connection succeeded!");
            BlobStoreContext ctx = null;
            try {
                ctx(providerName, credentialsId, endPointUrl, trustAll).getBlobStore().list();
            } catch (Exception ex) {
                result = FormValidation.error("Cannot connect to specified BlobStore, please check the credentials: " + ex.getMessage());
            } finally {
                Closeables.close(ctx, true);
            }
            return result;
        }
    }

    @Restricted(DoNotUse.class)
    public static class ConverterImpl extends XStream2.PassthruConverter<BlobStoreProfile> {
        static final Logger LOGGER = Logger.getLogger(ConverterImpl.class.getName());

        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override protected void callback(BlobStoreProfile bsp, UnmarshallingContext context) {
            if (Strings.isNullOrEmpty(bsp.getCredentialsId()) && !Strings.isNullOrEmpty(bsp.identity)) {
                final String description = "JClouds BlobStore " + bsp.profileName + " - auto-migrated";
                bsp.setCredentialsId(CredentialsHelper.convertCredentials(description, bsp.identity, Secret.fromString(bsp.credential)));
            }
        }
    }
}
