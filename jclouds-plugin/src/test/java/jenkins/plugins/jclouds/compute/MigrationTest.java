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
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Scanner;
import java.util.UUID;

import org.w3c.dom.Document;
import java.io.IOException;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

public class MigrationTest {

    private final static String SAVE_MIGRATION_PROPERTY = "jenkins.plugins.jclouds.test.saveMigrationResults";
    private final static String XPATH1 = "/hudson/clouds/jenkins.plugins.jclouds.compute.JCloudsCloud";
    private final static String XPATH2 = XPATH1 + "/templates/jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate";


    @Rule public JenkinsRule j = new JenkinsRule();

    private void saveMigrationResult(final String targetPath) throws Exception {
        if (Boolean.getBoolean(SAVE_MIGRATION_PROPERTY)) {
            File target = new File(targetPath);
            File src = Jenkins.get().root;
            target.mkdirs();
            new FilePath(src).copyRecursiveTo("**/*", new FilePath(target));
        }
    }

    private boolean isUUID(final String s) {
        try {
            return null != UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void checkExistingTags(final File cfg) throws Exception {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document cfgdoc = builder.parse(new FileInputStream(cfg));
        XPath xPath =  XPathFactory.newInstance().newXPath();
        String tmp = xPath.compile(XPATH1 + "/cloudGlobalKeyId").evaluate(cfgdoc);
        assertTrue("cloudGlobalKeyId value in config is a UUID", isUUID(tmp));
        tmp = xPath.compile(XPATH1 + "/cloudCredentialsId").evaluate(cfgdoc);
        assertTrue("cloudCredentialId value in config is a UUID", isUUID(tmp));
        tmp = xPath.compile(XPATH2 + "/credentialsId").evaluate(cfgdoc);
        assertTrue("credentialId value in config is a UUID", isUUID(tmp));
        tmp = xPath.compile(XPATH2 + "/adminCredentialsId").evaluate(cfgdoc);
        assertTrue("adminCredentialId value in config is a UUID", isUUID(tmp));
        tmp = xPath.compile(XPATH2 + "/initScriptId").evaluate(cfgdoc);
        assertTrue("initScriptId value in config is a UUID", isUUID(tmp));
        tmp = xPath.compile(XPATH2 + "/userDataEntries/jenkins.plugins.jclouds.compute.UserData/fileId").evaluate(cfgdoc);
        assertTrue("UserData fileId in config is a UUID", isUUID(tmp));
        tmp = xPath.compile(XPATH2 + "/useConfigDrive").evaluate(cfgdoc);
        assertEquals("useConfigDrive value in config", "false", tmp);
        tmp = xPath.compile(XPATH2 + "/waitPhoneHome").evaluate(cfgdoc);
        assertEquals("waitPhoneHome value in config", "false", tmp);
        tmp = xPath.compile(XPATH2 + "/waitPhoneHomeTimeout").evaluate(cfgdoc);
        assertEquals("waitPhoneHomeTimeout value in config", "0", tmp);
    }

    private void checkObsoleteTags(final File f) throws Exception {
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
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotEightDotOneDashOne() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotEightDotOneDashOne");

        File jhome = Jenkins.get().root;

        File f = new File(jhome, "credentials.xml");
        assertTrue("File credentials.xml exists in JENKINS_HOME", f.exists());

        f = new File(jhome, "org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml");
        assertTrue("File org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml exists in JENKINS_HOME", f.exists());

        String globalConfigFileContent = new Scanner(f).useDelimiter("\\Z").next();
        assertTrue("jenkins.plugins.jclouds.config.UserDataYaml must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataYaml"));
        assertTrue("jenkins.plugins.jclouds.config.UserDataScript must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataScript"));

        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        checkObsoleteTags(f);
        checkExistingTags(f);
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotNine() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotNine");

        File jhome = Jenkins.get().root;

        File f = new File(jhome, "org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml");
        assertTrue("File org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml exists in JENKINS_HOME", f.exists());

        String globalConfigFileContent = new Scanner(f).useDelimiter("\\Z").next();
        assertTrue("jenkins.plugins.jclouds.config.UserDataYaml must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataYaml"));
        assertTrue("jenkins.plugins.jclouds.config.UserDataScript must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataScript"));

        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        checkObsoleteTags(f);
        checkExistingTags(f);
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotTen() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotTen");

        File jhome = Jenkins.get().root;

        File f = new File(jhome, "org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml");
        assertTrue("File org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml exists in JENKINS_HOME", f.exists());

        String globalConfigFileContent = new Scanner(f).useDelimiter("\\Z").next();
        assertTrue("jenkins.plugins.jclouds.config.UserDataInclude must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataInclude"));
        assertTrue("jenkins.plugins.jclouds.config.UserDataScript must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataScript"));
        assertTrue("jenkins.plugins.jclouds.config.UserDataYaml must be found in global config", globalConfigFileContent.contains("jenkins.plugins.jclouds.config.UserDataYaml"));

        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        checkObsoleteTags(f);
        checkExistingTags(f);
    }
}
