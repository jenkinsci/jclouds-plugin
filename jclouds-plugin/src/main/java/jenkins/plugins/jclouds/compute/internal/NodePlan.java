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

import org.jclouds.compute.domain.NodeMetadata;

import shaded.com.google.common.base.Supplier;

public class NodePlan {
    private final String cloudName;
    private final String templateName;
    private final int count;
    private final boolean suspendOrTerminate;
    private final Supplier<NodeMetadata> nodeSupplier;

    public NodePlan(String cloud, String template, int count, boolean suspendOrTerminate, Supplier<NodeMetadata> nodeSupplier) {
        this.cloudName = cloud;
        this.templateName = template;
        this.count = count;
        this.suspendOrTerminate = suspendOrTerminate;
        this.nodeSupplier = nodeSupplier;
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getTemplateName() {
        return templateName;
    }

    public int getCount() {
        return count;
    }

    public boolean isSuspendOrTerminate() {
        return suspendOrTerminate;
    }

    public Supplier<NodeMetadata> getNodeSupplier() {
        return nodeSupplier;
    }
}
