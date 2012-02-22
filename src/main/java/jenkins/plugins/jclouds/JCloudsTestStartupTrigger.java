package jenkins.plugins.jclouds;

import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.labels.LabelAtom;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Logger;

/**
 * !!!!! WARNING !!!!!
 * Temporary class to test
 *
 * @author Vijay Kiran
 */
public class JCloudsTestStartupTrigger extends Trigger<BuildableItem> {
    private static final Logger LOGGER = Logger.getLogger(JCloudsTestStartupTrigger.class.getName());

    @Extension
    public static final TriggerDescriptor DESCRIPTOR = new TriggerDescriptor() {
        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Test Trigger - for provisioning EC2 with JClouds";
        }

        @Override
        public JCloudsTestStartupTrigger newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
            return new JCloudsTestStartupTrigger();
        }
    };

    @Override
    public void start(BuildableItem project, boolean newInstance) {
        super.start(project, newInstance);
        LOGGER.info("JClouds Plugin Test Trigger - Started - Provisioning new JCloudsCloud");
        JCloudsCloud jCloudsCloud = new JCloudsCloud("JClouds");
        jCloudsCloud.provision(new LabelAtom("EC2"), 1);
    }
}
