package ch.so.agi.jenkins.delivery.queue;
import hudson.Extension;
import hudson.model.*;
import java.util.List;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
/** A marker alone cannot schedule a build; only the dispatcher holds this transient exact-object permit. */
@Extension public final class DeliveryQueueDecision extends Queue.QueueDecisionHandler {
    private static final ThreadLocal<DeliveryQueueAction> PERMIT=new ThreadLocal<>();
    static hudson.model.queue.ScheduleResult schedule(Queue queue,WorkflowJob job,DeliveryQueueAction marker){
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
    private static final class Origin extends Cause {
        private final String submission;
        private Origin(String submission){this.submission=submission;}
        @Override public String getShortDescription(){return "Zentraler CSV-Lieferauftrag "+submission;}
    }
}
