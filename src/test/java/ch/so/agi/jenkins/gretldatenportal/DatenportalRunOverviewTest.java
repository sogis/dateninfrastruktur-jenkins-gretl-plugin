package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class DatenportalRunOverviewTest {
    private int nextBuildNumber = 1;
    @Test
    @WithJenkins
    void paginatesHistoryAndNormalizesPageNumbers(JenkinsRule j) throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "gretl-datenportal-afu");
        List<DatenportalRunSummary> history = new ArrayList<>();
        for (int i = 1; i <= 23; i++) {
            history.add(run(job, i, Result.SUCCESS, "afu", "ch.so.afu"));
        }
        for (int count : List.of(0, 10, 11, 23)) {
            var overview = overview(history.subList(0, count), "", "", "", "1");
            assertEquals(Math.min(count, 10), overview.getRuns().size());
            assertEquals(count, overview.getTotalCount());
            assertEquals(count > 0, overview.hasExecutedRuns());
            assertEquals(count > 10, overview.hasNextPage());
            assertFalse(overview.hasPreviousPage());
            assertEquals(count == 0 ? 0 : 1, overview.getFirstResult());
            assertEquals(Math.min(count, 10), overview.getLastResult());
        }
        for (String page : new String[] {null, "", "invalid", "0", "-1"}) {
            assertEquals(1, overview(history, "", "", "", page).getPage());
        }
        var second = overview(history, "", "", "", "2");
        assertEquals(11, second.getFirstResult());
        assertEquals(20, second.getLastResult());
        assertTrue(second.hasPreviousPage());
        assertTrue(second.hasNextPage());
        var last = overview(history, "", "", "", Long.toString(Long.MAX_VALUE));
        assertEquals(3, last.getPage());
        assertEquals(3, last.getRuns().size());
        assertEquals(21, last.getFirstResult());
        assertEquals(23, last.getLastResult());
        assertFalse(last.hasNextPage());
        assertEquals(23L, overview(history, "", "", "", "1").getRuns().getFirst().getStartTimeMillis());
        assertEquals(1L, last.getRuns().getLast().getStartTimeMillis());
    }

    @Test
    @WithJenkins
    void filtersBeforePagingAndKeepsOptionsFromTheFullHistory(JenkinsRule j) throws Exception {
        WorkflowJob afu = j.jenkins.createProject(WorkflowJob.class, "gretl-datenportal-afu");
        WorkflowJob agi = j.jenkins.createProject(WorkflowJob.class, "gretl-datenportal-agi");
        List<DatenportalRunSummary> history = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            history.add(run(afu, i, Result.FAILURE, "afu", "ch.so.older"));
        }
        history.add(run(afu, 13, Result.SUCCESS, "afu", "ch.so.newer"));
        history.add(run(afu, 14, null, "afu", "ch.so.newer"));
        for (int i = 20; i < 32; i++) {
            history.add(run(agi, i, Result.SUCCESS, "agi", "ch.so.latest"));
        }
        var filtered = overview(history, "afu", "ch.so.older", "FAILURE", "1");
        assertEquals(12, filtered.getTotalCount());
        assertEquals(10, filtered.getRuns().size());
        assertTrue(filtered.getRuns().stream().allMatch(run -> run.getDatasetValue().equals("ch.so.older")));
        assertEquals(2, overview(history, "afu", "ch.so.older", "FAILURE", "2").getRuns().size());
        assertEquals(14, overview(history, "afu", "", "", "1").getTotalCount());
        assertEquals(12, overview(history, "", "ch.so.older", "", "1").getTotalCount());
        assertEquals(13, overview(history, "", "", "SUCCESS", "1").getTotalCount());
        assertEquals(1, overview(history, "", "", "RUNNING", "1").getTotalCount());
        var noMatches = overview(history, "agi", "ch.so.older", "", "1");
        assertTrue(noMatches.hasExecutedRuns());
        assertTrue(noMatches.getRuns().isEmpty());
        assertEquals(0, noMatches.getFirstResult());
        assertEquals(1, noMatches.getPage());
        assertEquals(List.of("afu", "agi"), filtered.getOrganizationOptions().stream().map(option -> option.getValue()).toList());
        assertEquals(List.of("ch.so.latest", "ch.so.newer", "ch.so.older"),
                filtered.getDatasetOptions().stream().map(option -> option.getValue()).toList());
        assertTrue(filtered.getOrganizationOptions().getFirst().isSelected());
        assertEquals("?organization=afu&dataset=ch.so.older&status=FAILURE&page=2", filtered.getNextPageQuery());
        var special = overview(history, "a & b", "ch.so.+data", "", "1");
        assertEquals("?organization=a+%26+b&dataset=ch.so.%2Bdata&status=&page=2", special.getNextPageQuery());
    }

    @Test
    @WithJenkins
    void breaksTimestampTiesByDescendingJobNameAndBuildNumber(JenkinsRule j) throws Exception {
        WorkflowJob afu = j.jenkins.createProject(WorkflowJob.class, "gretl-datenportal-afu");
        WorkflowJob agi = j.jenkins.createProject(WorkflowJob.class, "gretl-datenportal-agi");
        List<DatenportalRunSummary> history = new ArrayList<>(List.of(
                run(afu, 1, Result.SUCCESS, "afu", "ch.so.afu"),
                run(agi, 1, Result.SUCCESS, "agi", "ch.so.agi"),
                run(afu, 1, Result.SUCCESS, "afu", "ch.so.afu")));
        Collections.reverse(history);
        var runs = overview(history, "", "", "", "1").getRuns();
        assertEquals(List.of("gretl-datenportal-agi", "gretl-datenportal-afu", "gretl-datenportal-afu"),
                runs.stream().map(DatenportalRunSummary::getJobFullName).toList());
        assertTrue(runs.get(1).getBuildNumber() > runs.get(2).getBuildNumber());
    }

    private DatenportalRunOverview overview(List<DatenportalRunSummary> runs, String organization, String dataset, String status, String page) {
        return new DatenportalRunOverview(new ScanResult(null, List.of(), List.of()), runs, organization, dataset, status, page);
    }

    private DatenportalRunSummary run(WorkflowJob job, long timestamp, Result result, String organization, String dataset) {
        WorkflowRun run = mock(WorkflowRun.class);
        when(run.getParent()).thenReturn(job);
        when(run.getTimeInMillis()).thenReturn(timestamp);
        when(run.getResult()).thenReturn(result);
        when(run.getNumber()).thenReturn(nextBuildNumber++);
        return new DatenportalRunSummary(run, new ParametersAction(
                new StringParameterValue("ORGANISATION", organization),
                new StringParameterValue("DATASET", dataset)));
    }
}
