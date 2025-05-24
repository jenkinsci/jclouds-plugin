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
package jenkins.plugins.jclouds.blobstore;

import static org.jclouds.Constants.LOGGER_HTTP_HEADERS;
import static org.jclouds.Constants.LOGGER_HTTP_WIRE;
import static org.jclouds.blobstore.reference.BlobStoreConstants.BLOBSTORE_LOGGER;

import java.util.logging.Logger;

/**
 * bumps {@code jclouds.blobstore} logging category debug to info.
 */
class BlobStoreLogger extends org.jclouds.logging.jdk.JDKLogger {

    public static class Factory extends JDKLoggerFactory {
        public org.jclouds.logging.Logger getLogger(String category) {
            if (category.equals(BLOBSTORE_LOGGER)
                    || category.equals(LOGGER_HTTP_WIRE) && logWire
                    || category.equals(LOGGER_HTTP_HEADERS) && logHeaders) {
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

    private static final String LOGPREFIX = BlobStoreLogger.class.getPackage().getName();
    private static final boolean logHeaders = Boolean.getBoolean(LOGPREFIX + ".headerLogging");
    private static final boolean logWire = Boolean.getBoolean(LOGPREFIX + ".wireLogging");
}
