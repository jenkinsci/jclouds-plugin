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


import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.OfflineCause;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.Symbol;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

public class JCloudsOneOffSlave extends SimpleBuildWrapper implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(JCloudsOneOffSlave.class.getName());

    @DataBoundConstructor
    public JCloudsOneOffSlave() {
    }

    //
    // convert Jenkins static stuff into pojos; performing as little critical stuff here as
    // possible, as this method is very hard to test due to static usage, etc.
    //
    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        context.setDisposer(new JCloudsOneOffSlaveDisposer());
    }

    @Extension @Symbol("jcloudsOneOffAgent")
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return "JClouds Single-use agent";
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(AbstractProject item) {
            return true;
        }

    }

    private static class JCloudsOneOffSlaveDisposer extends Disposer {

        private static final long serialVersionUID = 1L;

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            Computer computer = workspace.toComputer();
            if (computer == null) {
                throw new IllegalStateException("Computer is null");
            }
            if (JCloudsComputer.class.isInstance(computer)) {
                String msg = "Taking single-use agent " + computer.getName() + " offline.";
                LOGGER.warning(msg);
                listener.getLogger().println(msg);
                computer.setTemporaryOfflineCause(OfflineCause.create(Messages._oneOffCause()));
                final JCloudsSlave s = ((JCloudsComputer)computer).getNode();
                if (null != s) {
                    s.setOverrideRetentionTime(Integer.valueOf(0));
                }
            } else {
                listener.getLogger().println("Not a single-use agent, this is a " + computer.getClass());
            }
        }
    }
}
