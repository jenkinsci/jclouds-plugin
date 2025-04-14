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

import com.thoughtworks.xstream.XStreamException;

import hudson.Extension;
import hudson.cli.CLICommand;

import java.nio.charset.StandardCharsets;

import jenkins.model.Jenkins;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;

/**
 * Creates a new JCloudsCloud by reading stdin as a configuration XML file.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsUpdateTemplateCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.UpdateTemplateCommand_shortDescription();
    }

    @Argument(required = false, metaVar = "PROFILE", index = 1, usage = "Name of jclouds profile to use. Reqired, if TEMPLATE is ambiguous.")
    public String profile = null;

    @Argument(metaVar = "TEMPLATE", usage = "Name of the existing template to update.", required = true)
    public String name;

    @Option(required = false, name = "-v", aliases = "--verbose", usage = "Be verbose when validating references to credentials and config files.")
    private boolean verbose;

    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JCloudsCloud c = CliHelper.resolveCloud(profile, true);
        final JCloudsSlaveTemplate otpl = CliHelper.resolveTemplate(c, name);

        String xml = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
        JCloudsSlaveTemplate ntpl = null;
        try {
            ntpl = (JCloudsSlaveTemplate)Jenkins.XSTREAM.fromXML(xml);
        } catch (XStreamException e) {
            throw new IllegalStateException("Unable to parse input: " + e.toString());
        }
        if (!ntpl.name.equals(name) && null != c.getTemplate(ntpl.name)) {
            throw new IllegalStateException(
                    String.format("Unable to rename template: A template with the name %s already exists", ntpl.name));
        }
        CliHelper.validateTemplate(ntpl, xml, verbose ? stdout : CliHelper.getDevNull());
        c.replaceTemplate(otpl, ntpl);
        return 0;
    }
}
