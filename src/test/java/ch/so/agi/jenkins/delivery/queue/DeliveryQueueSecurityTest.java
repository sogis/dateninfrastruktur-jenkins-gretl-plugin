package ch.so.agi.jenkins.delivery.queue;

import hudson.model.*;
import hudson.security.*;
import java.util.*;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.matrixauth.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import static org.junit.jupiter.api.Assertions.*;

@WithJenkins class DeliveryQueueSecurityTest {
    @Test void foreignCollisionIsUntouched(JenkinsRule j)throws Exception{
        var existing=j.createFreeStyleProject(CsvJobProvisioner.JOB);existing.setDescription("Unowned sentinel");existing.save();
        assertThrows(IOException.class,CsvJobProvisioner::ensureJob);assertSame(existing,j.jenkins.getItem(CsvJobProvisioner.JOB));assertEquals("Unowned sentinel",existing.getDescription());
    }
    @Test void oneFixedJobAndNoManualOrForgedQueuePermit(JenkinsRule j)throws Exception{
        j.jenkins.setNumExecutors(0);j.jenkins.setSecurityRealm(j.createDummySecurityRealm());var matrix=new ProjectMatrixAuthorizationStrategy();
        matrix.add(Jenkins.READ,new PermissionEntry(AuthorizationType.USER,"supplier"));j.jenkins.setAuthorizationStrategy(matrix);
        var job=CsvJobProvisioner.ensureJob();assertSame(job,CsvJobProvisioner.ensureJob());assertEquals(List.of(CsvJobProvisioner.JOB),j.jenkins.getItems().stream().map(Item::getName).toList());assertTrue(CsvJobProvisioner.fixedDefinition(job));assertNull(job.getProperty(ParametersDefinitionProperty.class));
        try(var auth=ACL.as2(User.getById("supplier",true).impersonate2())){
            assertTrue(j.jenkins.hasPermission(Jenkins.READ));for(var permission:List.of(Item.READ,Item.BUILD,Item.CONFIGURE,Item.WORKSPACE,Item.CANCEL))assertFalse(job.hasPermission(permission),permission.getId());
        }
        var queue=j.jenkins.getQueue();var marker=new DeliveryQueueAction(UUID.randomUUID(),UUID.randomUUID());
        assertTrue(queue.schedule2(job,0,List.of()).isRefused());assertTrue(queue.schedule2(job,0,List.of(marker)).isRefused());
        assertTrue(queue.schedule2(job,0,List.of(new ParametersAction(new StringParameterValue("SUBMISSION_ID",marker.submissionId().toString())))).isRefused());
        hudson.model.Queue.withLock((Runnable)()->{
        var first=DeliveryQueueDecision.schedule(queue,job,marker);assertTrue(first.isCreated());assertEquals(CsvJobProvisioner.EXECUTOR,first.getItem().authenticate2().getName());
        var duplicate=DeliveryQueueDecision.schedule(queue,job,new DeliveryQueueAction(marker.submissionId(),marker.dispatchId()));assertEquals(first.getItem().getId(),duplicate.getItem().getId());
        var separate=DeliveryQueueDecision.schedule(queue,job,new DeliveryQueueAction(UUID.randomUUID(),UUID.randomUUID()));assertTrue(separate.isCreated());assertNotEquals(first.getItem().getId(),separate.getItem().getId());
        });
        assertFalse(ch.so.agi.jenkins.delivery.steps.CsvDeliveryProcessor.ready(),"No probe processor in normal plugin");
    }
}
