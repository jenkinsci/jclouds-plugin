/*
 *  The MIT License
 * 
 *  Copyright 2010 Monty Taylor
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

import hudson.model.Computer;
import hudson.model.Descriptor.FormException;
import hudson.model.Slave;
import java.io.IOException;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeState;
import org.jclouds.domain.Location;

/**
 *
 * @author mordred
 */
public class JCloudSlave extends Slave {

    /**
	 * 
	 */
	private static final long serialVersionUID = 2806718926870936855L;
	private final Location location;
    private final NodeMetadata nodemeta;

    private transient ComputeService context = null;

    public JCloudSlave(String name, String description, String remoteFS, Location location, String labelString, ComputeService context, NodeMetadata nodemeta) throws FormException, IOException {

        super(name, description, remoteFS, 1, Mode.EXCLUSIVE, labelString, new JCloudCompuerLauncher(), new JCloudRetentionStrategy(), null);
        this.nodemeta = nodemeta;
        this.location = location;
    }


    @Override
    public Computer createComputer() {
        return new JCloudComputer(this, context);
    }

    NodeState getState() {
        return nodemeta.getState();
    }

    NodeMetadata getMetadata() {
        return nodemeta;
    }


	public Location getLocation() {
		return location;
	}
}
