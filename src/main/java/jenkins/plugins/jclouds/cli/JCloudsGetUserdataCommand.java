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

import hudson.Extension;
import hudson.cli.CLICommand;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.config.ConfigExport;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.OptionHandler;

/**
 * Exports an all jclouds UserData to xml on stdout
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsGetUserdataCommand extends CLICommand {

    @Option(hidden = true, name = "--force", usage = "Force unencrypted export.")
    private boolean force;

    @Argument(
            required = true,
            metaVar = "CREDENTIAL",
            usage = "ID of credential (Must be a RSA SSH credential) to encrypt data.")
    public String cred = null;

    @Override
    public String getShortDescription() {
        return Messages.GetUserdataCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException, CmdLineException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        ConfigExport ce = new ConfigExport(force ? null : cred);
        stdout.println(CliHelper.XML_HEADER);
        stdout.println(ce.exportXml());
        return 0;
    }

    /* We override this and use our UsageHelper in order to
     * work around a bug in args4j which does not properly
     * honor the "hidden" flag in @Option and @Argument annotations.
     * The printSingleLineUsage method in the original shows
     * hidden option/arguments.
     */
    @Override
    protected void printUsage(PrintStream stderr, CmdLineParser p) {
        stderr.print("java -jar jenkins-cli.jar " + getName());
        new UsageHelper(p).printSingleLineUsage(stderr);
        stderr.println();
        printUsageSummary(stderr);
        p.printUsage(stderr);
    }

    private static class UsageHelper {
        private final List<OptionHandler> options;
        private final List<OptionHandler> arguments;
        private final ParserProperties parserProperties;

        public UsageHelper(CmdLineParser p) {
            options = p.getOptions();
            arguments = p.getArguments();
            parserProperties = p.getProperties();
        }

        public void printSingleLineUsage(PrintStream stderr) {
            for (OptionHandler h : arguments) {
                printSingleLineOption(stderr, h);
            }
            for (OptionHandler h : options) {
                printSingleLineOption(stderr, h);
            }
            stderr.flush();
        }

        private void printSingleLineOption(PrintStream out, OptionHandler h) {
            if (h.option.hidden()) {
                return;
            }
            out.print(' ');
            if (!h.option.required()) {
                out.print('[');
            }
            out.print(h.getNameAndMeta(null, parserProperties));
            if (h.option.isMultiValued()) {
                out.print(" ...");
            }
            if (!h.option.required()) {
                out.print(']');
            }
        }
    }
}
