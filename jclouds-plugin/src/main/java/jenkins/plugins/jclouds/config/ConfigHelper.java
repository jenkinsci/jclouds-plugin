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

import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.jenkinsci.lib.configprovider.ConfigProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.lang.reflect.Method;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.activation.DataHandler;
import javax.activation.DataSource;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class ConfigHelper {

    private static final Logger LOGGER = Logger.getLogger(ConfigHelper.class.getName());

    private ConfigHelper() {
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="REC_CATCH_EXCEPTION", justification="false positive")
    public static ContentType getRealContentType(ConfigProvider p) {
        ContentType ct = p.getContentType();
        if (null == ct) {
            try {
                Method m = p.getClass().getMethod("getRealContentType");
                Object o = m.invoke(p);
                if (o instanceof ContentType) {
                    ct = (ContentType)o;
                }
            } catch (Exception x) {
                ct = null;
            }
        }
        return ct;
    }

    private static BodyPart buildBody(final Config cfg) {
        DataSource source = new ConfigDataSource(cfg);
        if (null != source) {
            try {
                BodyPart body = new MimeBodyPart();
                final String mime = source.getContentType();
                body.setDataHandler(new DataHandler(source));
                body.setHeader("Content-Type", mime + "; charset=\"utf8\"");
                if (mime.equals("text/cloud-config")) {
                    body.setHeader("Merge-Type",
                            "dict(allow_delete,recurse_array)+list(recurse_array,append)");
                }
                body.setFileName(source.getName());
                return body;
            } catch (MessagingException e) {
                LOGGER.log(Level.WARNING, "", e);
            }
        }
        return null;
    }

    private static List<Config> getConfigs(final List<String> configIds) {
        List<Config> ret = new ArrayList<>();
        for (final String id : configIds) {
            final Config cfg = Config.getByIdOrNull(id);
            if (null != cfg) {
                ret.add(cfg);
            }
        }
        return ret;
    }

    public static byte [] buildUserData(final List<String> configIds) {
        List<Config> configs = getConfigs(configIds);
        if (configs.isEmpty()) {
            return null;
        }
        if (configs.size() > 1) {
            try {
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final MimeMessage msg = new MimeMessage((Session)null);
                final Multipart multipart = new MimeMultipart();
                msg.setFrom(new InternetAddress("nobody"));
                for (final Config cfg : configs) {
                    BodyPart body = buildBody(cfg);
                    if (null != body) {
                        multipart.addBodyPart(body);
                    }
                }
                msg.setContent(multipart);
                msg.writeTo(bos);
                return bos.toByteArray();
            } catch (IOException | MessagingException e) {
                LOGGER.log(Level.WARNING, "", e);
            }
        } else {
            Config cfg = configs.get(0);
            if (null != cfg.content && !cfg.content.isEmpty()) {
                return cfg.content.getBytes(StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
