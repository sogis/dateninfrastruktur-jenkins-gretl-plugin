package ch.so.agi.jenkins.delivery.queue;

import ch.so.agi.jenkins.delivery.upload.IncomingUploadStore;
import hudson.model.*;
import hudson.security.*;
import hudson.tasks.LogRotator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.io.IOException;
import java.nio.file.Files;
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

    @Test void foreignPipelineKeepsItsBuildDiscarder(JenkinsRule j) throws Exception {
        var existing = j.jenkins.createProject(WorkflowJob.class, CsvJobProvisioner.JOB);
        existing.setDescription("Unowned sentinel");
        var retention = new LogRotator(90, -1, -1, -1);
        existing.setBuildDiscarder(retention);

        assertThrows(IOException.class, CsvJobProvisioner::ensureJob);
        assertSame(existing, j.jenkins.getItem(CsvJobProvisioner.JOB));
        assertEquals("Unowned sentinel", existing.getDescription());
        assertSame(retention, existing.getBuildDiscarder());
    }

    @Test void provisionedRetentionExpiresTheLastSuccessfulBuildAndItsPrivateLog(JenkinsRule j)
            throws Exception {
        var job = CsvJobProvisioner.ensureJob();
        assertRetention(job);

        job.setBuildDiscarder(null); // Owned job saved before the retention rule existed.
        job.doReload();
        assertNull(job.getBuildDiscarder());
        assertSame(job, CsvJobProvisioner.ensureJob());
        assertEquals(List.of(job), j.jenkins.getItems());
        assertRetention(job);

        // Load a completed run from disk to simulate its age without running GRETL.
        var buildDir = Files.createDirectories(job.getBuildDir().toPath().resolve("1"));
        var report = Files.writeString(buildDir.resolve("validation.log"), "synthetic");
        Files.writeString(buildDir.resolve("build.xml"), """
                <flow-build>
                  <timestamp>%d</timestamp>
                  <result>SUCCESS</result>
                  <completed>true</completed>
                </flow-build>
                """.formatted(Instant.now().minus(31, ChronoUnit.DAYS).toEpochMilli()));
        job.doReload();
        assertRetention(job);
        var build = job.getBuildByNumber(1);
        assertNotNull(build);
        assertSame(build, job.getLastSuccessfulBuild());
        assertSame(build, job.getLastStableBuild());

        job.logRotate();

        assertNull(job.getBuildByNumber(1));
        assertFalse(Files.exists(report));
        assertFalse(Files.exists(buildDir));
    }

    private static void assertRetention(WorkflowJob job) {
        var retention = assertInstanceOf(LogRotator.class, job.getBuildDiscarder());
        assertEquals(30, retention.getDaysToKeep());
        assertEquals(-1, retention.getNumToKeep());
        assertEquals(-1, retention.getArtifactDaysToKeep());
        assertEquals(-1, retention.getArtifactNumToKeep());
        assertTrue(retention.isRemoveLastBuild());
    }
    @Test void oneFixedJobAndNoManualOrForgedQueuePermit(JenkinsRule j)throws Exception{
        j.jenkins.setNumExecutors(0);j.jenkins.setSecurityRealm(j.createDummySecurityRealm());var matrix=new ProjectMatrixAuthorizationStrategy();
        matrix.add(Jenkins.READ,new PermissionEntry(AuthorizationType.USER,"supplier"));
        matrix.add(Jenkins.READ,new PermissionEntry(AuthorizationType.USER,CsvJobProvisioner.EXECUTOR));
        j.jenkins.setAuthorizationStrategy(matrix);
        var job=CsvJobProvisioner.ensureJob();assertSame(job,CsvJobProvisioner.ensureJob());assertEquals(List.of(CsvJobProvisioner.JOB),j.jenkins.getItems().stream().map(Item::getName).toList());assertTrue(CsvJobProvisioner.fixedDefinition(job));assertNull(job.getProperty(ParametersDefinitionProperty.class));
        assertRetention(job);
        try(var auth=ACL.as2(User.getById("supplier",true).impersonate2())){
            assertTrue(j.jenkins.hasPermission(Jenkins.READ));for(var permission:List.of(Item.READ,Item.BUILD,Item.CONFIGURE,Item.WORKSPACE,Item.CANCEL))assertFalse(job.hasPermission(permission),permission.getId());
        }
        try (var auth = ACL.as2(User.getById(CsvJobProvisioner.EXECUTOR, true).impersonate2())) {
            for (var permission : List.of(Item.READ, Item.BUILD, Item.WORKSPACE, Item.CANCEL)) {
                assertTrue(job.hasPermission(permission), permission.getId());
            }
            assertFalse(job.hasPermission(Item.CONFIGURE));
        }
        var queue=j.jenkins.getQueue();var marker=new DeliveryQueueAction(UUID.randomUUID(), "supplier");
        assertTrue(queue.schedule2(job,0,List.of()).isRefused());assertTrue(queue.schedule2(job,0,List.of(marker)).isRefused());
        assertTrue(queue.schedule2(job,0,List.of(new ParametersAction(new StringParameterValue("SUBMISSION_ID",marker.submissionId().toString())))).isRefused());
        var store = new IncomingUploadStore(j.jenkins.getRootDir().toPath());
        store.receive(marker.submissionId(), 100, (path, limit) -> {
            Files.writeString(path, "code\n1\n");
            return new IncomingUploadStore.Evidence(Files.size(path), "", "input.csv");
        });
        var upload = store.existing(marker.submissionId());
        hudson.model.Queue.withLock((Runnable)()->{
        var first=DeliveryQueueDecision.schedule(queue,job,marker);assertTrue(first.isCreated());assertEquals(CsvJobProvisioner.EXECUTOR,first.getItem().authenticate2().getName());
        var duplicate=DeliveryQueueDecision.schedule(queue,job,new DeliveryQueueAction(marker.submissionId(), "supplier"));assertEquals(first.getItem().getId(),duplicate.getItem().getId());
        var separate=DeliveryQueueDecision.schedule(queue,job,new DeliveryQueueAction(UUID.randomUUID(), "supplier"));assertTrue(separate.isCreated());assertNotEquals(first.getItem().getId(),separate.getItem().getId());
        // Finish this queue contract while it is still a queue contract. A Pipeline
        // flyweight may otherwise begin writing run files as JenkinsRule removes Home.
        assertTrue(queue.cancel(first.getItem()));
        assertTrue(queue.cancel(separate.getItem()));
        });
        for (int attempt = 0; attempt < 100 && Files.exists(upload); attempt++) Thread.sleep(50);
        assertFalse(Files.exists(upload), "Cancelling the queue item must remove the private upload");
        assertTrue(job.getBuilds().isEmpty(), "A cancelled queue item must not start GRETL");
    }
}
