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
package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.slaves.OfflineCause;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

public class JCloudsOneOffSlave extends BuildWrapper {
    private static final Logger LOGGER = Logger.getLogger(JCloudsOneOffSlave.class.getName());

    @DataBoundConstructor
    public JCloudsOneOffSlave() {
    }

    //
    // convert Jenkins staticy stuff into pojos; performing as little critical stuff here as
    // possible, as this method is very hard to test due to static usage, etc.
    //
    @Override
    @SuppressWarnings("rawtypes")
    public Environment setUp(AbstractBuild build, Launcher launcher, final BuildListener listener) {
        final Executor x = build.getExecutor();
        if (null != x && JCloudsComputer.class.isInstance(x.getOwner())) {
            final JCloudsComputer c = (JCloudsComputer) x.getOwner();
            return new Environment() {
                @Override
                public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
                    LOGGER.warning("Single-use slave " + c.getName() + " getting torn down.");
                    c.setTemporarilyOffline(true, OfflineCause.create(Messages._OneOffCause()));
                    // Fixes JENKINS-28403
                    c.deleteSlave();
                    return true;
                }
            };
        } else {
            return new Environment() {
            };
        }

    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return "JClouds Single-use slave";
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(AbstractProject item) {
            return true;
        }

    }
}
