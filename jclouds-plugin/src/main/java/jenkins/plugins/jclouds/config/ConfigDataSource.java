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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import javax.activation.DataSource;

import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;

import jenkins.plugins.jclouds.config.UserDataScript.UserDataScriptProvider;

import org.jenkinsci.lib.configprovider.ConfigProvider;

/**
 * A readonly DataSource, backed by a {@link Config}.
 */
public class ConfigDataSource implements DataSource {

    private final Config cfg;
    private final boolean stripSignature;

    /**
     * Creates a new instance from the supplied config.
     * @param config The config to be used for supplying the content.
     * @param strip If {@code true}, then any leading signature will be stripped if possible.
     */
    public ConfigDataSource(final Config config, final boolean strip) {
        cfg = config;
        stripSignature = strip;
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getInputStream() throws IOException {
        String content = null == cfg.content ? "" : cfg.content;
        if (stripSignature) {
            ConfigProvider p = cfg.getProvider();
            if (p instanceof JCloudsConfig && !(p instanceof UserDataScriptProvider)) {
                String sig = ((JCloudsConfig)p).getSignature();
                content = Pattern.compile(sig, Pattern.DOTALL).matcher(content).replaceFirst("");
            }
        }
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * {@inheritDoc}
     */
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("ConfigDatasource is readonly");
    }

    /**
     * {@inheritDoc}
     */
    public String getContentType() {
        ContentType ct = ConfigHelper.getRealContentType(cfg.getProvider());
        String mime = null == ct ? null : ct.getMime();
        return null == mime ? "application/octet-stream" : mime;
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return null == cfg.name ? "" : cfg.name;
    }
}
