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
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

import jenkins.plugins.jclouds.compute.JCloudsComputer;
import jenkins.plugins.jclouds.compute.JCloudsSlave;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import static org.kohsuke.stapler.StaplerResponse2.SC_UNAUTHORIZED;
import static org.kohsuke.stapler.StaplerResponse2.SC_BAD_REQUEST;
import static org.kohsuke.stapler.StaplerResponse2.SC_FORBIDDEN;

/**
 * Receives phone home hook from slave.
 *
 * @author Fritz Elfert
 */
@Extension
public class JnlpProvisionWebHook implements UnprotectedRootAction {

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

        final String rHost = req.getRemoteHost();
        final String rAddr = req.getRemoteAddr();
        final String hostName = req.getParameter("hostname");
        final String auth = req.getParameter("auth");
        if (null == auth) {
            rsp.setStatus(SC_UNAUTHORIZED);
            LOGGER.warning("Received unauthenticated POST from %s [%s]".format(rHost, rAddr));
            return;
        }
        if (null == hostName) {
            rsp.setStatus(SC_BAD_REQUEST);
            LOGGER.warning("Received malformed POST from %s [%s]".format(rHost, rAddr));
            return;
        }
        LOGGER.info("Received POST from %s [%s] for %s".format(rHost, rAddr, hostName));
        // run in high privilege to see all the nodes anonymous users don't see.
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            for (final Computer c : Jenkins.get().getComputers()) {
                if (JCloudsComputer.class.isInstance(c)) {
                    final JCloudsSlave slave = ((JCloudsComputer) c).getNode();
                    if (null != slave && slave.getNodeName().equals(hostName)) {
                        final String result = slave.handleJnlpProvisioning(auth);
                        if (result.isEmpty()) {
                            rsp.setStatus(SC_FORBIDDEN);
                            return;
                        }
                        final ByteArrayInputStream str =
                            new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8));
                        try {
                            rsp.serveFile(req, str, 0, (long)(result.length()), "response.json");
                        } catch (Exception x) {
                            LOGGER.log(WARNING, "Could not send response:", x);
                        }
                        return;
                    }
                }
            }
            LOGGER.warning("hostName not found " + hostName);
        }
    }

    public static final String URLNAME = "jclouds-jnlp-provision";

    private static final Logger LOGGER = Logger.getLogger(JnlpProvisionWebHook.class.getName());

    public static JnlpProvisionWebHook get() {
        return Jenkins.get().getExtensionList(RootAction.class).get(JnlpProvisionWebHook.class);
    }

}
