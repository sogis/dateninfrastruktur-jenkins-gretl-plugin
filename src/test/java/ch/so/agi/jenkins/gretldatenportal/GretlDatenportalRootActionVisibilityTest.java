package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class GretlDatenportalRootActionVisibilityTest {
    @TempDir
    Path tempDir;

    @Test
    @WithJenkins
    void listsOnlyGeneratedWorkflowJobsReadableByTheCurrentUser(JenkinsRule jenkinsRule) throws Exception {
        Path topicRepository = tempDir.resolve("topics");
        GitTestSupport.addOrganization(topicRepository, "afu", "ch.so.afu");
        GitTestSupport.addOrganization(topicRepository, "statistikdienst", "ch.so.statistikdienst");
        GitTestSupport.addOrganization(topicRepository, "missing", "ch.so.missing");
        Files.writeString(
                topicRepository.resolve("shared").resolve(TopicRepositoryScanner.TEAMS_FILE),
                """
                teams:
                  datenportal-read:
                    users:
                      - read-user
                      - catalog-user
                  datenportal-build:
                    users:
                      - build-user
                  gretl-datenportal-seed-operators:
                    users:
                      - seed-user
                """,
                StandardCharsets.UTF_8);

        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryUrl("");
        configuration.setTopicRepositoryPath(topicRepository.toString());
        configuration.setTopicRepositoryMode(TopicRepositoryMode.WORKING_TREE.getValue());

        ProjectMatrixAuthorizationStrategy authorization = new ProjectMatrixAuthorizationStrategy();
        for (String user : List.of("read-user", "build-user", "catalog-user")) {
            authorization.getGrantedPermissionEntries()
                    .computeIfAbsent(Jenkins.READ, ignored -> new HashSet<>())
                    .add(PermissionEntry.user(user));
        }
        jenkinsRule.jenkins.setAuthorizationStrategy(authorization);

        GretlDatenportalAuthorizationSynchronizer synchronizer = new GretlDatenportalAuthorizationSynchronizer();
        WorkflowJob afuJob = workflowJob(jenkinsRule, "gretl-datenportal-afu");
        synchronizer.synchronize(
                afuJob,
                new PermissionConfiguration(List.of("read-user"), List.of("build-user")));
        WorkflowJob statistikdienstJob = workflowJob(jenkinsRule, "gretl-datenportal-statistikdienst");
        synchronizer.synchronize(
                statistikdienstJob,
                new PermissionConfiguration(List.of("other-user"), List.of()));
        createBuild(afuJob, "afu", "ch.so.afu");
        createBuild(statistikdienstJob, "statistikdienst", "ch.so.statistikdienst");

        GretlDatenportalRootAction action = new GretlDatenportalRootAction();

        try (ACLContext ignored = ACL.as2(authentication("read-user"))) {
            assertEquals(List.of("afu"), organizationIds(action));
            assertEquals(List.of("gretl-datenportal-afu"), executedJobNames(action));
            assertEquals(1, action.getRunOverview().getTotalCount());
            assertEquals(List.of("gretl-datenportal-afu"), action.getRunOverview().getRuns().stream()
                    .map(DatenportalRunSummary::getJobName).toList());
            assertEquals(List.of("ch.so.afu"), action.getRunOverview().getRuns().stream()
                    .map(DatenportalRunSummary::getDatasetValue).toList());
            assertEquals(List.of("afu"), action.getRunOverview().getOrganizationOptions().stream()
                    .map(DatenportalRunOverview.FilterOption::getValue).toList());
            assertEquals(List.of("ch.so.afu"), action.getRunOverview().getDatasetOptions().stream()
                    .map(DatenportalRunOverview.FilterOption::getValue).toList());
        }
        try (ACLContext ignored = ACL.as2(authentication("build-user"))) {
            assertEquals(List.of("afu"), organizationIds(action));
        }
        try (ACLContext ignored = ACL.as2(authentication("catalog-user"))) {
            assertEquals(List.of(), organizationIds(action));
            assertEquals(List.of(), executedJobNames(action));
            assertEquals(0, action.getRunOverview().getTotalCount());
            assertEquals(List.of(), action.getRunOverview().getOrganizationOptions());
            assertEquals(List.of(), action.getRunOverview().getDatasetOptions());
        }
    }

    private WorkflowJob workflowJob(JenkinsRule jenkinsRule, String name) throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, name);
        job.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("ORGANISATION", ""),
                new StringParameterDefinition("DATASET", "")));
        job.setDefinition(new CpsFlowDefinition("echo 'test'", true));
        return job;
    }

    private void createBuild(WorkflowJob job, String organization, String dataset) throws Exception {
        WorkflowRun run = job.scheduleBuild2(0, new ParametersAction(
                new StringParameterValue("ORGANISATION", organization),
                new StringParameterValue("DATASET", dataset))).get();
        assertEquals(1, run.getNumber());
    }

    private List<String> organizationIds(GretlDatenportalRootAction action) {
        return action.getOrganizationJobSummaries().stream()
                .map(DatenportalJobSummary::getOrganization)
                .toList();
    }

    private List<String> executedJobNames(GretlDatenportalRootAction action) {
        return action.getExecutedRuns().stream()
                .map(DatenportalRunSummary::getJobName)
                .toList();
    }

    private Authentication authentication(String username) {
        return new UsernamePasswordAuthenticationToken(username, "n/a", List.of());
    }
}
