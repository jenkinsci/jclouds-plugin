package jenkins.plugins.jclouds.compute;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Module;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import hudson.Util;
import org.jclouds.Constants;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.util.ComputeServiceUtils;
import org.jclouds.crypto.SshKeys;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * The JClouds version of the Jenkins Cloud.
 * @author Vijay Kiran
 */
public class JCloudsCloud extends Cloud {

   private static final Logger LOGGER = Logger.getLogger(JCloudsCloud.class.getName());

   private final String identity;
   private final String credential;
   private final String providerName;

   private final String privateKey;
   private final String publicKey;
   private final String endPointUrl;
   private final String profile;
   private final List<JCloudsSlaveTemplate> templates;
   private transient ComputeService compute;

   public static JCloudsCloud get() {
      return Hudson.getInstance().clouds.get(JCloudsCloud.class);
   }

   @DataBoundConstructor
   public JCloudsCloud(final String profile,
                       final String providerName,
                       final String identity,
                       final String credential,
                       final String privateKey,
                       final String publicKey,
                       final String endPointUrl,
                       final List<JCloudsSlaveTemplate> templates) {
      super(profile);
      this.profile = Util.fixEmptyAndTrim(profile);
      this.providerName = Util.fixEmptyAndTrim(providerName);
      this.identity = Util.fixEmptyAndTrim(identity);
      this.credential = Util.fixEmptyAndTrim(credential);
      this.privateKey = privateKey;
      this.publicKey = publicKey;
      this.endPointUrl = Util.fixEmptyAndTrim(endPointUrl);
      this.templates = Objects.firstNonNull(templates, Collections.<JCloudsSlaveTemplate>emptyList());
      setCloudForTemplates();

   }

   protected Object setCloudForTemplates() {
      for (JCloudsSlaveTemplate template : templates)
         template.cloud = this;
      return this;
   }

   public ComputeService getCompute() {
      if (this.compute == null) {
         Properties overrides = new Properties();
         if (this.getEndPointUrl() != null && !this.getEndPointUrl().equals("")) {
            overrides.setProperty(Constants.PROPERTY_ENDPOINT, this.getEndPointUrl());
         }
         Iterable<Module> modules = ImmutableSet.<Module>of(new SshjSshClientModule(), new SLF4JLoggingModule(),
               new EnterpriseConfigurationModule());
         this.compute = new ComputeServiceContextFactory()
               .createContext(this.providerName, this.identity, this.credential, modules, overrides).getComputeService();
      }
      return compute;
   }

   public String getProfile() {
      return profile;
   }

   public String getIdentity() {
      return identity;
   }

   public String getCredential() {
      return credential;
   }

   public String getProviderName() {
      return providerName;
   }

   public String getPrivateKey() {
      return privateKey;
   }

   public String getPublicKey() {
      return publicKey;
   }

   public String getEndPointUrl() {
      return endPointUrl;
   }


   public List<JCloudsSlaveTemplate> getTemplates() {
      return Collections.unmodifiableList(templates);
   }

   @Override
   public boolean canProvision(final Label label) {
      return getTemplate(label) != null;
   }


   public JCloudsSlaveTemplate getTemplate(String name) {
      for (JCloudsSlaveTemplate t : templates)
         if (t.getName().equals(name))
            return t;
      return null;
   }

   /**
    * Gets {@link jenkins.plugins.jclouds.compute.JCloudsSlaveTemplate} that has the matching {@link Label}.
    */
   public JCloudsSlaveTemplate getTemplate(Label label) {
      for (JCloudsSlaveTemplate t : templates)
         if (label == null || label.matches(t.getLabelSet()))
            return t;
      return null;
   }


   public void doProvision(StaplerRequest req, StaplerResponse rsp, @QueryParameter String name) throws ServletException, IOException, Descriptor.FormException {
      checkPermission(PROVISION);
      if (name == null) {
         sendError("The slave template name query parameter is missing", req, rsp);
         return;
      }
      JCloudsSlaveTemplate t = getTemplate(name);
      if (t == null) {
         sendError("No such slave template with name : " + name, req, rsp);
         return;
      }

      StringWriter sw = new StringWriter();
      StreamTaskListener listener = new StreamTaskListener(sw);

      JCloudsSlave node = t.provision(listener);
      Hudson.getInstance().addNode(node);

      rsp.sendRedirect2(req.getContextPath() + "/computer/" + node.getNodeName());

   }

