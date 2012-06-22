package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.regex.Pattern;

@Extension
public final class JCloudsCleanupThread extends AsyncPeriodicWork {

    public JCloudsCleanupThread() {
        super("JClouds slave cleanup");
    }

    public long getRecurrencePeriod() {
        return MIN * 5;
    }

    public static void invoke() {
        getInstance().run();
    }

    private static JCloudsCleanupThread getInstance() {
        return Jenkins.getInstance().getExtensionList(AsyncPeriodicWork.class).get(JCloudsCleanupThread.class);
    }

    protected void execute(TaskListener listener) {
        for (Computer c : Jenkins.getInstance().getComputers()) {
            if (JCloudsComputer.class.isInstance(c)) {
                if (((JCloudsComputer)c).getNode().isPendingDelete()) {
                    logger.log(Level.INFO, "Deleting pending node " + c.getName());
                    try {
                        ((JCloudsComputer)c).deleteSlave();
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Failed to disconnect and delete "+c.getName()+": "+e.getMessage());
                    }
                }
            }
        }
    }
}
