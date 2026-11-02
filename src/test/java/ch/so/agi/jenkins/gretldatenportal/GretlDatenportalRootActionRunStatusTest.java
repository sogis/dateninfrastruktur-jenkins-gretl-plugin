package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Queue;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.ScheduleResult;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class GretlDatenportalRootActionRunStatusTest {
    @Test
    @WithJenkins
    void runDetailsUrlUsesPluginRunView(JenkinsRule jenkinsRule) {
        GretlDatenportalRootAction action = new GretlDatenportalRootAction();

        String url = action.runDetailsUrl("https://jenkins.example", "folder/gretl-datenportal-afu", null, 42L);

        assertEquals(
                "https://jenkins.example/gretl-datenportal/run?job=folder%2Fgretl-datenportal-afu&queue=42",
                url);
    }

    @Test
    @WithJenkins
    void resolvesQueueIdsToQueuedAndRunningBuilds(JenkinsRule jenkinsRule) throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "gretl-datenportal-afu");
        job.setConcurrentBuild(false);
        job.setDefinition(new CpsFlowDefinition("sleep time: 30, unit: 'SECONDS'", true));

        WorkflowRun firstRun = job.scheduleBuild2(0, new CauseAction(new Cause.UserIdCause())).waitForStart();

        ScheduleResult queuedBuild = Queue.getInstance().schedule2(job, 0, new CauseAction(new Cause.UserIdCause()));
        assertTrue(queuedBuild.isAccepted());
        assertNotNull(queuedBuild.getItem());

        long queueId = queuedBuild.getItem().getId();
        GretlDatenportalRootAction action = new GretlDatenportalRootAction();

        RunDetails queuedDetails = action.resolveRunDetails(job.getFullName(), "", Long.toString(queueId));
        assertNotNull(queuedDetails);
        assertEquals("QUEUED", queuedDetails.getStatus());
        assertEquals(queueId, queuedDetails.getQueueId());
        assertFalse(queuedDetails.hasRun());

        JSONObject queuedJson = queuedDetails.toJson("https://jenkins.example", action.getUrlName());
        assertEquals("QUEUED", queuedJson.getString("status"));
        assertEquals("Wartet", queuedJson.getString("statusLabel"));
        assertFalse(queuedJson.getBoolean("complete"));

        @SuppressWarnings("unchecked")
        QueueTaskFuture<Queue.Executable> queuedFuture =
                (QueueTaskFuture<Queue.Executable>) queuedBuild.getItem().getFuture();
        firstRun.doStop();
        jenkinsRule.waitForCompletion(firstRun);

        WorkflowRun secondRun = (WorkflowRun) queuedFuture.waitForStart();
        assertEquals(queueId, secondRun.getQueueId());

        RunDetails runningDetails = action.resolveRunDetails(job.getFullName(), "", Long.toString(queueId));
        assertNotNull(runningDetails);
        assertTrue(runningDetails.hasRun());
        assertEquals("RUNNING", runningDetails.getStatus());
        assertEquals(queueId, runningDetails.getQueueId());

        JSONObject runningJson = runningDetails.toJson("https://jenkins.example", action.getUrlName());
        assertEquals("RUNNING", runningJson.getString("status"));
        assertEquals(runningDetails.getRun().getNumber(), runningJson.getInt("buildNumber"));
        assertTrue(runningJson.getString("consoleUrl").contains("/console"));

        RunDetails directDetails = action.resolveRunDetails(
                job.getFullName(),
                Integer.toString(runningDetails.getRun().getNumber()),
                "");
        assertNotNull(directDetails);
        assertEquals(runningDetails.getRun().getNumber(), directDetails.getBuildNumber());

        runningDetails.getRun().doStop();
        jenkinsRule.waitForCompletion(runningDetails.getRun());

        RunDetails finishedDetails = action.resolveRunDetails(
                job.getFullName(),
                Integer.toString(runningDetails.getRun().getNumber()),
                "");
        assertNotNull(finishedDetails);
        assertEquals("ABORTED", finishedDetails.getStatus());
        assertTrue(finishedDetails.isComplete());
    }
}
