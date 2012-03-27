package jenkins.plugins.jclouds;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.statements.java.InstallJDK;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;

/**
 * @author Vijay Kiran
 */
public class JCloudsSlaveTemplate implements Describable<JCloudsSlaveTemplate> {

   private static final Logger LOGGER = Logger.getLogger(JCloudsSlaveTemplate.class.getName());

   private double cores;
   private int ram;
   private String osFamily;
   private String labelString;
   private String name;
   private String description;
   private String osVersion;
   private transient Set<LabelAtom> labelSet;

   protected transient JCloudsCloud cloud;


   @DataBoundConstructor
   public JCloudsSlaveTemplate(final String name,
                               final double cores,
                               final int ram,
                               final String osFamily,
                               final String osVersion,
                               final String labelString,
                               final String description) {

      this.name = name;
      this.cores = cores;
      this.ram = ram;
      this.osFamily = Util.fixNull(osFamily);
      this.osVersion = Util.fixNull(osVersion);
      this.labelString = Util.fixNull(labelString);
      this.description = Util.fixNull(description);
      parseLabels();
   }

   public double getCores() {
      return cores;
   }

   public int getRam() {
      return ram;
   }

   public String getOsFamily() {
      return osFamily;
   }

   public String getOsVersion() {
      return osVersion;
   }

   public String getLabelString() {
      return labelString;
   }

   public String getName() {
      return name;
   }

   public String getDescription() {
      return description;
   }

   public JCloudsCloud getCloud() {
      return cloud;
   }


   public Set getLabelSet() {
      return labelSet;
   }

   /**
    * Initializes data structure that we don't persist.
    */
   protected Object parseLabels() {
      labelSet = Label.parse(labelString);
      return this;
   }

   public JCloudsSlave provision(TaskListener listener) throws IOException {
      LOGGER.info("Provisioning new node");
      NodeMetadata nodeMetadata = createNodeWithAdminUserAndJDKInGroupOpeningWithMinRam(name);
      try {
         return new JCloudsSlave(nodeMetadata, labelString, description);
      } catch (Descriptor.FormException e) {
         throw new AssertionError("Invalid configuration " + e.getMessage());
      }


   }


   private NodeMetadata createNodeWithAdminUserAndJDKInGroupOpeningWithMinRam(String group) {
      LOGGER.info("creating jclouds node");

      ImmutableMap<String, String> userMetadata = ImmutableMap.of("Name", group);

      Template defaultTemplate = getCloud().getCompute().templateBuilder().build();
      TemplateBuilder templateBuilder = getCloud().getCompute().templateBuilder()
            .fromTemplate(defaultTemplate)
            .minRam(ram)
            .minCores(cores);

      if (!Strings.isNullOrEmpty(osFamily)) {
         templateBuilder.osFamily(OsFamily.valueOf(osFamily));
      }

      org.jclouds.compute.domain.Template template = templateBuilder.build();

      // setup the jcloudTemplate to customize the nodeMetadata with jdk, etc. also opening ports
      AdminAccess adminAccess = AdminAccess.builder().adminUsername("jenkins")
            .installAdminPrivateKey(false) // no need
            .grantSudoToAdminUser(false) // no need
            .adminPrivateKey(getCloud().getPrivateKey()) // temporary due to jclouds bug
            .authorizeAdminPublicKey(true)
            .adminPublicKey(getCloud().getPublicKey())
            .build();

      // probably some missing configuration somewhere
      Statement jenkinsDirStatement = Statements.newStatementList(Statements.exec("mkdir /jenkins"), Statements.exec("chown jenkins /jenkins"));

      Statement bootstrap = newStatementList(InstallJDK.fromURL(), adminAccess, jenkinsDirStatement);

      template.getOptions()
            .inboundPorts(22)
            .userMetadata(userMetadata)
            .runScript(bootstrap);

      NodeMetadata nodeMetadata = null;


      try {
         nodeMetadata = getOnlyElement(getCloud().getCompute().createNodesInGroup(group, 1, template));
      } catch (RunNodesException e) {
         throw destroyBadNodesAndPropagate(e);
      }

      //Check if nodeMetadata is null and throw
      return nodeMetadata;
   }

   private RuntimeException destroyBadNodesAndPropagate(RunNodesException e) {
      for (Map.Entry<? extends NodeMetadata, ? extends Throwable> nodeError : e.getNodeErrors().entrySet())
         getCloud().getCompute().destroyNode(nodeError.getKey().getId());
      throw propagate(e);
   }


   public Descriptor<JCloudsSlaveTemplate> getDescriptor() {
      return Hudson.getInstance().getDescriptor(getClass());
   }

   @Extension
   public static final class DescriptorImpl extends Descriptor<JCloudsSlaveTemplate> {

      @Override
      public String getDisplayName() {
         return null;
      }

      public FormValidation doCheckCores(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }

      public FormValidation doCheckRam(@QueryParameter String value) {
         return FormValidation.validateRequired(value);
      }
   }
}
