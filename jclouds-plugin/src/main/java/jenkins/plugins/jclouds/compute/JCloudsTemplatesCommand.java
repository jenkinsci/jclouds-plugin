package jenkins.plugins.jclouds.compute;

import java.io.IOException;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

/**
 * Provisions a slave.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsTemplatesCommand extends CLICommand {

    @Override
    public String getShortDescription() {
        return Messages.TemplatesCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException {
        for (final Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof JCloudsCloud) {
                final JCloudsCloud c = (JCloudsCloud)cloud;
                for (final JCloudsSlaveTemplate t : c.getTemplates()) {
                    stdout.println(c.profile + " " + t.name);
                }
            }
        }
        return 0;
    }

}
