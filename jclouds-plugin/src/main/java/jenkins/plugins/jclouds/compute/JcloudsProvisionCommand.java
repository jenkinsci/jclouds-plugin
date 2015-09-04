package jenkins.plugins.jclouds.compute;

import java.io.IOException;
// import jenkins.model.Jenkins;
import hudson.cli.CLICommand;
import hudson.Extension;

import org.kohsuke.args4j.Argument;

/**
 * Provisions a slave.
 *
 * @author Fritz Elfert
 */
@Extension
public class JcloudsProvisionCommand extends CLICommand {

    @Argument(required = true, metaVar = "PROFILE", usage = "Name of jcloud profile to use")
        public String profile;

    @Argument(required = true, metaVar = "TEMPLATE", usage = "Name of template to use")
        public String tmpl;

    @Override
    public String getShortDescription() {
        return Messages.ProvisionCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException {
        stdout.println("Not yet");
        return 0;
    }

}
