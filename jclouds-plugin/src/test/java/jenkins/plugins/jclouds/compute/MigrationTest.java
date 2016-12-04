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

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void upgradeFromTwoDotEightDotOneDashOne() throws Exception {

    }

    @Test
    @LocalData
    public void upgradeFromTwoDotNine() throws Exception {
    }

    @Test
    @LocalData
    public void upgradeFromTwoDotTen() throws Exception {
        File jhome = Jenkins.getInstance().root;
        File f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataInclude.xml");
        assertTrue("jenkins.plugins.jclouds.config.UserDataInclude.xml exists in JENKINS_HOME", f.exists());
        // file size checks are reliable, because the generated uuids in there have a constant lenght
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataInclude.xml", 1785, f.length());
        f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataScript.xml");
        assertTrue("jenkins.plugins.jclouds.config.UserDataScript.xml", f.exists());
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataScript.xml", 678, f.length());
        f = new File(jhome, "jenkins.plugins.jclouds.config.UserDataYaml.xml");
        assertTrue("jenkins.plugins.jclouds.config.UserDataYaml.xml", f.exists());
        assertEquals("File size of jenkins.plugins.jclouds.config.UserDataYaml.xml", 1685, f.length());
        f = new File(jhome, "config.xml");
        assertTrue("config.xml", f.exists());
        assertEquals("File size of config.xml", 17495, f.length());

        //File target = new File("/tmp/fritztest");
        //File src = Jenkins.getInstance().root;
        //target.mkdirs();
        //new FilePath(src).copyRecursiveTo("**/*",new FilePath(target));
    }
}