   public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
      LOGGER.info("Provisioning new node with label " + label.getName() + ", excessWorkload: " + excessWorkload);
      List<NodeProvisioner.PlannedNode> nodes = new ArrayList<NodeProvisioner.PlannedNode>();
      nodes.add(new NodeProvisioner.PlannedNode(label.getName(),
            Computer.threadPoolForRemoting.submit(new Callable<Node>() {
               public Node call() throws Exception {
//                  NodeMetadata nodeMetadata =
//                        createNodeWithAdminUserAndJDKInGroupOpeningPortAndMinRam("jenkins", 8000);
                  // return new JCloudsSlave(nodeMetadata);
                  return null;
               }
            }), 1));
      return nodes;
   }


   @Extension
   public static class DescriptorImpl extends Descriptor<Cloud> {

      /**
       * Human readable name of this kind of configurable object.
       */
      @Override
      public String getDisplayName() {
         return "Cloud (JClouds)";
      }

      public FormValidation doTestConnection(@QueryParameter String providerName,
                                             @QueryParameter String identity,
                                             @QueryParameter String credential,
                                             @QueryParameter String privateKey,
                                             @QueryParameter String endPointUrl) {
         if (identity == null)
            return FormValidation.error("Invalid (AccessId).");
         if (credential == null)
            return FormValidation.error("Invalid credential (secret key).");
         if (privateKey == null)
            return FormValidation.error("Private key is not specified. Click 'Generate Key' to generate one.");


         // Remove empty text/whitespace from the fields.
         providerName = Util.fixEmptyAndTrim(providerName);
         identity = Util.fixEmptyAndTrim(identity);
         credential = Util.fixEmptyAndTrim(credential);
         endPointUrl = Util.fixEmptyAndTrim(endPointUrl);

         Iterable<Module> modules = ImmutableSet.<Module>of(new SshjSshClientModule(), new SLF4JLoggingModule(),
               new EnterpriseConfigurationModule());
         FormValidation result = FormValidation.ok("Connection succeeded!");
         ComputeService computeService = null;
         try {
            Properties overrides = new Properties();
            if (!Strings.isNullOrEmpty(endPointUrl)) {
               overrides.setProperty(Constants.PROPERTY_ENDPOINT, endPointUrl);
            }

            ComputeServiceContext context = new ComputeServiceContextFactory()
                  .createContext(providerName, identity, credential, modules, overrides);

            computeService = context.getComputeService();
            computeService.listNodes();
         } catch (Exception ex) {
            result = FormValidation.error("Cannot connect to specified cloud, please check the identity and credentials: " + ex.getMessage());
         } finally {
            if (computeService != null) {
               computeService.getContext().close();
            }
         }
         return result;
      }


      public FormValidation doGenerateKeyPair(StaplerResponse rsp, String identity, String credential) throws IOException, ServletException {
         Map<String, String> keyPair = SshKeys.generate();
         rsp.addHeader("script", "findPreviousFormItem(button,'privateKey').value='" + keyPair.get("private").replace("\n", "\\n") + "';" +
               "findPreviousFormItem(button,'publicKey').value='" + keyPair.get("public").replace("\n", "\\n") + "';"
         );
         return FormValidation.ok("Successfully generated private Key!");
      }

      public FormValidation doCheckPrivateKey(@QueryParameter String value) throws IOException, ServletException {
         boolean hasStart = false, hasEnd = false;
         BufferedReader br = new BufferedReader(new StringReader(value));
         String line;
         while ((line = br.readLine()) != null) {
            if (line.equals("-----BEGIN RSA PRIVATE KEY-----"))
               hasStart = true;
            if (line.equals("-----END RSA PRIVATE KEY-----"))
               hasEnd = true;
         }
         if (!hasStart)
            return FormValidation.error("Please make sure that the private key starts with '-----BEGIN RSA PRIVATE KEY-----'");
         if (!hasEnd)
            return FormValidation.error("The private key is missing the trailing 'END RSA PRIVATE KEY' marker. Copy&paste error?");
         if (SshKeys.fingerprintPrivateKey(value) == null)
            return FormValidation.error("Invalid private key, please check again or click on 'Generate Key' to generate a new key");
         return FormValidation.ok();
      }

      public AutoCompletionCandidates doAutoCompleteProviderName(@QueryParameter final String value) {

         Iterable<String> supportedProviders = ComputeServiceUtils.getSupportedProviders();

         Iterable<String> matchedProviders = Iterables.filter(supportedProviders, new Predicate<String>() {
            public boolean apply(@Nullable String input) {
               return input != null && input.startsWith(value.toLowerCase());
            }
         });

         AutoCompletionCandidates candidates = new AutoCompletionCandidates();
         for (String matchedProvider : matchedProviders) {
            candidates.add(matchedProvider);
         }
         return candidates;
      }

      public FormValidation doCheckProfile(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }

      public FormValidation doCheckProviderName(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }

      public FormValidation doCheckPublicKey(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }

      public FormValidation doCheckCredential(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }

      public FormValidation doCheckIdentity(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }

      public FormValidation doCheckEndPointUrl(@QueryParameter String value) {
         if (!value.isEmpty() && !value.startsWith("http")) {
            return FormValidation.error("The endpoint must be an URL");
         }
         return FormValidation.ok();
      }
   }
}
