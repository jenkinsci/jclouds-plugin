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
package jenkins.plugins.jclouds.internal;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

import org.jclouds.compute.domain.NodeMetadata;

import jenkins.plugins.jclouds.compute.JCloudsCloud;
import jenkins.plugins.jclouds.compute.JCloudsComputer;
import jenkins.plugins.jclouds.compute.JCloudsSlave;

import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Receives phone home hook from slave.
 *
 * @author Fritz Elfert
 */
@Extension
public class PhoneHomeWebHook implements UnprotectedRootAction {

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return URLNAME;
    }

    /**
     * Receives the webhook call.
     *
     * @param req The stapler request.
     * @param rsp The stapler response.
     */
    @RequirePOST
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) {

        String hostName = req.getParameter("hostname");
        if (null == hostName) {
            throw new IllegalArgumentException("Not intended to be browsed interactively (must specify hostname parameter)");
        }
        LOGGER.info("Received POST from " + hostName);
        // run in high privilege to see all the nodes anonymous users don't see.
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            for (final Computer c : Jenkins.get().getComputers()) {
                if (JCloudsComputer.class.isInstance(c)) {
                    final JCloudsSlave slave = ((JCloudsComputer) c).getNode();
                    if (null != slave) {
                        final NodeMetadata nmd = slave.getNodeMetaData();
                        if (null != nmd && nmd.getHostname().equals(hostName)) {
                            slave.setWaitPhoneHome(false);
                            return;
                        }
                    }
                }
            }
            for (Cloud c : Jenkins.get().clouds) {
                if (JCloudsCloud.class.isInstance(c) && ((JCloudsCloud)c).phoneHomeNotify(hostName)) {
                    return;
                }
            }
        }
    }

    public static final String URLNAME = "jclouds-phonehome";

    private static final Logger LOGGER = Logger.getLogger(PhoneHomeWebHook.class.getName());

    public static PhoneHomeWebHook get() {
        return Jenkins.get().getExtensionList(RootAction.class).get(PhoneHomeWebHook.class);
    }

}
