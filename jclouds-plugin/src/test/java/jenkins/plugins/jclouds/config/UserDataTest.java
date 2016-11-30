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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.junit.Rule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jvnet.hudson.test.JenkinsRule;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;

public class UserDataTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private void createConfig(final List<String> idlist, final ContentType contentType) {
        for (ConfigProvider cp : ConfigProvider.all()) {
            ContentType ct = ConfigHelper.getRealContentType(cp);
            if (null != ct && contentType.equals(ct)) {
                final String id = UUID.randomUUID().toString();
                Config c = cp.newConfig(id);
                cp.save(c);
                idlist.add(id);
                return;
            }
        }
    }

    @Test
    public void testSingleConfig() throws IOException {
        List<String> idlist = new ArrayList<>();
        createConfig(idlist, CloudInitContentType.CLOUDCONFIG);
        assertEquals("Number of configs", 1, idlist.size());
        byte[] udata = ConfigHelper.buildUserData(idlist);
        final String expected = "#cloud-config\n";
        assertEquals(expected, new String(udata));
    }

    @Test
    public void testTwoConfigs() throws IOException {
        List<String> idlist = new ArrayList<>();
        createConfig(idlist, CloudInitContentType.CLOUDCONFIG);
        createConfig(idlist, CloudInitContentType.INCLUDE);
        assertEquals("Number of configs", 2, idlist.size());
        byte[] udata = ConfigHelper.buildUserData(idlist);
        String sudata = new String(udata);
        assertTrue("Result contains multipart header", sudata.contains("Content-Type: multipart/mixed;"));
        assertTrue("Result contains cloud-config part", sudata.contains(
                    "Content-Type: text/cloud-config; charset=utf8; name=jclouds.yaml"));
        assertTrue("Result contains merge header", sudata.contains(
                    "Merge-Type: dict(allow_delete,recurse_array)+list(recurse_array,append)"));
        assertTrue("Result contains include part", sudata.contains(
                    "Content-Type: text/x-include-url; charset=utf8; name=jclouds.include"));
    }
}
