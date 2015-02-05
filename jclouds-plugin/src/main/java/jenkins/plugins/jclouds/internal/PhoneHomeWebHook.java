package jenkins.plugins.jclouds.internal;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import jenkins.plugins.jclouds.compute.JCloudsComputer;

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

        String instanceId = req.getParameter("instance_id");
        if (instanceId == null) {
            throw new IllegalArgumentException("Not intended to be browsed interactively (must specify instance_id parameter)");
        }
        LOGGER.info("Received POST for " + instanceId);
        // run in high privilege to see all the projects anonymous users don't see.
        // this is safe because when we actually schedule a build, it's a build that can
        // happen at some random time anyway.
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            for (final Computer c : Jenkins.getInstance().getComputers()) {
                if (JCloudsComputer.class.isInstance(c)) {
                }
            }
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }
    }

    public static final String URLNAME = "jclouds-phonehome";

    private static final Logger LOGGER = Logger.getLogger(PhoneHomeWebHook.class.getName());

    public static PhoneHomeWebHook get() {
        return Hudson.getInstance().getExtensionList(RootAction.class).get(PhoneHomeWebHook.class);
    }

}
