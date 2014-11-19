package jenkins.plugins.jclouds.internal;

import hudson.model.BuildListener;

public class BuildListenerLogger implements org.jclouds.logging.Logger {
    private final BuildListener listener;

    public BuildListenerLogger(BuildListener listener) {
        this.listener = listener;
    }

    public void debug(String message, Object... args) {
        // noop
    }

    public void error(String message, Object... args) {
        listener.fatalError(String.format(message, args));
    }

    public void error(Throwable throwable, String message, Object... args) {
        listener.fatalError(String.format(message, args) + ": " + throwable.getCause());
    }

    public String getCategory() {
        return null;
    }

    public void info(String message, Object... args) {
        listener.getLogger().println(String.format(message, args));
    }

    public boolean isDebugEnabled() {
        return false;
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public boolean isTraceEnabled() {
        return false;
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public void trace(String message, Object... args) {
    }

    public void warn(String message, Object... args) {
        listener.error(String.format(message, args));
    }

    public void warn(Throwable throwable, String message, Object... args) {
        listener.error(String.format(message, args) + ": " + throwable.getCause());
    }

}
