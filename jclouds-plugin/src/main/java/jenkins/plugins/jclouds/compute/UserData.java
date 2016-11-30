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

import java.util.UUID;
import java.util.regex.Pattern;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.ConfigProvider;

import jenkins.plugins.jclouds.config.JCloudsConfig;
import jenkins.plugins.jclouds.config.ConfigSuitableFor;
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

    static UserData createFromData(final String data, final String name) {
        ConfigProvider provider = ConfigProvider.all().get(UserDataScriptProvider.class);
        for (ConfigProvider p : ConfigProvider.all()) {
            if (p instanceof JCloudsConfig) {
                String sig = ((JCloudsConfig)p).getSignature();
                if (Pattern.compile(sig, Pattern.DOTALL).matcher(data).matches()) {
                    provider = p;
                    break;
                }
            }
        }
        final String id = UUID.randomUUID().toString();
        Config c = new Config(id, name, "auto-migrated", data, provider.getProviderId());
        provider.save(c);
        return new UserData(id);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<UserData> {

        @Override
        public String getDisplayName() {
            return "";
        }

        public ListBoxModel doFillFileIdItems(@QueryParameter final String currentValue) {
            ListBoxModel m = new ListBoxModel();
            for (ConfigProvider p : ConfigProvider.all()) {
                ConfigSuitableFor a = p.getClass().getAnnotation(ConfigSuitableFor.class);
                if (null != a && a.target() == UserData.class) {
                    for (Config cfg : p.getAllConfigs()) {
                        String label = p.getDisplayName() + " " + cfg.name;
                        if (cfg.comment != null && !cfg.comment.isEmpty()) {
                            label += String.format(" [%s]", cfg.comment);
                        }
                        m.add(label, cfg.id);
                        if (cfg.id.equals(currentValue)) {
                            m.get(m.size() - 1).selected = true;
                        }
                    }
                }
            }
            return m;
        }
    }
}
