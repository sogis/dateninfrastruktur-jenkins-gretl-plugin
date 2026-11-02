package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class GretlDatenportalSeedBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    @WithJenkins
    void generatesAndUpdatesJobsFromManagedGitCheckout(JenkinsRule jenkinsRule) throws Exception {
        Path sourceRepository = tempDir.resolve("source-repo");
        GitTestSupport.initRepository(sourceRepository);
        GitTestSupport.writeSharedJenkinsfile(sourceRepository);
        GitTestSupport.writeTeams(sourceRepository);
        GitTestSupport.addOrganization(sourceRepository, "afu", "ch.so.abfall.deponien");
        Files.createDirectories(sourceRepository.resolve("gradle/wrapper"));
        GitTestSupport.commitAll(sourceRepository, "initial topics");

        FreeStyleProject project = jenkinsRule.createFreeStyleProject("seed");
        project.getBuildersList().add(new GretlDatenportalSeedBuilder(
                "",
                GitTestSupport.fileUrl(sourceRepository),
                "main"));

        jenkinsRule.configRoundtrip(project);
        jenkinsRule.buildAndAssertSuccess(project);
        assertNotNull(jenkinsRule.jenkins.getItemByFullName("gretl-datenportal-afu", WorkflowJob.class));

        var context = jenkinsRule.jenkins.getItemByFullName("gretl-datenportal-afu", WorkflowJob.class)
                .getProperty(GretlDatenportalManagedJobProperty.class);
        assertEquals(GitTestSupport.fileUrl(sourceRepository), context.repositoryUrl());
        assertEquals("main", context.repositoryBranch());
        assertEquals("managed-git", context.repositoryMode());

        GitTestSupport.addOrganization(sourceRepository, "statistikdienst", "ch.so.statistik.bevoelkerung");
        Files.createDirectories(sourceRepository.resolve("statistikdienst/ch.so.statistik.bevoelkerung/examples"));
        GitTestSupport.commitAll(sourceRepository, "add statistikdienst");

        jenkinsRule.buildAndAssertSuccess(project);
        assertNotNull(jenkinsRule.jenkins.getItemByFullName("gretl-datenportal-statistikdienst", WorkflowJob.class));
    }

    @Test
    @WithJenkins
    void ignoresBrokenTopLevelFolderWithoutFailingSeedRun(JenkinsRule jenkinsRule) throws Exception {
        Path sourceRepository = tempDir.resolve("source-repo");
        GitTestSupport.initRepository(sourceRepository);
        GitTestSupport.writeSharedJenkinsfile(sourceRepository);
        GitTestSupport.addOrganization(sourceRepository, "afu", "ch.so.abfall.deponien");
        Path brokenDatasetPath = Files.createDirectories(
                sourceRepository.resolve("statistikdienst/ch.so.statistik.bevoelkerung"));
        Files.writeString(
                brokenDatasetPath.resolve("dataset.xtf"),
                GitTestSupport.datasetXml(
                        "ch.so.statistik.bevoelkerung",
                        "Bevoelkerung",
                        "Broken top-level folder fixture",
                        false));
        GitTestSupport.commitAll(sourceRepository, "topics with broken top-level folder");

        FreeStyleProject project = jenkinsRule.createFreeStyleProject("seed-with-warning");
        project.getBuildersList().add(new GretlDatenportalSeedBuilder(
                "",
                GitTestSupport.fileUrl(sourceRepository),
                "main"));

        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);
        jenkinsRule.assertLogContains("WARNING: Skipping folder because it has dataset-like child directories but no gretl-datenportal-job.yaml.", build);
        assertNotNull(jenkinsRule.jenkins.getItemByFullName("gretl-datenportal-afu", WorkflowJob.class));
    }

    @Test
    @WithJenkins
    void failsWithEnglishSummaryWhenTopicRepositoryValidationFails(JenkinsRule jenkinsRule) throws Exception {
        Path sourceRepository = tempDir.resolve("source-repo");
        GitTestSupport.initRepository(sourceRepository);
        GitTestSupport.writeSharedJenkinsfile(sourceRepository);
        GitTestSupport.writeTeams(sourceRepository);
        Path organizationPath = Files.createDirectories(sourceRepository.resolve("afu"));
        Files.writeString(
                organizationPath.resolve("gretl-datenportal-job.yaml"),
                """
                permissions:
                  read:
                    - team: datenportal-read
                """);
        GitTestSupport.commitAll(sourceRepository, "invalid permissions");

        FreeStyleProject project = jenkinsRule.createFreeStyleProject("seed-invalid");
        project.getBuildersList().add(new GretlDatenportalSeedBuilder(
                "",
                GitTestSupport.fileUrl(sourceRepository),
                "main"));

        FreeStyleBuild build = jenkinsRule.buildAndAssertStatus(Result.FAILURE, project);
        jenkinsRule.assertLogContains("ERROR: permissions.build must contain at least one team.", build);
        jenkinsRule.assertLogContains("ERROR: Topic repository validation failed.", build);
    }

    @Test
    @WithJenkins
    void failsWithEnglishMessageWhenTopicRepositoryPathIsMissing(JenkinsRule jenkinsRule) throws Exception {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("seed-missing-config");
        project.getBuildersList().add(new GretlDatenportalSeedBuilder("", "", ""));

        FreeStyleBuild build = jenkinsRule.buildAndAssertStatus(Result.FAILURE, project);
        jenkinsRule.assertLogContains("ERROR: Topic repository path is not configured.", build);
    }

    @Test
    @WithJenkins
    void seedOperatorCanRunSeedWithoutGlobalConfigurePermission(JenkinsRule jenkinsRule) throws Exception {
        Path sourceRepository = tempDir.resolve("operator-repo");
        GitTestSupport.initRepository(sourceRepository);
        GitTestSupport.writeSharedJenkinsfile(sourceRepository);
        GitTestSupport.addOrganization(sourceRepository, "afu", "ch.so.abfall.deponien");
        GitTestSupport.commitAll(sourceRepository, "operator seed fixture");

        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryUrl(GitTestSupport.fileUrl(sourceRepository));
        configuration.setTopicRepositoryBranch("main");
        new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = (FreeStyleProject) jenkinsRule.jenkins.getItem(
                GretlDatenportalSeedJobProvisioner.DEFAULT_SEED_JOB_NAME);
        new GretlDatenportalAuthorizationSynchronizer().synchronizeSeedJob(
                seedJob,
                new TeamDirectory(Map.of(
                        "gretl-datenportal-seed-operators", Set.of("seed-user"))),
                configuration.getSeedJobOperatorsTeam());

        ProjectMatrixAuthorizationStrategy authorization = new ProjectMatrixAuthorizationStrategy();
        authorization.getGrantedPermissionEntries()
                .computeIfAbsent(Jenkins.READ, ignored -> new HashSet<>())
                .add(PermissionEntry.user("seed-user"));
        jenkinsRule.jenkins.setAuthorizationStrategy(authorization);

        Authentication operator = new UsernamePasswordAuthenticationToken("seed-user", "n/a", List.of());
        assertTrue(seedJob.getACL().hasPermission2(operator, Item.BUILD));
        assertFalse(jenkinsRule.jenkins.getACL().hasPermission2(operator, Item.CONFIGURE));

        QueueTaskFuture<FreeStyleBuild> future;
        try (ACLContext ignored = ACL.as2(operator)) {
            future = seedJob.scheduleBuild2(0);
        }
        assertNotNull(future);
        FreeStyleBuild build = future.get(60, TimeUnit.SECONDS);
        assertNotNull(build);
        assertEquals(Result.SUCCESS, build.getResult());
    }
}
