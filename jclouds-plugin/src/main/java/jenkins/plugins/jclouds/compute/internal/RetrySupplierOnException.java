package jenkins.plugins.jclouds.compute.internal;

import java.util.concurrent.Callable;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.logging.Logger;

import shaded.com.google.common.base.Supplier;

class RetrySupplierOnException implements Callable<NodeMetadata> {
    private final int MAX_ATTEMPTS = 5;
    private final Logger logger;
    private final Supplier<NodeMetadata> supplier;

    RetrySupplierOnException(Supplier<NodeMetadata> supplier, Logger logger) {
        this.supplier = supplier;
        this.logger = logger;
    }

    public NodeMetadata call() throws Exception {
        int attempts = 0;

        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            try {
                NodeMetadata n = supplier.get();
                if (n != null) {
                    return n;
                }
            } catch (RuntimeException e) {
                logger.warn("Exception creating a node: " + e.getMessage());
                // Something to log the e.getCause() which should be a
                // RunNodesException
            }
        }

        return null;
    }
}
