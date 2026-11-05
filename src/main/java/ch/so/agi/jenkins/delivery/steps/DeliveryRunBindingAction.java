package ch.so.agi.jenkins.delivery.steps;
import ch.so.agi.jenkins.delivery.client.Wire;
import hudson.model.InvisibleAction;
import hudson.util.Secret;
import java.util.UUID;

/** Jenkins' Secret converter encrypts the only durable lease value in the private run record. */
public final class DeliveryRunBindingAction extends InvisibleAction {
    private final UUID executionId,submissionId;
    private final long fence;
    private final String planDigest;
    private final Secret lease;
    private boolean accepted;
    DeliveryRunBindingAction(UUID submissionId,Wire.Claimed claim){this.submissionId=submissionId;executionId=claim.executionId();fence=claim.fence();planDigest=claim.planDigest();lease=Secret.fromString(claim.leaseSecret());}
    UUID executionId(){return executionId;}UUID submissionId(){return submissionId;}String planDigest(){return planDigest;}
    Wire.Binding binding(){return new Wire.Binding(fence,lease.getPlainText());}
    boolean accepted(){return accepted;}void markAccepted(){accepted=true;}
    @Override public String toString(){return "DeliveryRunBinding[private]";}
}
