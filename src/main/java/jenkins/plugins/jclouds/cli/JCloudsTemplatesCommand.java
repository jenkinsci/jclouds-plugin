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
import hudson.security.Permission;
import java.io.IOException;
import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;

/**
 * Provisions an agent.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsTemplatesCommand extends CLICommand {
    static final String PROFILE = "Profile";
    static final String LABEL = "Label";
    static final String NAME = "Name";

    @Override
    public String getShortDescription() {
        return Messages.TemplatesCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException {
        int maxProfileLen = 0;
        int maxTemplateLen = 0;
        int maxLabelLen = 0;
        for (final JCloudsCloud c : CliHelper.getAllJCloudClouds()) {
            if (c.hasPermission(Permission.READ)) {
                if (c.profile.length() > maxProfileLen) {
                    maxProfileLen = c.profile.length();
                }
                for (final JCloudsSlaveTemplate t : c.getTemplates()) {
                    if (t.name.length() > maxTemplateLen) {
                        maxTemplateLen = t.name.length();
                    }
                    if (t.labelString.length() > maxLabelLen) {
                        maxLabelLen = t.labelString.length();
                    }
                }
            }
        }

        if (maxTemplateLen > 0) {
            if (PROFILE.length() > maxProfileLen) {
                maxProfileLen = PROFILE.length();
            }
            if (NAME.length() > maxTemplateLen) {
                maxTemplateLen = NAME.length();
            }
            if (LABEL.length() > maxTemplateLen) {
                maxTemplateLen = LABEL.length();
            }
            final String fmt = "%-" + maxProfileLen + "s %-" + maxTemplateLen + "s %-" + maxLabelLen + "s %s%n";
            stdout.printf(fmt, PROFILE, NAME, LABEL, "Description");
            stdout.println(String.format("%-" + (maxProfileLen + maxTemplateLen + maxLabelLen + 14) + "s", " ")
                    .replaceAll(" ", "="));
            final String indent = String.format("%-" + (maxProfileLen + maxTemplateLen + maxLabelLen + 4) + "s", "\n");
            for (final JCloudsCloud c : CliHelper.getAllJCloudClouds()) {
                for (final JCloudsSlaveTemplate t : c.getTemplates()) {
                    stdout.printf(fmt, c.profile, t.name, t.labelString, t.description.replaceAll("\n", indent));
                }
            }
        }
        return 0;
    }
}
