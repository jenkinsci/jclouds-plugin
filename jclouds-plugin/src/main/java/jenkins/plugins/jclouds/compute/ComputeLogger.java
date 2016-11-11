package jenkins.plugins.jclouds.compute;

import java.util.logging.Logger;

import static org.jclouds.Constants.LOGGER_HTTP_WIRE;
import static org.jclouds.Constants.LOGGER_HTTP_HEADERS;
import static org.jclouds.compute.reference.ComputeServiceConstants.COMPUTE_LOGGER;

/**
 * bumps {@code jclouds.compute} logging category debug to info.
 *
 * @author Adrian Cole
 */
class ComputeLogger extends org.jclouds.logging.jdk.JDKLogger {

    public static class Factory extends JDKLoggerFactory {
        public org.jclouds.logging.Logger getLogger(String category) {
            if (category.equals(COMPUTE_LOGGER) || category.equals(LOGGER_HTTP_WIRE) && logWire ||
                    category.equals(LOGGER_HTTP_HEADERS) && logHeaders) {
                return new ComputeLogger(Logger.getLogger(category));
            } else {
                return super.getLogger(category);
            }
        }
    }

    public ComputeLogger(Logger logger) {
        super(logger);
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    protected void logDebug(String message) {
        super.logInfo(message);
    }

    private final static String LOGPREFIX = ComputeLogger.class.getPackage().getName();
    public static boolean logHeaders = Boolean.getBoolean(LOGPREFIX + ".headerLogging") ||
        Boolean.getBoolean(ComputeLogger.class.getName() + ".headerLogging"); // backward compatibility
    public static boolean logWire = Boolean.getBoolean(LOGPREFIX + ".wireLogging") ||
        Boolean.getBoolean(ComputeLogger.class.getName() + ".wireLogging");
}
