package ch.so.agi.jenkins.delivery.queue;

import hudson.Extension;
import hudson.model.Queue;
import hudson.security.ACL;
import hudson.security.SecurityRealm;
import java.util.List;
import jenkins.security.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Never changes authentication of any other Jenkins job. */
public final class DeliveryQueueAuthenticator extends QueueItemAuthenticator {
    @Override public Authentication authenticate2(Queue.Item item){
        if(!(item.task instanceof WorkflowJob job) || !CsvJobProvisioner.owned(job))return null;
        return CsvJobProvisioner.usable(job) && item.getActions(DeliveryQueueAction.class).size()==1?executor():JenkinsAnonymous.authentication();
    }
    static Authentication executor(){return new UsernamePasswordAuthenticationToken(CsvJobProvisioner.EXECUTOR,null,List.of(SecurityRealm.AUTHENTICATED_AUTHORITY2));}
    @Extension public static final class Provider extends QueueItemAuthenticatorProvider {
        private final List<QueueItemAuthenticator> authenticators=List.of(new DeliveryQueueAuthenticator());
        @Override public List<QueueItemAuthenticator> getAuthenticators(){return authenticators;}
    }
    private static final class JenkinsAnonymous {static Authentication authentication(){return jenkins.model.Jenkins.ANONYMOUS2;}}
}
