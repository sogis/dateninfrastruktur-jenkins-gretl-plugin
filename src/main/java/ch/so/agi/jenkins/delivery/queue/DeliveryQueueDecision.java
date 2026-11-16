package ch.so.agi.jenkins.delivery.queue;
import ch.so.agi.jenkins.delivery.client.DeliveryProblem;
import ch.so.agi.jenkins.delivery.upload.IncomingUploadStore;
import hudson.Extension;
import hudson.model.*;
import hudson.model.queue.QueueListener;
import java.io.IOException;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
/** A marker alone cannot schedule a build; only the plugin submission handler holds this transient exact-object permit. */
@Extension public final class DeliveryQueueDecision extends Queue.QueueDecisionHandler {
    private static final ThreadLocal<DeliveryQueueAction> PERMIT=new ThreadLocal<>();
    public static hudson.model.queue.ScheduleResult schedule(Queue queue,WorkflowJob job,DeliveryQueueAction marker){
        if(PERMIT.get()!=null)throw new IllegalStateException("Nested delivery queue permit");
        PERMIT.set(marker);
        try{return queue.schedule2(job,0,List.of(marker,new CauseAction(new Origin(marker.submissionId().toString()))));}
        finally{PERMIT.remove();}
    }
    @Override public boolean shouldSchedule(Queue.Task task,List<Action> actions){
        if(!(task instanceof WorkflowJob job) || !CsvJobProvisioner.owned(job))return true;
        var markers=actions.stream().filter(DeliveryQueueAction.class::isInstance).toList();
        return CsvJobProvisioner.usable(job)&&markers.size()==1&&markers.getFirst()==PERMIT.get()&&actions.stream().noneMatch(ParametersAction.class::isInstance);
    }
    @Extension
    public static final class CancelledUploadCleanup extends QueueListener {
        @Override public void onLeft(Queue.LeftItem item) {
            if (!item.isCancelled() || !(item.task instanceof WorkflowJob job) || !CsvJobProvisioner.owned(job)) return;
            var marker = item.getAction(DeliveryQueueAction.class);
            if (marker == null) return;
            Timer.get().execute(() -> {
                try {
                    new IncomingUploadStore(Jenkins.get().getRootDir().toPath()).remove(marker.submissionId());
                } catch (IOException | DeliveryProblem ignored) {
                    // Another cleanup may already have removed the upload.
                }
            });
        }
    }

    private static final class Origin extends Cause {
        private final String submission;
        private Origin(String submission){this.submission=submission;}
        @Override public String getShortDescription(){return "CSV-Lieferauftrag "+submission;}
    }
}
