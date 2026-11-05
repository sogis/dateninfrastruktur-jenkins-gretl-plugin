package ch.so.agi.jenkins.delivery.queue;
import hudson.model.*;
import hudson.model.Queue;
import java.util.*;
/** Durable transport references only. No parameter, token, lease or public action URL. */
public final class DeliveryQueueAction extends InvisibleAction implements Queue.QueueAction {
    private final UUID submissionId,dispatchId;
    DeliveryQueueAction(UUID submissionId,UUID dispatchId){this.submissionId=Objects.requireNonNull(submissionId);this.dispatchId=Objects.requireNonNull(dispatchId);}
    public UUID submissionId(){return submissionId;}public UUID dispatchId(){return dispatchId;}
    @Override public boolean shouldSchedule(List<Action> actions){return actions.stream().noneMatch(a->a instanceof DeliveryQueueAction d && dispatchId.equals(d.dispatchId()));}
}
