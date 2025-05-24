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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.config.ConfigExport;
import jenkins.plugins.jclouds.config.ConfigHelper;
import jenkins.plugins.jclouds.config.UserDataBoothook;
import jenkins.plugins.jclouds.config.UserDataInclude;
import jenkins.plugins.jclouds.config.UserDataIncludeOnce;
import jenkins.plugins.jclouds.config.UserDataPartHandler;
import jenkins.plugins.jclouds.config.UserDataScript;
import jenkins.plugins.jclouds.config.UserDataUpstart;
import jenkins.plugins.jclouds.config.UserDataYaml;
import jenkins.plugins.jclouds.internal.CryptoHelper;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFileStore;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

/**
 * Imports an all jclouds UserData from XML supplied on stdin
 *
 * @author Fritz Elfert
 */
@Extension
public class JCloudsCreateUserdataCommand extends CLICommand {

    private static final String REPL_FMT = "  <replacement old=\"%s\" new=\"%s\"/>%n";

    @Option(
            name = "--overwrite",
            forbids = {"--merge"},
            usage = "Overwrite existing userdata files.")
    private boolean overwrite;

    @Option(
            name = "--merge",
            forbids = {"--overwrite"},
            usage =
                    "Generate new Ids for imported userdata files if the id already exists and references different user data.")
    private boolean merge;

    @Argument(
            required = false,
            metaVar = "CREDENTIAL",
            usage = "ID of credential (Must be an RSA SSH credential) to encrypt data. Default: Taken from input XML.")
    private String cred = null;

    @Override
    public String getShortDescription() {
        return Messages.CreateUserdataCommand_shortDescription();
    }

    @Override
    protected int run() throws IOException, CmdLineException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String xml = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
        ConfigExport ce;
        try {
            ce = (ConfigExport) Jenkins.XSTREAM.fromXML(xml);
        } catch (XStreamException e) {
            throw new IllegalStateException("Unable to parse input: " + e.toString());
        }
        List<Config> cfgs = ce.configData;
        if (null != ce.getEncryptedConfigData()) {
            if (null == cred) {
                cred = ce.credentialsId;
            }
            CryptoHelper ch = new CryptoHelper(cred);
            xml = ch.decrypt(ce.getEncryptedConfigData());
            try {
                cfgs = (List<Config>) Jenkins.XSTREAM.fromXML(xml);
            } catch (XStreamException e) {
                throw new IllegalStateException("Unable to parse input: " + e.toString());
            }
        }

        if (merge) {
            try {
                Map<String, String> existingHashes =
                        ConfigHelper.getUserDataHashesFromConfigs(ConfigHelper.getJCloudsConfigs());
                Map<String, String> newHashes = ConfigHelper.getUserDataHashesFromConfigs(cfgs);
                // Merge goes as follows:
                // For each of the new configs to import (cfgs)
                //   - if the same id exists in the existing configs and hashes are equal:
                //     Do nothing (Element does not need to be imported)
                //   - if the same id exists in the existing configs and hashes are different:
                //     Generate duplicate entry with a new UUID, add that to newCfgs and record the
                //     change in replacements.
                //   - if the same id does not exist in the existing configs, but the hash is equal to
                //     one of the existing configs, then that config does not need to be imported, but
                //     record the replacement from importedId to existingId
                //   - if the same id does not exist in the existing configs and the hash does not yet
                //     exist, just add it to newCfgs (regular import without replacement).
                List<Config> newCfgs = new ArrayList<>();
                StringBuilder repl = new StringBuilder();
                for (Config cfg : cfgs) {
                    String oHash = existingHashes.get(cfg.id);
                    String nHash = newHashes.get(cfg.id);
                    if (null != oHash && oHash.equals(nHash)) {
                        continue;
                    }
                    if (null != oHash && !oHash.equals(nHash)) {
                        Config ncfg = dupCfg(cfg);
                        newCfgs.add(ncfg);
                        repl.append(String.format(REPL_FMT, cfg.id, ncfg.id));
                        continue;
                    }
                    if (null == oHash) {
                        for (Map.Entry<String, String> entry : existingHashes.entrySet()) {
                            if (entry.getValue().equals(nHash)) {
                                repl.append(String.format(REPL_FMT, cfg.id, entry.getKey()));
                                break;
                            }
                        }
                        continue;
                    }
                    newCfgs.add(cfg);
                }
                if (!repl.isEmpty()) {
                    repl.insert(0, String.format("<replacements>%n")).append("</replacements>");
                    stdout.println(repl.toString());
                }
                cfgs = newCfgs;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Could not calculate hashes for userdata");
            }
        }

        if (!overwrite) {
            StringBuilder sb = new StringBuilder();
            // Check if any of the configs already exists
            for (Config cfg : cfgs) {
                if (null != ConfigFiles.getByIdOrNull(Jenkins.get(), cfg.id)) {
                    sb.append(String.format("Config data with id %s already exists%n", cfg.id));
                }
            }
            if (sb.length() > 0) {
                throw new IllegalStateException(sb.toString());
            }
        }

        // Save the actual data
        ConfigFileStore store = GlobalConfigFiles.get();
        for (Config cfg : cfgs) {
            store.save(cfg);
        }
        return 0;
    }

    private Config dupCfg(Config cfg) {
        Config ncfg = null;
        if (cfg instanceof UserDataBoothook) {
            ncfg = ((UserDataBoothook) cfg).dup();
        }
        if (cfg instanceof UserDataInclude) {
            ncfg = ((UserDataInclude) cfg).dup();
        }
        if (cfg instanceof UserDataIncludeOnce) {
            ncfg = ((UserDataIncludeOnce) cfg).dup();
        }
        if (cfg instanceof UserDataPartHandler) {
            ncfg = ((UserDataPartHandler) cfg).dup();
        }
        if (cfg instanceof UserDataScript) {
            ncfg = ((UserDataScript) cfg).dup();
        }
        if (cfg instanceof UserDataUpstart) {
            ncfg = ((UserDataUpstart) cfg).dup();
        }
        if (cfg instanceof UserDataYaml) {
            ncfg = ((UserDataYaml) cfg).dup();
        }
        if (null == ncfg) {
            throw new IllegalStateException(
                    String.format("Invalid config type! Please report BUG at %s", Messages.BUGURL()));
        }
        return ncfg;
    }
}
