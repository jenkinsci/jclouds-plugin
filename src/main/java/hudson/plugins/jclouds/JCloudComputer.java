/*
 *  The MIT License
 * 
 *  Copyright 2010 mordred.
 * 
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 * 
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 * 
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package hudson.plugins.jclouds;

import com.google.common.base.Predicate;
import hudson.slaves.SlaveComputer;
import java.io.PrintStream;
import java.util.Map;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunScriptOnNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeState;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.ssh.ExecResponse;

/**
 *
 * @author mordred
 */
public class JCloudComputer extends SlaveComputer {

    private transient ComputeService context = null;

    public JCloudComputer(JCloudSlave slave, ComputeService context) {
        super(slave);
        this.context = context;
    }

    @Override
    public JCloudSlave getNode() {
        return (JCloudSlave)super.getNode();
    }

    public String getInstanceId() {
        return getName();
    }

    public NodeState getState() {
        return ((JCloudSlave)super.getNode()).getState();
    }

    public NodeMetadata describeNode() {
        return ((JCloudSlave)super.getNode()).getMetadata();
    }

    public int executeScript(String script, PrintStream logger) {

        try {
            if (context == null) {
                context = JClouds.get().connect();
            }
            if (context != null) {
                throw new Throwable("ComputeService is null. Major problem!");
            }
        } catch (Throwable ex) {
            logger.print(ex.getLocalizedMessage());
            return -1;
        }

        try {
            ExecResponse ret = context.runScriptOnNodesMatching(new Predicate<NodeMetadata> () {
                public boolean apply(NodeMetadata input) {
                    return input.equals(describeNode());
                }
            }, script.getBytes()).get(describeNode());
            logger.print(ret.getOutput());
            return ret.getExitCode();

        } catch (RunScriptOnNodesException ex) {
            logger.print(ex.getLocalizedMessage());
            return -1;
        }

    }
}
