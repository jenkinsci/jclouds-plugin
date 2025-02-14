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
package jenkins.plugins.jclouds.compute;

import jenkins.plugins.jclouds.config.ConfigHelper;
import jenkins.plugins.jclouds.config.UserDataBoothook.UserDataBoothookProvider;
import jenkins.plugins.jclouds.config.UserDataInclude.UserDataIncludeProvider;
import jenkins.plugins.jclouds.config.UserDataIncludeOnce.UserDataIncludeOnceProvider;
import jenkins.plugins.jclouds.config.UserDataScript.UserDataScriptProvider;
import jenkins.plugins.jclouds.config.UserDataUpstart.UserDataUpstartProvider;
import jenkins.plugins.jclouds.config.UserDataYaml.UserDataYamlProvider;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class UserDataConverterTest {

    @Test
    void testMigrationBoothook(JenkinsRule j) {
        String data = "#cloud-boothook\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertInstanceOf(UserDataBoothookProvider.class, p, "Provider is an instance of UserDataBoothookProvider");
    }

    @Test
    void testMigrationInclude(JenkinsRule j) {
        String data = "#include\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertInstanceOf(UserDataIncludeProvider.class, p, "Provider is an instance of UserDataIncludeProvider");
    }

    @Test
    void testMigrationIncludeOnce(JenkinsRule j) {
        String data = "#include-once\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertInstanceOf(UserDataIncludeOnceProvider.class, p, "Provider is an instance of UserDataIncludeOnceProvider");
    }

    @Test
    void testMigrationScript(JenkinsRule j) {
        String data = "#!/bin/sh\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertInstanceOf(UserDataScriptProvider.class, p, "Provider is an instance of UserDataScriptProvider");
    }

    @Test
    void testMigrationUpstart(JenkinsRule j) {
        String data = "#upstart-job\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertInstanceOf(UserDataUpstartProvider.class, p, "Provider is an instance of UserDataUpstartProvider");
    }

    @Test
    void testMigrationYaml(JenkinsRule j) {
        String data = "#cloud-config\napt_upgrade: true";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertInstanceOf(UserDataYamlProvider.class, p, "Provider is an instance of UserDataYamlProvider");
    }

    @Test
    void testBuildStrip(JenkinsRule j) throws Exception {
        List<String> idlist = new ArrayList<>();
        UserData ud = UserData.createFromData("#cloud-boothook\nfoo 1\n", "test1.cfg");
        idlist.add(ud.fileId);
        ud = UserData.createFromData("#include\nfoo 2\n", "test2.cfg");
        idlist.add(ud.fileId);
        ud = UserData.createFromData("#include-once\nfoo 3\n", "test3.cfg");
        idlist.add(ud.fileId);
        ud = UserData.createFromData("#!/bin/sh\nfoo 4\n", "test4.cfg");
        idlist.add(ud.fileId);
        ud = UserData.createFromData("#upstart-job\nfoo 5\n", "test5.cfg");
        idlist.add(ud.fileId);
        ud = UserData.createFromData("#cloud-config\nfoo 6\n", "test6.cfg");
        idlist.add(ud.fileId);
        byte[] udata = ConfigHelper.buildUserData(idlist, null, false);
        String sudata = new String(udata);

        assertFalse(sudata.contains("#cloud-boothook\n"), "Result contains boothook signature");
        assertFalse(sudata.contains("#include\n"), "Result contains include signature");
        assertFalse(sudata.contains("#include-once\n"), "Result contains include-once signature");
        assertTrue(sudata.contains("#!/bin/sh\n"), "Result contains shellscript signature");
        assertFalse(sudata.contains("#upstart-job\n"), "Result contains upstart-job signature");
        assertFalse(sudata.contains("#cloud-config\n"), "Result contains cloud-config signature");

        assertTrue(sudata.contains("foo 1\n"), "Result contains boothook content");
        assertTrue(sudata.contains("foo 2\n"), "Result contains include content");
        assertTrue(sudata.contains("foo 3\n"), "Result contains include-once content");
        assertTrue(sudata.contains("#!/bin/sh\nfoo 4\n"), "Result contains shellscript content");
        assertTrue(sudata.contains("foo 5\n"), "Result contains upstart-job content");
        assertTrue(sudata.contains("foo 6\n"), "Result contains cloud-config content");
    }
}
