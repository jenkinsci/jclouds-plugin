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
package jenkins.plugins.jclouds.compute;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.Nullable;

import edu.umd.cs.findbugs.annotations.NonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

import static hudson.util.ReflectionUtils.*;

import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.ConfigProvider;

import jenkins.plugins.jclouds.config.JCloudsConfig;
import jenkins.plugins.jclouds.config.ConfigHelper;
import jenkins.plugins.jclouds.config.UserDataScript.UserDataScriptProvider;

/**
 * A simple "bean" for user data entries.
 */
public final class UserData extends AbstractDescribableImpl<UserData> {

    /**
     * The fileId from the config-file-provider plugin
     */
    public final String fileId;

    @DataBoundConstructor
    public UserData(final String fileId) {
        this.fileId = fileId;
    }

    @NonNull
    static UserData createFromData(final String data, final String name) {
        ConfigProvider provider = ConfigProvider.all().get(UserDataScriptProvider.class);
        for (ConfigProvider p : ConfigProvider.all()) {
            if (p instanceof JCloudsConfig) {
                String sig = ((JCloudsConfig) p).getSignature();
                if (Pattern.compile(sig, Pattern.DOTALL).matcher(data).find()) {
                    provider = p;
                    break;
                }
            }
        }
        final String id = UUID.randomUUID().toString();
        Config c = provider.newConfig(id);
        setField(getConfigField("name"), c, name);
        setField(getConfigField("comment"), c, "auto-migrated");
        setField(getConfigField("content"), c, data);

        // migrated data is stored on global scope
        GlobalConfigFiles.get().save(c);
        return new UserData(c.id);
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private static Field getConfigField(String name) {
        Field field = findField(Config.class, name);
        field.setAccessible(true);
        return field;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<UserData> {

        @Override
        public String getDisplayName() {
            return "";
        }

        @NonNull
        public ListBoxModel doFillFileIdItems(@QueryParameter @Nullable final String currentValue) {
            return ConfigHelper.doFillFileItems(currentValue);
        }
    }
}
