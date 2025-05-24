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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Updates an existing JCloudsCloud by reading stdin as a configuration XML file.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsUpdateCloudCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.UpdateCloudCommand_shortDescription();
    }

    @Argument(metaVar = "NAME", usage = "Name of the profile to update.", required = true)
    public String name;

    @Option(
            required = false,
            name = "-v",
            aliases = "--verbose",
            usage = "Be verbose when validating references to credentials.")
    private boolean verbose;

    @Option(
            required = false,
            name = "-d",
            aliases = "--delete-templates",
            usage = "Delete templates of target profile.")
    private boolean delete;

    @Option(required = false, name = "-k", aliases = "--keep-templates", usage = "Keep templates of target profile.")
    private boolean keep;

    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JCloudsCloud oc = CliHelper.resolveCloud(name, false);
        if (null == oc) {
            throw new IllegalStateException("JClouds cloud profile '" + name + "' does not exist");
        }

        String xml = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
        JCloudsCloud nc = null;
        try {
            nc = (JCloudsCloud) Jenkins.XSTREAM.fromXML(xml);
        } catch (XStreamException e) {
            throw new IllegalStateException("Unable to parse input: " + e.toString());
        }
        if (!nc.name.equals(name) && null != Jenkins.get().clouds.getByName(nc.name)) {
            throw new IllegalStateException(
                    String.format("Unable to rename cloud profile: A cloud with the name %s already exists", nc.name));
        }
        if (nc.getTemplates().size() == 0 && oc.getTemplates().size() > 0 && !keep && !delete) {
            throw new IllegalStateException(
                    "Unable to update " + name + ": Need --delete-templates or --keep-templates");
        }
        PrintStream devnull = CliHelper.getDevNull();
        CliHelper.validateCloudCredentials(nc, xml, verbose ? stdout : devnull);
        for (JCloudsSlaveTemplate tpl : nc.getTemplates()) {
            CliHelper.validateTemplate(tpl, xml, verbose ? stdout : devnull);
            tpl.setCloud(nc);
        }
        if (keep) {
            nc.setTemplates(oc.getTemplates());
        }
        Jenkins.get().clouds.replace(oc, nc);
        return 0;
    }
}
