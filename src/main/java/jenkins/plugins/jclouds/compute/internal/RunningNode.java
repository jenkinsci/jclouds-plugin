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
package jenkins.plugins.jclouds.compute.internal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;

import jenkins.model.Jenkins;

import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate;
import jenkins.plugins.jclouds.compute.JCloudsLauncher;

public class RunningNode implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String cloud;
    private final String template;
    private final boolean shouldSuspend;
    private String nodeId = null;
    private String nodeName = null;
    private String nodeInstanceAddress = null;
    private transient JCloudsNodeMetadata node;

    public RunningNode(String cloud, String template, boolean shouldSuspend, JCloudsNodeMetadata node) {
        this.cloud = cloud;
        this.template = template;
        this.shouldSuspend = shouldSuspend;
        this.node = node;
        copyMetadata(null);
    }

    private void copyMetadata(PrintStream logger) {
        if (null != node) {
            nodeId = node.getId();
            nodeName = node.getName();
            String preferredAddress = null;
            // JCloudsCloud c = JCloudsCloud.getByName(cloud);
            JCloudsCloud c = (JCloudsCloud) Jenkins.get().clouds.getByName(cloud);
            if (null != c) {
                for (JCloudsSlaveTemplate t : c.getTemplates()) {
                    if (t.name.equals(template)) {
                        preferredAddress = t.getPreferredAddress();
                        break;
                    }
                }
            }
            nodeInstanceAddress = JCloudsLauncher.getConnectionAddress(node, logger, preferredAddress);
        }
    }

    private void writeObject(final ObjectOutputStream oos) throws IOException {
        copyMetadata(null);
        oos.defaultWriteObject();
    }

    private void readObject(final ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.node = null;
    }

    public String getNodeId() {
        copyMetadata(null);
        return nodeId;
    }

    public String getNodeName() {
        copyMetadata(null );
        return nodeName;
    }

    public String getNodeInstanceAddress(PrintStream logger) {
        copyMetadata(logger);
        return nodeInstanceAddress;
    }

    public String getCloudName() {
        return cloud;
    }

    public String getTemplateName() {
        return template;
    }

    public boolean getShouldSuspend() {
        return shouldSuspend;
    }
}
