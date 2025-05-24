/*
 * Copyright 2010-2016 Adrian Cole, Andrew Bayer, Fritz Elfert, Marat Mavlyutov, Monty Taylor, Vijay Kiran et. al.
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
import hudson.slaves.Cloud;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlave;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;
import org.jclouds.compute.domain.NodeMetadata;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

/**
 * Provisions a slave.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsProvisionCommand extends CLICommand {

    enum OutputFormat {
        HUMAN,
        JSON,
        PROPERTIES
    }

    @Argument(
            required = false,
            metaVar = "PROFILE",
            index = 1,
            usage = "Name of jclouds profile to use. Mandatory, if the TEMPLATE is ambiguous.")
    public String profile = null;

    @Argument(required = true, metaVar = "TEMPLATE", index = 0, usage = "Name of template to use.")
    public String tmpl;

    @Option(
            required = false,
            name = "-f",
            aliases = "--format",
            usage = "Output format of provisioned agent properties.")
    public OutputFormat format = OutputFormat.HUMAN;

    @Override
    public String getShortDescription() {
        return Messages.ProvisionCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException, CmdLineException {
        Jenkins.get().checkPermission(Jenkins.READ);
        JCloudsCloud c = CliHelper.resolveCloud(profile, true);
        final JCloudsSlaveTemplate tpl = CliHelper.resolveTemplate(c, tmpl);
        if (null == c) {
            c = tpl.getCloud();
        }
        c.checkPermission(Cloud.PROVISION);
        if (c.getRunningNodesCount() < c.instanceCap) {
            final JCloudsSlave s = c.doProvisionFromTemplate(tpl);
            final NodeMetadata nmd = s.getNodeMetaData();
            final Set<String> a = new HashSet<>();
            a.addAll(nmd.getPrivateAddresses());
            a.addAll(nmd.getPublicAddresses());
            String allAddrs;
            switch (format) {
                case HUMAN:
                    allAddrs = a.toString().replaceAll("^\\[|\\]$", "");
                    stdout.println("Provisioned node " + s.getNodeName() + " with Address(es) " + allAddrs);
                    break;
                case JSON:
                    allAddrs = a.toString().replaceAll("^\\[|\\]$", "").replaceAll(", ", "\", \"");
                    stdout.println("{ \"name\": \"" + s.getNodeName() + "\", \"addr\": [\"" + allAddrs + "\"] }");
                    break;
                case PROPERTIES:
                    stdout.println("name=" + s.getNodeName());
                    for (final String addr : a) {
                        stdout.println("addr=" + addr);
                    }
                    break;
            }
        } else {
            throw new CmdLineException(null, CliMessages.INSTANCE_CAP_REACHED, profile, tmpl);
        }
        return 0;
    }
}
