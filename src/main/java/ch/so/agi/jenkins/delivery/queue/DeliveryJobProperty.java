package ch.so.agi.jenkins.delivery.queue;
import hudson.Extension;
import hudson.model.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
/** Own marker, provisioned in code, never an editable form/parameter. */
public final class DeliveryJobProperty extends JobProperty<WorkflowJob> {
    private final int schemaVersion=1;
    boolean valid(){return schemaVersion==1;}
    @Extension public static final class DescriptorImpl extends JobPropertyDescriptor {
        @Override public String getDisplayName(){return "Dateninfrastruktur CSV job";}
        @Override public boolean isApplicable(Class<? extends Job> type){return false;}
    }
}
