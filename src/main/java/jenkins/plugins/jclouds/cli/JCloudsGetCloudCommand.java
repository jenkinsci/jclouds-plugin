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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;

import hudson.Extension;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;
import jenkins.plugins.jclouds.internal.CredentialsHelper;

/**
 * Exports an existing jclouds cloud to xml on stdout
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsGetCloudCommand extends CLICommand {

    @Argument(required = true, metaVar = "PROFILE", usage = "Name of jclouds profile to use.")
    public String profile = null;

    @Option(required = false, name = "-f", aliases = "--full", usage = "Include all templates of this cloud in the export.")
    private boolean full;

    @Option(required = false, name = "-r", aliases = "--replace", usage = "Read replacements as XML from stdin.")
    private boolean replace;

    @Override
    public String getShortDescription() {
        return Messages.GetCloudCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException, CmdLineException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        final JCloudsCloud c = CliHelper.resolveCloud(profile, false);
        String xml = Jenkins.XSTREAM.toXML(c);
        try {
            String hash = CredentialsHelper.getCredentialsHash(c.getCloudGlobalKeyId());
            xml = xml.replaceAll("<cloudGlobalKeyId>", String.format("<cloudGlobalKeyId sha256=\"%s\">", hash));
            hash = CredentialsHelper.getCredentialsHash(c.getCloudCredentialsId());
            xml = xml.replaceAll("<cloudCredentialsId>", String.format("<cloudCredentialsId sha256=\"%s\">", hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not calculate hashes for credentials");
        }
        if (full) {
            StringBuffer sb = new StringBuffer();
            for (JCloudsSlaveTemplate tpl : c.getTemplates()) {
                sb.append(JCloudsGetTemplateCommand.getXmlWithHashes(tpl)).append("\n");
            }
            sb = new StringBuffer(sb.toString().replace("\n", "\n    "));
            sb.insert(0, "<templates>\n    ").replace(sb.length() - 2, sb.length(), "</templates>");
            xml = xml.replaceFirst("(?s)<templates>.*</templates>", Matcher.quoteReplacement(sb.toString()));
        } else {
            xml = xml.replaceFirst("(?s)<templates>.*</templates>", "<templates/>");
        }
        xml = replace(xml);
        stdout.println(CliHelper.XML_HEADER);
        stdout.println(xml);
        return 0;
    }

    private String replace(String xml) throws IOException {
        if (replace) {
            String rxml = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
            xml = new Replacements(rxml).replace(xml);
        }
        return xml;
    }
}
