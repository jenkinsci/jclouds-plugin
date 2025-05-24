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

import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import jenkins.model.Jenkins;

/**
 * The launcher used for JNLP slaves.
 */
public class JCloudsJnlpLauncher extends JNLPLauncher {

    /**
     * Launch the Jenkins Slave on the SlaveComputer.
     *
     * @param computer The node on which to launch the slave.
     * @param listener Task listener for notification purposes.
     */
    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {

        PrintStream logger = listener.getLogger();
        final JCloudsSlave slave = (JCloudsSlave) computer.getNode();
        if (null != slave) {
            slave.publishJnlpMetaData();
            try {
                slave.waitForPhoneHome(logger);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted while waiting for phone home.", e.getCause());
            }
        } else {
            throw new IllegalArgumentException("Could not launch NULL agent.");
        }
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        return null;
    }

    public boolean getTcpSupported() {
        return Jenkins.get().getTcpSlaveAgentListener() != null;
    }

    public boolean getInstanceIdentityInstalled() {
        return Jenkins.get().getPluginManager().getPlugin("instance-identity") != null;
    }

    public boolean getWebSocketSupported() {
        // HACK!! Work around @Restricted(Beta.class). Normally, we would write:
        // return WebSockets.isSupported();
        try {
            Class<?> cl = Class.forName("jenkins.websocket.WebSockets");
            Method m = cl.getMethod("isSupported");
            return (boolean) m.invoke(null);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InvocationTargetException
                | IllegalAccessException x) {
            return false;
        }
    }

    public JCloudsJnlpLauncher() {
        super();
    }
}
