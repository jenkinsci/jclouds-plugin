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
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.kohsuke.stapler.DataBoundConstructor;

import jenkins.plugins.jclouds.compute.UserData;

public class UserDataPartHandler extends Config {

    @DataBoundConstructor
    public UserDataPartHandler(final String id, final String name, final String comment, final String content) {
        super(id, name, comment, content);
    }

    @Extension(ordinal = 70)
    @ConfigSuitableFor(target=UserData.class)
    public static class UserDataPartHandlerProvider extends AbstractJCloudsConfigProviderImpl {

        private static final String SIGNATURE = "^#part-handler[\\r\\n]+.*";
        private static final String DEFAULT_CONTENT = "#part-handler\n" +
            "def list_types():\n" +
            "   # return a list of mime-types that are handled by this module\n" +
            "   return([\"text/go-cubs-go\"])\n\n" +
            "def handle_part(data,ctype,filename,payload):\n" +
            "   # data: the cloudinit object\n" +
            "   # ctype: '__begin__', '__end__', or the specific mime-type of the part\n" +
            "   # filename: the filename for the part, or dynamically generated part if\n" +
            "   #           no filename is given attribute is present\n" +
            "   # payload: the content of the part (empty for begin or end)\n" +
            "   if ctype == \"__begin__\":\n" +
            "       print \"my handler is beginning\"\n" +
            "       return\n" +
            "   if ctype == \"__end__\":\n" +
            "       print \"my handler is ending\"\n" +
            "       return\n\n" +
            "   print \"==== received ctype=%s filename=%s ====\" % (ctype,filename)\n" +
            "   print payload\n" +
            "   print \"==== end ctype=%s filename=%s\" % (ctype, filename)\n";
        private static final String DEFAULT_NAME = "jclouds.parthandler";

        public UserDataPartHandlerProvider() {
            load();
        }

        public String getSignature() {
            return SIGNATURE;
        }

        @Override
        public ContentType getContentType() {
            return CloudInitContentType.PARTHANDLER;
        }

        @Override
        public String getDisplayName() {
            return "JClouds user data (part-handler)";
        }

        @Override
        public UserDataPartHandler getConfigById(final String configId) {
            final Config c = super.getConfigById(configId);
            return new UserDataPartHandler(c.id, c.name, c.comment, c.content);
        }

        @Override
        public Config newConfig() {
            String id = getProviderId() + "." + System.currentTimeMillis();
            return new Config(id, DEFAULT_NAME, "", DEFAULT_CONTENT);
        }

        @Override
        public Config newConfig(final String id) {
            return new Config(id, DEFAULT_NAME, "", DEFAULT_CONTENT, getProviderId());
        }
    }
}
