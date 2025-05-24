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
package jenkins.plugins.jclouds.compute.internal;

import com.google.common.base.Supplier;
import java.util.concurrent.Callable;
import org.jclouds.logging.Logger;

class RetryOnExceptionSupplier implements Callable<JCloudsNodeMetadata> {
    private static final int MAX_ATTEMPTS = 5;
    private final Logger logger;
    private final Supplier<JCloudsNodeMetadata> supplier;

    RetryOnExceptionSupplier(Supplier<JCloudsNodeMetadata> supplier, Logger logger) {
        this.supplier = supplier;
        this.logger = logger;
    }

    public JCloudsNodeMetadata call() throws Exception {
        int attempts = 0;

        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            try {
                JCloudsNodeMetadata n = supplier.get();
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
