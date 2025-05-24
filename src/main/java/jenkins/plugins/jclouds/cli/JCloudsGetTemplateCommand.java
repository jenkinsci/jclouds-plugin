/*
 * Copyright 2025 Fritz Elfert
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

import hudson.Extension;
import hudson.cli.CLICommand;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;
import jenkins.plugins.jclouds.config.ConfigHelper;
import jenkins.plugins.jclouds.internal.CredentialsHelper;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

/**
 * Exports an existing template to xml on stdout
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsGetTemplateCommand extends CLICommand {

    @Argument(
            required = false,
            metaVar = "PROFILE",
            index = 1,
            usage = "Name of jclouds profile to use. Required, if TEMPLATE is ambiguous.")
    public String profile = null;

    @Argument(required = true, metaVar = "TEMPLATE", index = 0, usage = "Name of template to export.")
    public String tmpl;

    @Option(required = false, name = "-r", aliases = "--replace", usage = "Read replacements as XML from stdin.")
    private boolean replace;

    @Override
    public String getShortDescription() {
        return Messages.GetTemplateCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException, CmdLineException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        final JCloudsCloud c = CliHelper.resolveCloud(profile, true);
        final JCloudsSlaveTemplate tpl = CliHelper.resolveTemplate(c, tmpl);
        String xml = replace(getXmlWithHashes(tpl));
        stdout.println(CliHelper.XML_HEADER);
        stdout.println(xml);
        return 0;
    }

    protected static String getXmlWithHashes(JCloudsSlaveTemplate tpl) {
        String xml = Jenkins.XSTREAM.toXML(tpl);
        try {
            String hash;
            String aid = tpl.getAdminCredentialsId();
            if (null != aid && aid.length() > 0) {
                hash = CredentialsHelper.getCredentialsHash(aid);
                xml = xml.replaceFirst(
                        "<adminCredentialsId>", String.format("<adminCredentialsId sha256=\"%s\">", hash));
            }
            hash = CredentialsHelper.getCredentialsHash(tpl.getCredentialsId());
            xml = xml.replaceFirst("<credentialsId>", String.format("<credentialsId sha256=\"%s\">", hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not calculate hashes for credentials");
        }
        try {
            xml = getUserDataHashes(tpl, xml);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not calculate hashes for userdata");
        }
        return xml;
    }

    private static String getUserDataHashes(JCloudsSlaveTemplate tpl, String xml) throws NoSuchAlgorithmException {
        List<String> ids = tpl.getUserDataIds();
        Map<String, String> m = ConfigHelper.getUserDataHashes(ids);
        for (String id : ids) {
            String hash = m.get(id);
            xml = xml.replaceFirst(String.format("<fileId>(%s)", id), String.format("<fileId sha256=\"%s\">$1", hash));
        }
        String sid = tpl.getInitScriptId();
        if (null != sid && !sid.isEmpty()) {
            m = ConfigHelper.getUserDataHashes(List.of(sid));
            xml = xml.replaceFirst(
                    String.format("<initScriptId>(%s)", sid),
                    String.format("<initScriptId sha256=\"%s\">$1", m.get(sid)));
        }
        return xml;
    }

    private String replace(String xml) throws IOException {
        if (replace) {
            String rxml = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
            xml = new Replacements(rxml).replace(xml);
        }
        return xml;
    }
}
