/*
 * Copyright 2023 Fritz Elfert
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
package jenkins.plugins.jclouds.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.PrintStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.slaves.Cloud;
import hudson.util.EditDistance;

import jenkins.model.Jenkins;

import org.kohsuke.args4j.CmdLineException;

import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;
import jenkins.plugins.jclouds.config.ConfigHelper;
import jenkins.plugins.jclouds.internal.CredentialsHelper;

class CliHelper {

    static public final String XML_HEADER = "<?xml version='1.1' encoding='UTF-8'?>";

    static public List<JCloudsCloud> getAllJCloudClouds() {
        List<JCloudsCloud> ret = new ArrayList<>();
        for (Cloud c : Jenkins.get().clouds) {
            if (c instanceof JCloudsCloud) {
                ret.add((JCloudsCloud)c);
            }
        }
        return ret;
    }

    @SuppressFBWarnings(value={"DM_DEFAULT_ENCODING"}, justification="Dont care for nullOutputStream")
    static public PrintStream getDevNull() {
        return new PrintStream(PrintStream.nullOutputStream());
    }

    static public JCloudsCloud resolveCloud(String name, boolean optional) throws CmdLineException {
        if (null != name) {
            final Jenkins.CloudList cl = Jenkins.get().clouds;
            final Cloud c = cl.getByName(name);
            if (null != c && c instanceof JCloudsCloud) {
                return (JCloudsCloud)c;
            }
            final List<String> names = new ArrayList<>();
            for (final Cloud cloud : Jenkins.get().clouds) {
                if (cloud instanceof JCloudsCloud) {
                    String n = ((JCloudsCloud)cloud).profile;
                    if (n.length() > 0) {
                        names.add(n);
                    }
                }
            }
            throw new CmdLineException(null, CliMessages.NO_SUCH_PROFILE_EXISTS,
                name, EditDistance.findNearest(name, names));
        } else {
            if (optional) {
                List<JCloudsCloud> cl = getAllJCloudClouds();
                if (cl.size() == 1) {
                    return cl.get(0);
                }
            }
        }
        return null;
    }

    static public List<String> getAllTemplateNames(JCloudsCloud cloud) {
        final List<String> ret = new ArrayList<>();
        if (null == cloud) {
            for (final Cloud c : Jenkins.get().clouds) {
                if (c instanceof JCloudsCloud) {
                    final JCloudsCloud jc = (JCloudsCloud)c;
                    for (final JCloudsSlaveTemplate t : jc.getTemplates()) {
                        if (!ret.contains(t.name)) {
                            ret.add(t.name);
                        }
                    }
                }
            }
            return ret;
        }
        for (final JCloudsSlaveTemplate t : cloud.getTemplates()) {
            ret.add(t.name);
        }
        return ret;
    }

    static public JCloudsSlaveTemplate resolveTemplate(JCloudsCloud cloud, String name) throws CmdLineException {
        JCloudsSlaveTemplate ret = null;
        if (null == cloud) {
            for (final Cloud c : Jenkins.get().clouds) {
                if (c instanceof JCloudsCloud) {
                    final JCloudsCloud jc = (JCloudsCloud)c;
                    JCloudsSlaveTemplate t = jc.getTemplate(name);
                    if (null != t) {
                        if (null != ret) {
                            throw new CmdLineException(null, CliMessages.AMBIGUOUS_TEMPLATE, name);
                        }
                        ret = t;
                    }
                }
            }
        } else {
            ret = cloud.getTemplate(name);
        }
        if (null == ret) {
            List<String> names = getAllTemplateNames(cloud);
            throw new CmdLineException(null, CliMessages.NO_SUCH_TEMPLATE_EXISTS, name, EditDistance.findNearest(name, names));
        }
        return ret;
    }

    static public String getHashAttribute(String xml, String tag) {
        Pattern p = Pattern.compile(String.format("(?s).*?<%s sha256=\"([0-9a-fA-F]+)\">.*", tag));
        Matcher m = p.matcher(xml);
        if (m.matches()) {
            return m.group(1);
        }
        return "";
    }

    static private String getHashAttribute(String xml, String tag, String id) {
        Pattern p = Pattern.compile(String.format("(?s).*?<%s sha256=\"([0-9a-fA-F]+)\">%s.*", tag, id));
        Matcher m = p.matcher(xml);
        if (m.matches()) {
            return m.group(1);
        }
        return "";
    }

    static public void validateTemplate(JCloudsSlaveTemplate tpl, String xml, PrintStream verbose) {
        validateTemplateCredentials(tpl, xml, verbose);
        validateUserData(tpl, xml, verbose);
    }

    static private void validateTemplateCredentials(JCloudsSlaveTemplate tpl, String xml, PrintStream verbose) {
        String id = tpl.getCredentialsId();
        if (null == CredentialsHelper.getCredentialsById(id)) {
            throw new IllegalStateException(String.format("credentialsId %s of template %s does not resolve", id, tpl.name));
        }
        try {
            String hash = CredentialsHelper.getCredentialsHash(id);
            String ohash = getHashAttribute(xml, "credentialsId", id);
            if (!ohash.isEmpty()) {
                if (! hash.equalsIgnoreCase(ohash)) {
                    throw new IllegalStateException(String.format("credentialsId %s of template %s resolves to a different credential",
                            id, tpl.name));
                }
                verbose.println(String.format("Validated credentialsId %s of template %s", id, tpl.name));
            } else {
                verbose.println(String.format("Found credentialsId %s of template %s", id, tpl.name));
            }
            id = tpl.getAdminCredentialsId();
            if (null != id) {
                if (null == CredentialsHelper.getCredentialsById(id)) {
                    throw new IllegalStateException(String.format("adminCredentialsId %s of template %s does not resolve",
                            id, tpl.name));
                }
                hash = CredentialsHelper.getCredentialsHash(id);
                ohash = getHashAttribute(xml, "adminCredentialsId", id);
                if (!ohash.isEmpty()) {
                    if (! hash.equalsIgnoreCase(ohash)) {
                        throw new IllegalStateException(
                                String.format("adminCredentialsId %s of template %s resolves to a different credential",
                                        id, tpl.name));
                    }
                    verbose.println(String.format("Validated adminCredentialsId %s of template %s", id, tpl.name));
                } else {
                    verbose.println(String.format("Found adminCredentialsId %s of template %s", id, tpl.name));
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not calculate hashes for credentials of template " + tpl.name);
        }
    }

    static private void validateUserData(JCloudsSlaveTemplate tpl, String xml, PrintStream verbose) {
        try {
            List<String> ids = tpl.getUserDataIds();
            Map<String, String> m = ConfigHelper.getUserDataHashes(ids);
            for (String id : ids) {
                String hash = m.get(id);
                if (null == hash) {
                    throw new IllegalStateException(String.format("fileId %s in template %s does not resolve", id, tpl.name));
                }
                String ohash = getHashAttribute(xml, "fileId", id);
                if (!ohash.isEmpty()) {
                    if (! hash.equalsIgnoreCase(ohash)) {
                        throw new IllegalStateException(String.format("fileId %s in template %s resolves to a different config file",
                                id, tpl.name));
                    }
                    verbose.println(String.format("Validated fileId %s of template %s", id, tpl.name));
                } else {
                    verbose.println(String.format("Found fileId %s of template %s", id, tpl.name));
                }
            }
            String sid = tpl.getInitScriptId();
            if (null != sid && !sid.isEmpty()) {
                m = ConfigHelper.getUserDataHashes(List.of(sid));
                String hash = m.get(sid);
                String ohash = getHashAttribute(xml, "initScriptId", sid);
                if (!ohash.isEmpty()) {
                    if (! hash.equalsIgnoreCase(ohash)) {
                        throw new IllegalStateException(String.format("initScriptId %s in template %s resolves to a different config file",
                                sid, tpl.name));
                    }
                    verbose.println(String.format("Validated initScriptId %s of template %s", sid, tpl.name));
                } else {
                    verbose.println(String.format("Found initScriptId %s of template %s", sid, tpl.name));
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not calculate hashes for userdata of template " + tpl.name);
        }
    }

    static public void validateCloudCredentials(JCloudsCloud c, String xml, PrintStream verbose) {
        String id = c.getCloudGlobalKeyId();
        if (null == CredentialsHelper.getCredentialsById(id)) {
            throw new IllegalStateException(String.format("cloudGlobalKeyId %s does not resolve", id));
        }
        try {
            String hash = CredentialsHelper.getCredentialsHash(id);
            String ohash = getHashAttribute(xml, "cloudGlobalKeyId");
            if (ohash.isEmpty()) {
                verbose.println(String.format("Found cloudGlobalKeyId %s of cloud %s", id, c.name));
            } else {
                if (! hash.equalsIgnoreCase(ohash)) {
                    throw new IllegalStateException(String.format("cloudGlobalKeyId %s resolves to a different credential", id));
                }
                verbose.println(String.format("Validated cloudGlobalKeyId %s of cloud %s", id, c.name));
            }
            id = c.getCloudCredentialsId();
            if (null == CredentialsHelper.getCredentialsById(id)) {
                throw new IllegalStateException(String.format("cloudCredentialsId %s does not resolve", id));
            }
            hash = CredentialsHelper.getCredentialsHash(id);
            ohash = getHashAttribute(xml, "cloudCredentialsId");
            if (ohash.isEmpty()) {
                verbose.println(String.format("Found cloudCredentialsId %s of cloud %s", id, c.name));
            } else {
                if (! hash.equalsIgnoreCase(ohash)) {
                    throw new IllegalStateException(String.format("cloudCredentialsId %s resolves to a different credential", id));
                }
                verbose.println(String.format("Validated cloudCredentialsId %s of cloud %s", id, c.name));
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not calculate hashes for credentials");
        }
    }

}
