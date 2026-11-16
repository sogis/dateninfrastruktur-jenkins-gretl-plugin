package ch.so.agi.jenkins.delivery.queue;
import hudson.model.*;
import hudson.model.Queue;
import java.util.*;
/** Durable transport references only. No parameter, token, lease or public action URL. */
public final class DeliveryQueueAction extends InvisibleAction implements Queue.QueueAction {
    private final UUID submissionId;
    public DeliveryQueueAction(UUID submissionId){this.submissionId=Objects.requireNonNull(submissionId); }
    public UUID submissionId(){return submissionId;}
    @Override public boolean shouldSchedule(List<Action> actions){return actions.stream().noneMatch(a->a instanceof DeliveryQueueAction d && submissionId.equals(d.submissionId()));}
}
