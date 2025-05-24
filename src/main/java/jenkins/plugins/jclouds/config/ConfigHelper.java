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

import com.google.common.base.Joiner;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.util.ListBoxModel;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.compute.UserData;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.jenkinsci.plugins.configfiles.ConfigFiles;

public class ConfigHelper {

    private static final Logger LOGGER = Logger.getLogger(ConfigHelper.class.getName());

    private ConfigHelper() {}

    @NonNull
    public static String getConfig(@Nullable final String id) {
        if (id != null) {
            final Config cfg = ConfigFiles.getByIdOrNull(Jenkins.get(), id);
            if (null != cfg && null != cfg.content) {
                return cfg.content;
            }
        }
        return "";
    }

    public static ContentType getRealContentType(@NonNull ConfigProvider p) {
        ContentType ct = p.getContentType();
        if (null == ct && p instanceof JCloudsConfig) {
            ct = ((JCloudsConfig) p).getRealContentType();
        }
        return ct;
    }

    @CheckForNull
    private static BodyPart buildBody(final Config cfg, @Nullable Map<String, String> replacements) {
        DataSource source = new ConfigDataSource(cfg, true, replacements);
        if (null != source) {
            try {
                BodyPart body = new MimeBodyPart();
                final String mime = source.getContentType();
                body.setDataHandler(new DataHandler(source));
                body.setHeader("Content-Type", mime + "; charset=\"utf8\"");
                if (mime.equals("text/cloud-config")) {
                    body.setHeader("Merge-Type", "dict(allow_delete,recurse_array)+list(recurse_array,append)");
                }
                body.setFileName(source.getName());
                return body;
            } catch (MessagingException e) {
                LOGGER.log(Level.WARNING, "", e);
            }
        }
        return null;
    }

    @NonNull
    private static List<Config> getConfigs(@NonNull final List<String> configIds) {
        List<Config> ret = new ArrayList<>();
        for (final String id : configIds) {
            final Config cfg = ConfigFiles.getByIdOrNull(Jenkins.get(), id);
            if (null != cfg) {
                ret.add(cfg);
            }
        }
        return ret;
    }

    /* cloudbase-init is buggy in recognizing mime-multipart
     * messages if they do not start with "Content-Type:..."
     * Therefore, we move the mandatory mime version header
     * after the Content-Type header.
     */
    private static byte[] injectMimeVersion(@NonNull final byte[] msg) {
        String[] lines = new String(msg, StandardCharsets.UTF_8).split("\n");
        if (3 < lines.length && lines[0].contains("multipart")) {
            String[] ret = new String[lines.length + 1];
            System.arraycopy(lines, 0, ret, 0, 2);
            System.arraycopy(lines, 2, ret, 3, lines.length - 2);
            ret[2] = "MIME-Version: 1.0";
            return Joiner.on("\n").join(Arrays.asList(ret)).getBytes(StandardCharsets.UTF_8);
        }
        return msg;
    }

    @CheckForNull
    public static byte[] buildUserData(
            @NonNull final List<String> configIds, @Nullable Map<String, String> replacements, boolean gzip)
            throws IOException {
        List<Config> configs = getConfigs(configIds);
        if (configs.isEmpty()) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (OutputStream os = gzip ? new GZIPOutputStream(baos) : baos) {
                if (configs.size() > 1) {
                    try {
                        final MimeMessage msg = new MimeMessage((Session) null);
                        final Multipart multipart = new MimeMultipart();
                        for (final Config cfg : configs) {
                            BodyPart body = buildBody(cfg, replacements);
                            if (null != body) {
                                multipart.addBodyPart(body);
                            }
                        }
                        msg.setContent(multipart);
                        // Yet another nested stream, as workaround for cloudbase-init
                        try (ByteArrayOutputStream tmpbaos = new ByteArrayOutputStream()) {
                            msg.writeTo(tmpbaos, new String[] {"Date", "Message-ID", "MIME-Version"});
                            os.write(injectMimeVersion(tmpbaos.toByteArray()));
                        }
                    } catch (IOException | MessagingException e) {
                        LOGGER.log(Level.WARNING, "", e);
                    }
                } else {
                    Config cfg = configs.get(0);
                    if (null != cfg.content && !cfg.content.isEmpty()) {
                        os.write(cfg.content.getBytes(StandardCharsets.UTF_8));
                    } else {
                        return null;
                    }
                }
            }
            return baos.toByteArray();
        }
    }

    private static void appendItemsOfProvider(
            @NonNull ConfigProvider p, @NonNull ListBoxModel m, @Nullable String currentValue) {
        StringBuffer sb = new StringBuffer();
        ConfigSuitableFor a = p.getClass().getAnnotation(ConfigSuitableFor.class);
        if (null != a && a.target() == UserData.class) {
            for (Config cfg : ConfigFiles.getConfigsInContext(Jenkins.get(), p.getClass())) {
                sb.setLength(0);
                sb.append(p.getDisplayName()).append(" ").append(cfg.name);
                if (cfg.comment != null && !cfg.comment.isEmpty()) {
                    sb.append(" [").append(cfg.comment).append("]");
                }
                m.add(sb.toString(), cfg.id);
                if (cfg.id.equals(currentValue)) {
                    m.get(m.size() - 1).selected = true;
                }
            }
        }
    }

    @NonNull
    public static ListBoxModel doFillFileItems(@Nullable final String currentValue) {
        ListBoxModel m = new ListBoxModel();
        m.add("- none -", "");
        for (ConfigProvider p : ConfigProvider.all()) {
            appendItemsOfProvider(p, m, currentValue);
        }
        return m;
    }

    @NonNull
    public static ListBoxModel doFillInitScriptItems(@Nullable final String currentValue) {
        ListBoxModel m = new ListBoxModel();
        m.add("- none -", "");
        appendItemsOfProvider(new UserDataScript("-", "", "", "").getProvider(), m, currentValue);
        return m;
    }

    public static Map<String, String> getUserDataHashes(List<String> configIds) throws NoSuchAlgorithmException {
        return getUserDataHashesFromConfigs(getConfigs(configIds));
    }

    public static Map<String, String> getUserDataHashesFromConfigs(List<Config> cfgs) throws NoSuchAlgorithmException {
        HexFormat hex = HexFormat.of();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Map<String, String> ret = new HashMap<>();
        for (Config cfg : cfgs) {
            DataSource ds = new ConfigDataSource(cfg, false, Map.of());
            String content = null == cfg.content ? "" : cfg.content;
            md.update(ds.getContentType().getBytes(StandardCharsets.UTF_8));
            md.update(cfg.name.getBytes(StandardCharsets.UTF_8));
            md.update(cfg.comment.getBytes(StandardCharsets.UTF_8));
            String hash = hex.formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
            ret.put(cfg.id, hash);
        }
        return ret;
    }

    public static List<Config> getJCloudsConfigs() {
        List<Config> cfgs = new ArrayList<>();
        for (ConfigProvider p : ConfigProvider.all()) {
            ConfigSuitableFor a = p.getClass().getAnnotation(ConfigSuitableFor.class);
            if (null != a && a.target() == UserData.class) {
                for (Config cfg : ConfigFiles.getConfigsInContext(Jenkins.get(), p.getClass())) {
                    cfgs.add(cfg);
                }
            }
        }
        return cfgs;
    }

    public static String exportXml() {
        return Jenkins.XSTREAM.toXML(getJCloudsConfigs());
    }
}
