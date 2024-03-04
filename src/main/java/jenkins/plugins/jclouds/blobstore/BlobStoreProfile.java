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
package jenkins.plugins.jclouds.blobstore;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
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
import org.jclouds.domain.Location;
import org.jclouds.logging.jdk.config.JDKLoggingModule;
import org.jclouds.providers.Providers;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;

import com.thoughtworks.xstream.converters.UnmarshallingContext;

import jenkins.plugins.jclouds.internal.CredentialsHelper;
import jenkins.plugins.jclouds.internal.LocationHelper;
import jenkins.plugins.jclouds.modules.JenkinsConfigurationModule;

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
    private final String locationId;
    private String credentialsId;
    private final boolean trustAll;

    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    @Deprecated
    private final transient String identity;
    /** @deprecated Not used anymore, but retained for backward compatibility during deserialization. */
    @Deprecated
    private final transient String credential;

    @DataBoundConstructor
    public BlobStoreProfile(final String profileName, final String providerName, final String credentialsId,
            final String endPointUrl, final String locationId, final boolean trustAll) {
        this.profileName = profileName;
        this.providerName = providerName;
        this.credentialsId = credentialsId;
        this.endPointUrl = endPointUrl;
        this.locationId = locationId;
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
     * @return The endpoint URL.
     */
    public String getEndPointUrl() {
        return endPointUrl;
    }

    /**
     * credentials.
     *
     * @return The ID of the credentials object.
     */
    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(final String value) {
        credentialsId = value;
    }

    /**
     * location.
     *
     * @return The ID of the selected location.
     */
    public String getLocationId() {
        return locationId;
    }

    static final Iterable<Module> MODULES = ImmutableSet.<Module>of(new JDKLoggingModule() {
        @Override
        public org.jclouds.logging.Logger.LoggerFactory createLoggerFactory() {
            return new BlobStoreLogger.Factory();
        }
    }, new JenkinsConfigurationModule());


    private static BlobStoreContext ctx(final String provider, final String credId, final Properties overrides) {
        // correct the classloader so that extensions can be found
        Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
        CredentialsHelper.setProject(credId, overrides);
        return CredentialsHelper.setCredentials(ContextBuilder.newBuilder(provider), credId)
            .overrides(overrides).modules(MODULES).buildView(BlobStoreContext.class);
    }

    private static Properties buildJCloudsOverrides(final String url, final boolean relaxed) {
        Properties ret = new Properties();
        if (null != url && !url.isEmpty()) {
            ret.setProperty(Constants.PROPERTY_ENDPOINT, url);
        }
        if (relaxed) {
            ret.put(Constants.PROPERTY_TRUST_ALL_CERTS, "true");
            ret.put(Constants.PROPERTY_RELAX_HOSTNAME, "true");
        }
        return ret;
    }

    static BlobStoreContext ctx(final String provider, final String credId, final String url, final boolean relaxed) {
        return ctx(provider, credId, buildJCloudsOverrides(url, relaxed));
    }

    /**
     * Upload the specified file from the
     *
     * @param container - The container where the file needs to be uploaded.
     * @param path      - The path in container where the file needs to be uploaded.
     * @param filePath  - the {@link FilePath} of the file which needs to be uploaded.
     * @throws IOException if an IO error occurs.
     * @throws InterruptedException  If the upload gets interrupted.
     */
    public void upload(final String container, final String path, final FilePath filePath) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }
        try (BlobStoreContext bsc = ctx(providerName, credentialsId, endPointUrl, trustAll)) {
            BlobStore blobStore = bsc.getBlobStore();
            final String locId = Util.fixEmptyAndTrim(locationId);
            Location location = null;
            if (null != locId) {
                for (Location loc : blobStore.listAssignableLocations()) {
                    if (loc.getId().equals(locId)) {
                        location = loc;
                        break;
                    }
                }
            }
            if (blobStore.createContainerInLocation(location, container)) {
                LOGGER.info("Created container " + container);
            }

            String destPath;
            if (path.isEmpty()) {
                destPath = filePath.getName();
            } else {
                destPath = path + "/" + filePath.getName();
            }
            LOGGER.info("Publishing now to container: " + container + " path: " + destPath);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(filePath.read(), md5);

            Blob blob = blobStore.blobBuilder(destPath)
                .payload(dis).contentLength(filePath.length()).build();
            blobStore.putBlob(container, blob);
            bsc.close();
            String md5local = Util.toHexString(md5.digest()).toLowerCase();

            do {
                try (BlobStoreContext bsc2 = ctx(providerName, credentialsId, endPointUrl, trustAll)) {
                    blobStore = bsc2.getBlobStore();
                    LOGGER.info("Fetching remote MD5sum for " + destPath);
                    String md5remote = blobStore.blobMetadata(container, destPath)
                        .getContentMetadata().getContentMD5AsHashCode().toString();
                    if (md5local.equals(md5remote)) {
                        LOGGER.info("Published " + destPath + " to container " + container + " with profile " + profileName);
                    } else {
                        LOGGER.warning("MD5 mismatch while publishing " + destPath + " to container " + container + " with profile " + profileName);
                        throw new IOException("MD5 mismatch while publishing");
                    }
                    break;
                } catch (IllegalStateException ise) {
                    // Happens, if the remote MD5sum is not yet available.
                    if (!ise.getMessage().contains("absent value")) {
                        throw ise;
                    }
                    Thread.sleep(1000);
                }
            } while (true);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 not installed (should never happen).");
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String currentValue) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.get()).hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel().includeCurrentValue(currentValue);
            }
            return new StandardUsernameListBoxModel()
                .includeAs(ACL.SYSTEM2, context, StandardUsernameCredentials.class).includeCurrentValue(currentValue);
        }

        ImmutableSortedSet<String> getAllProviders() {
            // correct the classloader so that extensions can be found
            Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
            // TODO: apis need endpoints, providers don't; do something smarter
            // with this stuff :)
            Builder<String> builder = ImmutableSet.<String> builder();
            builder.addAll(Iterables.transform(Apis.viewableAs(BlobStoreContext.class), Apis.idFunction()));
            builder.addAll(Iterables.transform(Providers.viewableAs(BlobStoreContext.class), Providers.idFunction()));
            return ImmutableSortedSet.copyOf(builder.build());
        }

        public String defaultProviderName() {
            return getAllProviders().first();
        }

        public ListBoxModel doFillProviderNameItems(@AncestorInPath ItemGroup context) {
            ListBoxModel m = new ListBoxModel();
            for (String supportedProvider : getAllProviders()) {
                m.add(supportedProvider, supportedProvider);
            }
            return m;
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("providerName") final String provider,
               @QueryParameter("credentialsId") final String credId,
               @QueryParameter("endPointUrl") final String url,
               @QueryParameter("trustAll") final boolean relaxed) throws IOException {


            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (null == Util.fixEmptyAndTrim(credId)) {
                return FormValidation.error("BlobStore credentials not specified.");
            }
            FormValidation res = FormValidation.ok("Connection succeeded!");
            try (BlobStoreContext ctx = ctx(Util.fixEmptyAndTrim(provider), credId, Util.fixEmptyAndTrim(url), relaxed)) {
                ctx.getBlobStore().list();
            } catch (Exception ex) {
                res = FormValidation.error("Cannot connect to specified BlobStore, please check the credentials: "
                        + ex.getMessage());
            }
            return res;
        }

        public ListBoxModel doFillLocationIdItems(@QueryParameter String providerName,
                @QueryParameter String credentialsId,
                @QueryParameter String endPointUrl) {

            ListBoxModel m = new ListBoxModel();
            m.add("None specified", "");
            if (null == Util.fixEmptyAndTrim(credentialsId)) {
                return m;
            }
            // Remove empty text/whitespace from the fields.
            providerName = Util.fixEmptyAndTrim(providerName);
            endPointUrl = Util.fixEmptyAndTrim(endPointUrl);

            try (BlobStoreContext ctx = ctx(providerName, credentialsId, endPointUrl, true)) {
                LocationHelper.fillLocations(m, ctx.getBlobStore().listAssignableLocations());
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            }
            return m;
        }

        public FormValidation doValidateLocationId(@QueryParameter("providerName") final String provider,
                @QueryParameter("credentialsId") final String credId,
                @QueryParameter("endPointUrl") final String url,
                @QueryParameter("locationId") final String locId) {

            if (null == Util.fixEmptyAndTrim(credId)) {
                return FormValidation.error("No cloud credentials provided.");
            }
            if (null == Util.fixEmptyAndTrim(provider)) {
                return FormValidation.error("Provider Name shouldn't be empty");
            }
            final String testLoc = Util.fixEmptyAndTrim(locId);
            if (null == testLoc) {
                return FormValidation.ok("No location configured. jclouds automatically will choose one.");
            }

            FormValidation res = FormValidation.error("Invalid Location Id, please check the value and try again.");
            try (BlobStoreContext ctx = ctx(provider, credId, url, true)) {
                Set<? extends Location> locations = ctx.getBlobStore().listAssignableLocations();
                for (Location location : locations) {
                    if (!location.getId().equals(testLoc)) {
                        if (location.getId().contains(testLoc)) {
                            return FormValidation.warning("Sorry cannot find the location id, " + "Did you mean: " + location.getId() + "?\n" + location);
                        }
                    } else {
                        return FormValidation.ok("Location Id is valid.");
                    }
                }
            } catch (Exception ex) {
                res = FormValidation.error("Unable to check the location id, " + "please check if the credentials you provided are correct.", ex);
            }
            return res;
        }
    }

    @Restricted(DoNotUse.class)
    public static class ConverterImpl extends XStream2.PassthruConverter<BlobStoreProfile> {
        static final Logger LOGGER = Logger.getLogger(ConverterImpl.class.getName());

        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override protected void callback(BlobStoreProfile bsp, UnmarshallingContext context) {
        if ((null == bsp.getCredentialsId() || bsp.getCredentialsId().isEmpty()) && null != bsp.identity && !bsp.identity.isEmpty()) {
            final String description = "JClouds BlobStore " + bsp.profileName + " - auto-migrated";
            bsp.setCredentialsId(CredentialsHelper.convertCredentials(description, bsp.identity, Secret.fromString(bsp.credential)));
        }
        }
    }
}
