/*
 * Copyright 2016 Fritz Elfert
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
package jenkins.plugins.jclouds.config;

import org.jenkinsci.lib.configprovider.model.ContentType;

/**
 * Defines the possible mime types of JClouds user-data and their <a href="https://github.com/stapler/stapler-adjunct-codemirror/tree/master/src/main/resources/org/kohsuke/stapler/codemirror/mode">codeMirror</a> mode.
 * See <a href="http://cloudinit.readthedocs.io/en/latest/topics/format.html">cloud-init formats</a>
 */
public enum CloudInitContentType implements ContentType {
    CLOUDCONFIG("yaml", "text/cloud-config"),
    SHELL("shell", "text/x-shellscript"),
    INCLUDE("html", "text/x-include-url"),
    INCLUDEONCE("html", "text/x-include-once-url"),
    BOOTHOOK("shell", "text/cloud-boothook"),
    UPSTART("shell", "text/upstart-job"),
    PARTHANDLER("python", "text/part-handler");

    private final String codeMirrorMode;
    private final String mimetype;

    private CloudInitContentType(final String mode, final String mime) {
        codeMirrorMode = mode;
        mimetype = mime;
    }

    /**
     * {@inheritDoc}
     */
    public String getCmMode() {
        return codeMirrorMode;
    }

    /**
     * {@inheritDoc}
     */
    public String getMime() {
        return mimetype;
    }
}
