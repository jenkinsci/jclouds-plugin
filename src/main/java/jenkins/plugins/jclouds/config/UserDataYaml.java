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
package jenkins.plugins.jclouds.config;

import java.util.UUID;
import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.kohsuke.stapler.DataBoundConstructor;

import jenkins.plugins.jclouds.compute.UserData;

public class UserDataYaml extends Config {

    @DataBoundConstructor
    public UserDataYaml(final String id, final String name, final String comment, final String content) {
        super(id, name, comment, content);
    }

    public UserDataYaml dup() {
        UserDataYaml ret = new UserDataYaml(UUID.randomUUID().toString(),
                name, comment, content);
        String pid = getProviderId();
        if (null != pid) {
            ret.setProviderId(pid);
        }
        return ret;
    }

    @Override
    public ConfigProvider getDescriptor() {
        return Jenkins.get().getDescriptorByType(UserDataYamlProvider.class);
    }

    @Extension(ordinal = 70)
    @ConfigSuitableFor(target = UserData.class)
    public static class UserDataYamlProvider extends AbstractJCloudsConfigProviderImpl {

        private static final String SIGNATURE = "^#cloud-config[\\r\\n]+";
        private static final String DEFAULT_CONTENT = "#cloud-config\n";
        private static final String DEFAULT_NAME = "jclouds.yaml";

        public UserDataYamlProvider() {
            load();
        }

        public String getSignature() {
            return SIGNATURE;
        }

        @Override
        public ContentType getContentType() {
            return CloudInitContentType.CLOUDCONFIG;
        }

        @Override
        public String getDisplayName() {
            return "JClouds user data (yaml)";
        }

        @Override
        public Config newConfig(final String id) {
            return new UserDataYaml(id, DEFAULT_NAME, "", DEFAULT_CONTENT);
        }

        /**
         * used for data migration only (config-file-provider prior 1.15)
         */
        @Override
        public UserDataYaml convert(Config config) {
            return new UserDataYaml(config.id, config.name, config.comment, config.content);
        }
    }
}
