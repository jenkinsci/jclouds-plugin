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
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;
import org.kohsuke.args4j.Argument;

/**
 * Copies an existing JClouds template, to the same cloud with a different name.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsCopyTemplateCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.CopyTemplateCommand_shortDescription();
    }

    @Argument(metaVar = "FROM-TEMPLATE", index = 0, usage = "Name of the existing template to copy.", required = true)
    public String from;

    @Argument(metaVar = "TO-TEMPLATE", index = 1, usage = "Name of the new template to create.", required = true)
    public String to;

    @Argument(
            required = false,
            metaVar = "PROFILE",
            index = 2,
            usage = "Name of jclouds profile. Required, if FROM-TEMPLATE is ambiguous.")
    public String profile = null;

    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JCloudsCloud c = CliHelper.resolveCloud(profile, true);
        if (from.equals(to)) {
            throw new IllegalStateException("Cannot copy template to itself");
        }
        final JCloudsSlaveTemplate tplFrom = CliHelper.resolveTemplate(c, from);
        c = tplFrom.getCloud();
        if (null != c.getTemplate(to)) {
            throw new IllegalStateException("Template '" + to + "' already exists");
        }
        String xml = Jenkins.XSTREAM.toXML(tplFrom);
        // Not great, but template name is final
        xml = xml.replaceFirst("<name>.*</name>", "<name>" + to + "</name>");
        try {
            JCloudsSlaveTemplate tplTo = (JCloudsSlaveTemplate) Jenkins.XSTREAM.fromXML(xml);
            c.addTemplate(tplTo);
        } catch (XStreamException e) {
            throw new IllegalStateException("Unable to copy " + e.toString());
        }
        return 0;
    }
}
