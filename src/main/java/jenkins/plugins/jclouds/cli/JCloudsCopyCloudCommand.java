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

import org.kohsuke.args4j.Argument;

import jenkins.plugins.jclouds.compute.JCloudsCloud;

/**
 * Copies an existing JClouds cloud, to a different name.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsCopyCloudCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.CopyCloudCommand_shortDescription();
    }

    @Argument(metaVar = "FROM-CLOUD", index = 0, usage = "Name of the existing jclouds profile to copy.", required = true)
    public String from;

    @Argument(metaVar = "TO-CLOUD", index = 1, usage = "Name of the new jclouds profile to create.", required = true)
    public String to;

    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        final JCloudsCloud cFrom = CliHelper.resolveCloud(from, false);
        if (from.equals(to)) {
            throw new IllegalStateException("Cannot copy cloud to itself");
        }
        if (null != Jenkins.get().clouds.getByName(to)) {
            throw new IllegalStateException("Cloud '" + to + "' already exists");
        }
        String xml = Jenkins.XSTREAM.toXML(cFrom);
        // Not great, but template name is final
        xml = xml.replaceFirst("<name>.*</name>", "<name>" + to + "</name>");
        try {
            JCloudsCloud cTo = (JCloudsCloud)Jenkins.XSTREAM.fromXML(xml);
            Jenkins.get().clouds.add(cTo);
        } catch (XStreamException e) {
            throw new IllegalStateException("Unable to copy " + e.toString());
        }
        return 0;
    }
}
