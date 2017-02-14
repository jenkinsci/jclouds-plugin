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


import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.Rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import jenkins.model.Jenkins;
import hudson.FilePath;

import java.io.File;
import java.util.Collection;
import java.util.Scanner;

public class MigrationTest {

    private final static String SAVE_MIGRATION_PROPERTY = "jenkins.plugins.jclouds.test.saveMigrationResults";

    @Rule public JenkinsRule j = new JenkinsRule();

    private void saveMigrationResult(final String targetPath) throws Exception {
        if (Boolean.getBoolean(SAVE_MIGRATION_PROPERTY)) {
            File target = new File(targetPath);
            File src = Jenkins.getInstance().root;
            target.mkdirs();
            new FilePath(src).copyRecursiveTo("**/*", new FilePath(target));
        }
    }

    private void checkTags(final File f) throws Exception {
        // Verify that the obsoleted tags are gone
        String content = new Scanner(f).useDelimiter("\\Z").next();
        assertFalse("Tag <identity> still in file", content.contains("<identity>"));
        assertFalse("Tag <credential> still in file", content.contains("<credential>"));
        assertFalse("Tag <privateKey> still in file", content.contains("<privateKey>"));
        assertFalse("Tag <publicKey> still in file", content.contains("<publicKey>"));
        assertFalse("Tag <initScript> still in file", content.contains("<initScript>"));
        assertFalse("Tag <userData> still in file", content.contains("<userData>"));
        assertFalse("Tag <vmUser> still in file", content.contains("<vmUser>"));
        assertFalse("Tag <vmPassword> still in file", content.contains("<vmPassword>"));
        assertFalse("Tag <preInstalledJava> still in file", content.contains("<preInstalledJava>"));
        assertFalse("Tag <preInstalledJava> still in file", content.contains("<preInstalledJava>"));
        assertFalse("Tag <assignFloatingIp> still in file", content.contains("<assignFloatingIp>"));
        assertFalse("Tag <isWindows> still in file", content.contains("<isWindows>"));
        // Verify, that new tags are there
        assertTrue("Tag <cloudCredentialsId> in file", content.contains("<cloudCredentialsId>"));
        assertTrue("Tag <cloudGlobalKeyId> in file", content.contains("<cloudGlobalKeyId>"));
        assertTrue("Tag <credentialsId> in file", content.contains("<credentialsId>"));
        assertTrue("Tag <initScriptId> in file", content.contains("<initScriptId>"));
        assertTrue("Tag <userDataEntries> in file", content.contains("<userDataEntries>"));
        assertTrue("Tag <adminCredentialsId> in file", content.contains("<adminCredentialsId>"));
        assertTrue("Tag <useConfigDrive> in file", content.contains("<useConfigDrive>"));
        assertTrue("Tag <waitPhoneHome> in file", content.contains("<waitPhoneHome>"));
        assertTrue("Tag <waitPhoneHomeTimeout> in file", content.contains("<waitPhoneHomeTimeout>"));
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotEightDotOneDashOne() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotEightDotOneDashOne");

        File jhome = Jenkins.getInstance().root;

        File f = new File(jhome, "credentials.xml");
        assertTrue("File credentials.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of credentials.xml", 7246, f.length());
        f = new File(jhome, "org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml");
        String globalConfigFileContent = FileUtils.readFileToString(f);
        assertTrue("File org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml exists in JENKINS_HOME", f.exists());
        assertTrue("jenkins.plugins.jclouds.config.UserDataYaml must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataYaml"));
        assertTrue("jenkins.plugins.jclouds.config.UserDataScript must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataScript"));
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataScript.xml", 843, f.length());

        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of config.xml", 3720, f.length());
        checkTags(f);
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotNine() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotNine");

        File jhome = Jenkins.getInstance().root;

        File f = new File(jhome, "org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml");
        String globalConfigFileContent = FileUtils.readFileToString(f);
        assertTrue("File org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml exists in JENKINS_HOME", f.exists());
        assertTrue("jenkins.plugins.jclouds.config.UserDataYaml must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataYaml"));
        assertTrue("jenkins.plugins.jclouds.config.UserDataScript must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataScript"));
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataScript.xml", 843, f.length());

        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of config.xml", 3750, f.length());
        checkTags(f);
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotTen() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotTen");

        File jhome = Jenkins.getInstance().root;

        File f = new File(jhome, "org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml");
        assertTrue("File org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml exists in JENKINS_HOME", f.exists());
        String globalConfigFileContent = FileUtils.readFileToString(f);
        System.out.println(globalConfigFileContent);
        assertTrue("jenkins.plugins.jclouds.config.UserDataInclude must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataInclude"));
        assertTrue("jenkins.plugins.jclouds.config.UserDataScript must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataScript"));
        assertTrue("jenkins.plugins.jclouds.config.UserDataYaml must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataYaml"));
        // file size checks are reliable, because the generated uuids in there have a constant lenght
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataScript.xml", 2742, f.length());

        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of config.xml", 17495, f.length());
        checkTags(f);
    }
}
