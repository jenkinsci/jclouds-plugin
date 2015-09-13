package jenkins.plugins.jclouds.compute;

import java.io.IOException;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

import shaded.com.google.common.base.Strings;

/**
 * Provisions a slave.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsTemplatesCommand extends CLICommand {
    final static String PROFILE = "Profile";
    final static String NAME = "Name";

    @Override
    public String getShortDescription() {
        return Messages.TemplatesCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException {
        int maxProfileLen = 0;
        int maxTemplateLen = 0;
        for (final Cloud cloud : Jenkins.getActiveInstance().clouds) {
            if (cloud instanceof JCloudsCloud) {
                final JCloudsCloud c = (JCloudsCloud)cloud;
                if (c.profile.length() > maxProfileLen) {
                    maxProfileLen = c.profile.length();
                }
                for (final JCloudsSlaveTemplate t : c.getTemplates()) {
                    if (t.name.length() > maxTemplateLen) {
                        maxTemplateLen = t.name.length();
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
            final String fmt = "%-" + maxProfileLen + "s %-" + maxTemplateLen + "s %s%n";
            stdout.printf(fmt, PROFILE, NAME, "Description");
            stdout.println(Strings.padEnd("",  maxProfileLen + maxTemplateLen + 13, '='));
            final String indent = Strings.padEnd("\n",  maxProfileLen + maxTemplateLen + 3, ' ');
            for (final Cloud cloud : Jenkins.getActiveInstance().clouds) {
                if (cloud instanceof JCloudsCloud) {
                    final JCloudsCloud c = (JCloudsCloud)cloud;
                    for (final JCloudsSlaveTemplate t : c.getTemplates()) {
                        stdout.printf(fmt, c.profile, t.name, t.description.replaceAll("\n", indent));
                    }
                }
            }
        }
        return 0;
    }

}
