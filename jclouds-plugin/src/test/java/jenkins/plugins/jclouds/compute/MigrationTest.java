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


import org.junit.Test;
import org.junit.Rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import jenkins.model.Jenkins;
import hudson.FilePath;

import java.io.File;

public class MigrationTest {

    private final static String SAVE_MIGRATION_PROPERTY = "jenkins.plugins.jclouds.test.saveMigrationResults";

    @Rule public JenkinsRule j = new JenkinsRule();

    private void saveMigrationResult(final String targetPath) throws Exception {
        if (Boolean.getBoolean(SAVE_MIGRATION_PROPERTY)) {
            File target = new File(targetPath);
            File src = Jenkins.getInstance().root;
            target.mkdirs();
            new FilePath(src).copyRecursiveTo("**/*",new FilePath(target));
        }
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotEightDotOneDashOne() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotEightDotOneDashOne");

        File jhome = Jenkins.getInstance().root;
        File f = new File(jhome, "credentials.xml");
        assertTrue("File credentials.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of credentials.xml", 7246, f.length());
        f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataScript.xml");
        assertTrue("File jenkins.plugins.jclouds.config.UserDataScript.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataScript.xml", 667, f.length());
        f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataYaml.xml");
        assertTrue("File jenkins.plugins.jclouds.config.UserDataYaml.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataYaml.xml", 667, f.length());
        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of config.xml", 3720, f.length());
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotNine() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotNine");

        File jhome = Jenkins.getInstance().root;
        File f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataScript.xml");
        assertTrue("File jenkins.plugins.jclouds.config.UserDataScript.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataScript.xml", 667, f.length());
        f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataYaml.xml");
        assertTrue("File jenkins.plugins.jclouds.config.UserDataYaml.xml exists in JENKINS_HOME", f.exists());
        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of config.xml", 3750, f.length());
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotTen() throws Exception {
        saveMigrationResult("/tmp/upgradeFromTwoDotTen");

        File jhome = Jenkins.getInstance().root;
        File f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataInclude.xml");
        assertTrue("File jenkins.plugins.jclouds.config.UserDataInclude.xml exists in JENKINS_HOME", f.exists());
        // file size checks are reliable, because the generated uuids in there have a constant lenght
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataInclude.xml", 1785, f.length());
        f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataScript.xml");
        assertTrue("File jenkins.plugins.jclouds.config.UserDataScript.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataScript.xml", 678, f.length());
        f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataYaml.xml");
        assertTrue("File jenkins.plugins.jclouds.config.UserDataYaml.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataYaml.xml", 1685, f.length());
        f = new File(jhome, "config.xml");
        assertTrue("File config.xml exists in JENKINS_HOME", f.exists());
        assertEquals("File size of config.xml", 17495, f.length());
    }
}
