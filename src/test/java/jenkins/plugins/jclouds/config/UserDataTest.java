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

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class UserDataTest {

    @Test
    void testSingleConfig(JenkinsRule j) throws Exception {
        List<String> idlist = new ArrayList<>();
        createConfig(idlist, CloudInitContentType.CLOUDCONFIG);
        assertEquals(1, idlist.size(), "Number of configs");
        byte[] udata = ConfigHelper.buildUserData(idlist, null, false);
        final String expected = "#cloud-config\n";
        assertEquals(expected, new String(udata));
    }

    @Test
    void testTwoConfigs(JenkinsRule j) throws Exception {
        List<String> idlist = new ArrayList<>();
        createConfig(idlist, CloudInitContentType.CLOUDCONFIG);
        createConfig(idlist, CloudInitContentType.INCLUDE);
        assertEquals(2, idlist.size(), "Number of configs");
        byte[] udata = ConfigHelper.buildUserData(idlist, null, false);
        String sudata = new String(udata);
        assertTrue(sudata.contains("Content-Type: multipart/mixed;"), "Result contains multipart header");
        assertTrue(sudata.contains(
                "Content-Type: text/cloud-config; charset=utf8; name=jclouds.yaml"), "Result contains cloud-config part");
        assertTrue(sudata.contains(
                "Merge-Type: dict(allow_delete,recurse_array)+list(recurse_array,append)"), "Result contains merge header");
        assertTrue(sudata.contains(
                "Content-Type: text/x-include-url; charset=utf8; name=jclouds.include"), "Result contains include part");
    }

    private static void createConfig(final List<String> idlist, final ContentType contentType) {
        for (ConfigProvider cp : ConfigProvider.all()) {
            ContentType ct = ConfigHelper.getRealContentType(cp);
            if (contentType.equals(ct)) {
                final String id = UUID.randomUUID().toString();
                Config c = cp.newConfig(id);
                GlobalConfigFiles.get().save(c);
                idlist.add(id);
                return;
            }
        }
    }
}
