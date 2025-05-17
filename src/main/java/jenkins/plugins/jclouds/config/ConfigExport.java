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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.compute.UserData;
import jenkins.plugins.jclouds.internal.CryptoHelper;

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;

public class ConfigExport {

    private static final Logger LOGGER = Logger.getLogger(ConfigExport.class.getName());

    public final String credentialsId;
    public List<Config> configData;
    protected String encryptedConfigData;

    private final transient List<Config> tmp;

    public ConfigExport(@CheckForNull String id) {
        credentialsId = id;
        tmp = new ArrayList<>();
        for (ConfigProvider p : ConfigProvider.all()) {
            ConfigSuitableFor a = p.getClass().getAnnotation(ConfigSuitableFor.class);
            if (null != a && a.target() == UserData.class) {
                for (Config cfg : ConfigFiles.getConfigsInContext(Jenkins.get(), p.getClass())) {
                    tmp.add(cfg);
                }
            }
        }
        if (null != credentialsId && !credentialsId.isEmpty()) {
            configData = null;
            encryptedConfigData = null;
        } else {
            LOGGER.warning("Unencrypted export");
            configData = tmp;
            encryptedConfigData = null;
        }
    }

    public String getEncryptedConfigData() {
        return encryptedConfigData;
    }

    /**
     * Exports all our userData.
     */
    @NonNull
    public String exportXml() {
        if (null != credentialsId && !credentialsId.isEmpty()) {
            CryptoHelper ch = new CryptoHelper(credentialsId);
            encryptedConfigData = ch.encrypt(Jenkins.XSTREAM.toXML(tmp));
        }
        return Jenkins.XSTREAM.toXML(this);
    }
}
