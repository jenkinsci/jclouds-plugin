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
import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Creates a new JCloudsCloud by reading stdin as a configuration XML file.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsCreateTemplateCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.CreateTemplateCommand_shortDescription();
    }

    @Argument(
            required = false,
            metaVar = "PROFILE",
            index = 1,
            usage = "Name of destination jclouds profile. Required, if multiple profiles exist.")
    public String profile = null;

    @Argument(metaVar = "NAME", usage = "Name of the new template to create.", required = true)
    public String name;

    @Option(
            required = false,
            name = "-v",
            aliases = "--verbose",
            usage = "Be verbose when validating references to credentials and config files.")
    private boolean verbose;

    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (CliHelper.getAllJCloudClouds().size() == 0) {
            throw new IllegalStateException("At least one jclouds profile must exist to create a template");
        }
        JCloudsCloud c = CliHelper.resolveCloud(profile, true);
        if (null == c) {
            if (null == profile && CliHelper.getAllJCloudClouds().size() > 1) {
                throw new IllegalStateException("More than one JCloudsCloud exists. Please specify target profile");
            }
            return 0;
        } else if (null != c.getTemplate(name)) {
            throw new IllegalStateException("Template '" + name + "' already exists");
        }

        String xml = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
        // Not great, but template name is final
        xml = xml.replaceFirst("<name>.*</name>", "<name>" + name + "</name>");
        JCloudsSlaveTemplate tpl = null;
        try {
            tpl = (JCloudsSlaveTemplate) Jenkins.XSTREAM.fromXML(xml);
        } catch (XStreamException e) {
            throw new IllegalStateException("Unable to parse input: " + e.toString());
        }
        CliHelper.validateTemplate(tpl, xml, verbose ? stdout : CliHelper.getDevNull());
        c.addTemplate(tpl);
        return 0;
    }
}
