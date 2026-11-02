package ch.so.agi.jenkins.gretldatenportal;

import hudson.Extension;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public final class GretlDatenportalManagedSeedJobProperty extends JobProperty<FreeStyleProject> {

    @DataBoundConstructor
    public GretlDatenportalManagedSeedJobProperty() {
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return FreeStyleProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "GRETL Datenportal managed seed job";
        }
    }
}
