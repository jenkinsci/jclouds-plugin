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
package jenkins.plugins.jclouds.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.Extension;
import hudson.Util;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Extends Username/Password with Openstack project and domain.
 */
public class OpenstackKeystoneV3 extends UsernamePasswordCredentialsImpl implements StandardUsernameCredentials {

    /**
     * The Openstack domain.
     */
    @NonNull
    private final String domain;

    /**
     * The Openstack project name.
     */
    @NonNull
    private final String project;

    @DataBoundConstructor
    public OpenstackKeystoneV3(final @CheckForNull CredentialsScope scope, final @CheckForNull String id,
            final @CheckForNull String description, final @CheckForNull String username, final @CheckForNull String password,
            final @CheckForNull String domain, final @CheckForNull String project) {
        super(scope, id, description, username, password);
        this.domain = Util.fixNull(domain);
        this.project = Util.fixNull(project);
    }

    @NonNull
    public String getDomain() {
        return domain;
    }

    @NonNull
    public String getProject() {
        return project;
    }

    /**
     * {@inheritDoc}
     */
    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Openstack Keystone V3 credentials";
        }
    }

}
