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

import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.kohsuke.stapler.DataBoundConstructor;

import jenkins.plugins.jclouds.compute.UserData;

public class UserDataUpstart extends Config {
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public UserDataUpstart(final String id, final String name, final String comment, final String content) {
        super(id, name, comment, content);
    }

    @Override
    public ConfigProvider getDescriptor() {
        return Jenkins.get().getDescriptorByType(UserDataUpstartProvider.class);
    }

    @Extension(ordinal = 70)
    @ConfigSuitableFor(target = UserData.class)
    public static class UserDataUpstartProvider extends AbstractJCloudsConfigProviderImpl {

        private static final String SIGNATURE = "^#upstart-job[\\r\\n]+";
        private static final String DEFAULT_CONTENT = "#upstart-job\n";
        private static final String DEFAULT_NAME = "jclouds.upstart";

        public UserDataUpstartProvider() {
            load();
        }

        public String getSignature() {
            return SIGNATURE;
        }

        @Override
        public ContentType getContentType() {
            return CloudInitContentType.UPSTART;
        }

        @Override
        public String getDisplayName() {
            return "JClouds user data (upstart)";
        }

        @Override
        public UserDataUpstart newConfig(final String id) {
            return new UserDataUpstart(id, DEFAULT_NAME, "", DEFAULT_CONTENT);
        }

        /**
         * used for data migration only (config-file-provider prior 1.15)
         */
        @Override
        public UserDataUpstart convert(Config config) {
            return new UserDataUpstart(config.id, config.name, config.comment, config.content);
        }
    }
}
