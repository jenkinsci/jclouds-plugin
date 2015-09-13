package jenkins.plugins.jclouds.internal;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import org.jclouds.compute.domain.NodeMetadata;

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
     */
    @RequirePOST
    public void doIndex(StaplerRequest req, StaplerResponse rsp) {

        String hostName = req.getParameter("hostname");
        if (null == hostName) {
            throw new IllegalArgumentException("Not intended to be browsed interactively (must specify hostname parameter)");
        }
        LOGGER.info("Received POST from " + hostName);
        // run in high privilege to see all the nodes anonymous users don't see.
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            for (final Computer c : Jenkins.getActiveInstance().getComputers()) {
                if (JCloudsComputer.class.isInstance(c)) {
                    final JCloudsSlave slave = ((JCloudsComputer) c).getNode();
                    if (null != slave) {
                        final NodeMetadata nmd = slave.getNodeMetaData();
                        if (null != nmd && nmd.getHostname().equals(hostName)) {
                            slave.setWaitPhoneHome(false);
                        }
                    }
                }
            }
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }
    }

    public static final String URLNAME = "jclouds-phonehome";

    private static final Logger LOGGER = Logger.getLogger(PhoneHomeWebHook.class.getName());

    public static PhoneHomeWebHook get() {
        return Jenkins.getActiveInstance().getExtensionList(RootAction.class).get(PhoneHomeWebHook.class);
    }

}
