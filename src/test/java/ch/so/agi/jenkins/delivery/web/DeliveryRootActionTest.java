package ch.so.agi.jenkins.delivery.web;

import ch.so.agi.jenkins.delivery.queue.CsvJobProvisioner;
import ch.so.agi.jenkins.delivery.queue.DeliveryQueueAction;
import ch.so.agi.jenkins.delivery.queue.DeliveryQueueDecision;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import static org.junit.jupiter.api.Assertions.*;

@WithJenkins class DeliveryRootActionTest {
    @Test void resolvesOnlyTheBoundBuildAndItsPrivateReport(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        var job = CsvJobProvisioner.ensureJob();
        var id = UUID.randomUUID();
        var marker = new DeliveryQueueAction(id, "submitter");
        var scheduled = DeliveryQueueDecision.schedule(j.jenkins.getQueue(), job, marker);
        var run = (WorkflowRun) scheduled.getItem().getFuture().get(30, TimeUnit.SECONDS);
        assertSame(run, DeliveryRootAction.findRun(id));
        assertNull(DeliveryRootAction.findRun(UUID.randomUUID()));
        assertNull(DeliveryRootAction.validationLog(run, "submitter"));

        var path = run.getRootDir().toPath().resolve("validation.log");
        Files.createSymbolicLink(path, run.getRootDir().toPath().resolve("build.xml"));
        assertNull(DeliveryRootAction.validationLog(run, "submitter"));
        Files.delete(path);
        Files.writeString(path, "private");
        assertNull(DeliveryRootAction.validationLog(run, "other"));
        assertTrue(run.getArtifacts().isEmpty());
        job.setDisabled(true);
        assertSame(run, DeliveryRootAction.findRun(id));
        assertEquals(path, DeliveryRootAction.validationLog(run, "submitter"));

        run.addAction(new DeliveryQueueAction(id, "other"));
        assertNull(DeliveryRootAction.findRun(id));
        assertTrue(run.removeActions(DeliveryQueueAction.class));
        assertNull(DeliveryRootAction.findRun(id));
        run.addAction(marker);
        assertSame(run, DeliveryRootAction.findRun(id));
        run.delete();
        assertNull(DeliveryRootAction.findRun(id));
        assertFalse(Files.exists(path));
    }
}
