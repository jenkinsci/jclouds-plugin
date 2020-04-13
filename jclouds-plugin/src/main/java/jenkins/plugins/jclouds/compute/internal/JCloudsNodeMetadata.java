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

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.domain.Location;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.javax.annotation.Nullable;

import org.jclouds.compute.domain.internal.NodeMetadataImpl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


@SuppressFBWarnings(value={"EQ_DOESNT_OVERRIDE_EQUALS"}, justification="We really don't want to compare the nonce")
public class JCloudsNodeMetadata extends NodeMetadataImpl implements NodeMetadata {
    private final String nonce;

    public final String getNonce() {
        return nonce;
    }

    public JCloudsNodeMetadata(String providerId, String name, String id, Location location, URI uri,
            Map<String, String> userMetadata, Set<String> tags, @Nullable String group, @Nullable Hardware hardware,
            @Nullable String imageId, @Nullable OperatingSystem os, Status status, @Nullable String backendStatus,
            int loginPort, Iterable<String> publicAddresses, Iterable<String> privateAddresses,
            @Nullable LoginCredentials credentials, String hostname, String nonce) {
            super(providerId, name, id, location, uri, userMetadata, tags, group, hardware, imageId, os, status,
                    backendStatus, loginPort, publicAddresses, privateAddresses, credentials, hostname);
            this.nonce = nonce;
    }

    public static JCloudsNodeMetadata fromNodeMetadata(final NodeMetadata nmd, final String nonce) {
        return new JCloudsNodeMetadata(nmd.getProviderId(), nmd.getName(), nmd.getId(), nmd.getLocation(),
                nmd.getUri(), nmd.getUserMetadata(), nmd.getTags(), nmd.getGroup(), nmd.getHardware(),
                nmd.getImageId(), nmd.getOperatingSystem(), nmd.getStatus(), nmd.getBackendStatus(),
                nmd.getLoginPort(), nmd.getPublicAddresses(), nmd.getPrivateAddresses(),
                nmd.getCredentials(), nmd.getHostname(), nonce);
    }
}
