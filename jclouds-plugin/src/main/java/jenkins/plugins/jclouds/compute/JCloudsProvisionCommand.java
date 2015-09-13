package jenkins.plugins.jclouds.compute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.util.EditDistance;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

import org.jclouds.compute.domain.NodeMetadata;

/**
 * Provisions a slave.
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsProvisionCommand extends CLICommand {

    enum OutputFormat { HUMAN, JSON, PROPERTIES };

    @Argument(required = true, metaVar = "PROFILE", index = 0, usage = "Name of jclouds profile to use")
        public String profile;

    @Argument(required = true, metaVar = "TEMPLATE", index = 1, usage = "Name of template to use")
        public String tmpl;

    @Option(required = false, name = "-f", aliases = "--format", usage = "Output format of provisioned slave properties")
        public OutputFormat format = OutputFormat.HUMAN;

    @Override
    public String getShortDescription() {
        return Messages.ProvisionCommand_shortDescription();
    }

    private JCloudsCloud resolveCloud() throws CmdLineException {
        final Jenkins.CloudList cl = Jenkins.getActiveInstance().clouds;
        final Cloud c = cl.getByName(profile);
        if (null != c && c instanceof JCloudsCloud) {
            return (JCloudsCloud)c;
        }
        final List<String> names = new ArrayList<>();
        for (final Cloud cloud : Jenkins.getActiveInstance().clouds) {
            if (cloud instanceof JCloudsCloud) {
                String n = ((JCloudsCloud)cloud).profile;
                if (n.length() > 0) {
                    names.add(n);
                }
            }
        }
        throw new CmdLineException(null, Messages.JClouds_NoSuchProfileExists(profile, EditDistance.findNearest(profile, names)));
    }

    @Override
    protected int run() throws IOException, CmdLineException {
        final JCloudsCloud c = resolveCloud();
        c.checkPermission(Cloud.PROVISION);
        final JCloudsSlaveTemplate tpl = c.getTemplate(tmpl);
        if (null == tpl) {
            final List<String> names = new ArrayList<>();
            for (final JCloudsSlaveTemplate t : c.getTemplates()) {
                names.add(t.name);
            }
            throw new CmdLineException(null, Messages.JClouds_NoSuchTemplateExists(tmpl, EditDistance.findNearest(tmpl, names)));
        }
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
            throw new CmdLineException("Instance cap for this cloud is now reached for cloud profile: " + profile + " for template type " + tmpl);
        }
        return 0;
    }

}
