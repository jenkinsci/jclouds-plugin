/*
 * Copyright 2010-2020 Adrian Cole, Andrew Bayer, Fritz Elfert, Marat Mavlyutov, Monty Taylor, Vijay Kiran et. al.
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

import static org.jclouds.domain.LocationScope.*;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.domain.Location;
import org.jclouds.domain.LocationScope;
import org.jclouds.googlecomputeengine.GoogleComputeEngineApi;
import org.jclouds.googlecomputeengine.domain.Metadata;
import org.jclouds.googlecomputeengine.features.InstanceApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

/**
 * Publish metadata to VMs.
 *
 * This class provides a method to publish arbitrary
 * metadata do jclouds virtual machines. Since it is highly
 * provider-specific, it was placed in a separate class.
 *
 * @author Fritz Elfert
 */
public class MetaDataPublisher {

    private static final Logger LOGGER = Logger.getLogger(MetaDataPublisher.class.getName());

    private final JCloudsCloud c;

    MetaDataPublisher(JCloudsCloud cloud) {
        c = cloud;
    }

    private String getLocationPart(final NodeMetadata nmd, LocationScope desiredScope) {
        Location loc = nmd.getLocation();
        while (null != loc && loc.getScope() != desiredScope) {
            loc = loc.getParent();
        }
        return (null != loc) ? loc.getId() : "";
    }

    public void publish(String nodeId, String msg, Map<String, String> data) {

        NodeMetadata nmd = c.getCompute().getNodeMetadata(nodeId);
        final String providerName = c.providerName;
        try {
            if (providerName.equals("openstack-nova")) {
                LOGGER.info(msg);
                String region = getLocationPart(nmd, REGION);
                String sid = nmd.getId().replaceFirst("^" + region + "/", "");
                NovaApi napi = c.newApi(NovaApi.class);
                ServerApi sapi = napi.getServerApi(region);
                data.putAll(sapi.getMetadata(sid));
                sapi.updateMetadata(sid, data);
                return;
            }
            if (providerName.equals("google-compute-engine")) {
                LOGGER.info(msg);
                String instance = nmd.getName();
                String zone = getLocationPart(nmd, ZONE);
                GoogleComputeEngineApi gce = c.newApi(GoogleComputeEngineApi.class);
                InstanceApi ia = gce.instancesInZone(zone);
                Metadata md = ia.get(instance).metadata().clone();
                md.putAll(data);
                ia.setMetadata(instance, md);
                return;
            }
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, "Failed to set JNLP properties", x);
            return;
        }
        LOGGER.info(
                "Updating custom metadata not implemented for cloud %s (provider: %s).".format(c.name, providerName));
    }
}
