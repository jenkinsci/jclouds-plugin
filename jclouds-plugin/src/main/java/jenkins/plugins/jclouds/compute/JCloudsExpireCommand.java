/*
 * Copyright 2018 Fritz Elfert
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
package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import jenkins.plugins.jclouds.internal.MsgMapper;

/**
 * Expires a running slave.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsExpireCommand extends CLICommand {

    @Argument(required = true, metaVar = "NODENAME", index = 0, usage = "The name of the node to be expired.")
    public String nodeName;

    @Override
    public String getShortDescription() {
        return Messages.ExpireCommand_shortDescription();
    }

    @Override
    protected int run() throws CmdLineException {
        Node n = Jenkins.get().getNode(nodeName);
        CmdLineParser p = getCmdLineParser();
        if (null != n) {
            n.checkPermission(Permission.CONFIGURE);
        } else {
            throw new CmdLineException(p, new MsgMapper(Messages.class, "_JClouds_NoSuchNodeExists"), nodeName);
        }
        Computer c = n.toComputer();
        if (JCloudsComputer.class.isInstance(c)) {
            final JCloudsSlave s = ((JCloudsComputer)c).getNode();
            if (null != s) {
                s.setOverrideRetentionTime(Integer.valueOf(0));
            } else {
                throw new CmdLineException(p, new MsgMapper(Messages.class, "_JClouds_NotFromJClouds"), nodeName);
            }
        } else {
            throw new CmdLineException(p, new MsgMapper(Messages.class, "_JClouds_NotFromJClouds"), nodeName);
        }
        return 0;
    }

}
