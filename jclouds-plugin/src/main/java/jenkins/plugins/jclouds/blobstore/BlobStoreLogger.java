package jenkins.plugins.jclouds.blobstore;

import java.util.logging.Logger;

import static org.jclouds.Constants.LOGGER_HTTP_WIRE;
import static org.jclouds.Constants.LOGGER_HTTP_HEADERS;
import static org.jclouds.blobstore.reference.BlobStoreConstants.BLOBSTORE_LOGGER;

/**
 * bumps {@code jclouds.blobstore} logging category debug to info.
 */
class BlobStoreLogger extends org.jclouds.logging.jdk.JDKLogger {

    public static class Factory extends JDKLoggerFactory {
        public org.jclouds.logging.Logger getLogger(String category) {
            if (category.equals(BLOBSTORE_LOGGER) || category.equals(LOGGER_HTTP_WIRE) && logWire ||
                    category.equals(LOGGER_HTTP_HEADERS) && logHeaders) {
                return new BlobStoreLogger(Logger.getLogger(category));
            } else {
                return super.getLogger(category);
            }
        }
    }

    public BlobStoreLogger(Logger logger) {
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

    private final static String LOGPREFIX = BlobStoreLogger.class.getPackage().getName();
    private final static boolean logHeaders = Boolean.getBoolean(LOGPREFIX + ".headerLogging");
    private final static boolean logWire = Boolean.getBoolean(LOGPREFIX + ".wireLogging");
}
