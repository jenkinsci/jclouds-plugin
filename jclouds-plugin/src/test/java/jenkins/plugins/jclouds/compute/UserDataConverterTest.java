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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.junit.Test;
import org.junit.Rule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.jvnet.hudson.test.JenkinsRule;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;

import jenkins.plugins.jclouds.config.ConfigHelper;
import jenkins.plugins.jclouds.config.UserDataBoothook.UserDataBoothookProvider;
import jenkins.plugins.jclouds.config.UserDataInclude.UserDataIncludeProvider;
import jenkins.plugins.jclouds.config.UserDataIncludeOnce.UserDataIncludeOnceProvider;
import jenkins.plugins.jclouds.config.UserDataScript.UserDataScriptProvider;
import jenkins.plugins.jclouds.config.UserDataUpstart.UserDataUpstartProvider;
import jenkins.plugins.jclouds.config.UserDataYaml.UserDataYamlProvider;

public class UserDataConverterTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void testMigrationBoothook() {
        String data = "#cloud-boothook\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertTrue("Provider is an instance of UserDataBoothookProvider", p instanceof UserDataBoothookProvider);
    }

    @Test
    public void testMigrationInclude() {
        String data = "#include\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertTrue("Provider is an instance of UserDataIncludeProvider", p instanceof UserDataIncludeProvider);
    }

    @Test
    public void testMigrationIncludeOnce() {
        String data = "#include-once\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertTrue("Provider is an instance of UserDataIncludeOnceProvider", p instanceof UserDataIncludeOnceProvider);
    }

    @Test
    public void testMigrationScript() {
        String data = "#!/bin/sh\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertTrue("Provider is an instance of UserDataScriptProvider", p instanceof UserDataScriptProvider);
    }

    @Test
    public void testMigrationUpstart() {
        String data = "#upstart-job\nfoo bar baz";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertTrue("Provider is an instance of UserDataUpstartProvider", p instanceof UserDataUpstartProvider);
    }

    @Test
    public void testMigrationYaml() {
        String data = "#cloud-config\napt_upgrade: true";
        UserData ud = UserData.createFromData(data, "test1.cfg");
        final Config c = ConfigFiles.getByIdOrNull(j.getInstance(), ud.fileId);
        ConfigProvider p = c.getProvider();
        assertTrue("Provider is an instance of UserDataYamlProvider", p instanceof UserDataYamlProvider);
    }

    @Test
    public void testBuildStrip() throws Exception {
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
        byte[] udata = ConfigHelper.buildUserData(idlist, false);
        String sudata = new String(udata);

        assertFalse("Result contains boothook signature", sudata.contains("#cloud-boothook\n"));
        assertFalse("Result contains include signature", sudata.contains("#include\n"));
        assertFalse("Result contains include-once signature", sudata.contains("#include-once\n"));
        assertTrue("Result contains shellscript signature", sudata.contains("#!/bin/sh\n"));
        assertFalse("Result contains upstart-job signature", sudata.contains("#upstart-job\n"));
        assertFalse("Result contains cloud-config signature", sudata.contains("#cloud-config\n"));

        assertTrue("Result contains boothook content", sudata.contains("foo 1\n"));
        assertTrue("Result contains include content", sudata.contains("foo 2\n"));
        assertTrue("Result contains include-once content", sudata.contains("foo 3\n"));
        assertTrue("Result contains shellscript content", sudata.contains("#!/bin/sh\nfoo 4\n"));
        assertTrue("Result contains upstart-job content", sudata.contains("foo 5\n"));
        assertTrue("Result contains cloud-config content", sudata.contains("foo 6\n"));
    }
}
