package hudson.plugins.jclouds;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.ListBoxModel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.Template;
import org.jclouds.rest.AuthorizationException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Monty Taylor
 */
public class SlaveTemplate implements Describable<SlaveTemplate>  {

    private String name;
    private String description;
    private String labels;
    private String image;

    private String numExecutors;
    private transient JClouds parent;

    @DataBoundConstructor
    public SlaveTemplate(String name, String description, String labelString, String image, String numExecutors)
    {
        this.name = name;
        this.description = description;
        this.labels = Util.fixNull(labelString);
        this.image = image;
        this.numExecutors = numExecutors;
    }


    private static final Logger LOGGER = Logger.getLogger(JClouds.class.getName());
    
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumExecutors() {
        return numExecutors;
    }

    public void setNumExecutors(String numExecutors) {
        this.numExecutors = numExecutors;
    }

    void buildTemplate(JClouds parent)
    {
        this.parent = parent;
    }

    public JClouds getParent() {
        return parent;
    }

    public void setParent(JClouds parent) {
        this.parent = parent;
    }


    @Override
    public Descriptor<SlaveTemplate> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SlaveTemplate> {

        @Override
        public String getDisplayName() {
            return null;
        }

        public ListBoxModel doFillImageItems(@QueryParameter String provider, @QueryParameter String user,@QueryParameter String secret) {
            
            ListBoxModel m = new ListBoxModel();
            ComputeService client = null;
            try {
                client = JClouds.getComputeService(provider, user, secret);
            } catch (Throwable ex) {
                LOGGER.log(Level.INFO, "computer service problem");
                return m;
            }
            for (Image image : client.getImages().values()) {
                m.add(image.getDescription());

                    LOGGER.log(Level.INFO, "image: {0}|{1}|{2}:{3}", new Object[]{
                                image.getArchitecture(),
                                image.getOsFamily(),
                                image.getOsDescription(),
                                image.getDescription()
                            });
            }
            return m;
        }
    }
}
