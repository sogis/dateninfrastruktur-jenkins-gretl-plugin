package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.tasks.Shell;
import hudson.triggers.TimerTrigger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class GretlDatenportalSeedJobProvisionerTest {
    @TempDir
    Path tempDir;

    @Test
    @WithJenkins
    void createsManagedSeedJobWhenRepositoryIsConfigured(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), true);

        new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        assertNotNull(seedJob);
        assertNotNull(seedJob.getProperty(GretlDatenportalManagedSeedJobProperty.class));
        assertFalse(seedJob.isDisabled());

        List<GretlDatenportalSeedBuilder> builders = seedBuilders(seedJob);
        assertEquals(1, builders.size());
        GretlDatenportalSeedBuilder builder = builders.get(0);
        assertEquals("", builder.getTopicRepositoryUrl());
        assertEquals("", builder.getTopicRepositoryBranch());
        assertEquals("", builder.getTopicRepositoryPath());
    }

    @Test
    @WithJenkins
    void configuresTimerTriggerWithDefaultCron(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), true);

        new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        TimerTrigger trigger = seedJob.getTrigger(TimerTrigger.class);
        assertNotNull(trigger);
        assertEquals("H/15 * * * *", trigger.getSpec());
    }

    @Test
    @WithJenkins
    void blankCronLeavesJobManualOnly(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), true);
        configuration.setSeedJobCron("");

        new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        assertNull(seedJob.getTrigger(TimerTrigger.class));
        assertFalse(seedJob.isDisabled());
    }

    @Test
    @WithJenkins
    void disablesSeedJobWhenRepositoryIsMissing(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryUrl("");
        configuration.setTopicRepositoryPath("");
        configuration.setSeedJobAutoCreate(true);

        new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        assertNotNull(seedJob);
        assertTrue(seedJob.isDisabled());
        assertNull(seedJob.getTrigger(TimerTrigger.class));
    }

    @Test
    @WithJenkins
    void automaticallyRunsInitialSeedOnceWhenRepositoryIsConfigured(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        Path repository = tempDir.resolve("repo");
        GitTestSupport.initRepository(repository);
        GitTestSupport.writeSharedJenkinsfile(repository);
        GitTestSupport.addOrganization(repository, "afu", "ch.so.abfall.deponien");
        GitTestSupport.commitAll(repository, "initial topics");

        GretlDatenportalGlobalConfiguration configuration = configureRepository(repository, true);
        GretlDatenportalSeedJobProvisioner provisioner = new GretlDatenportalSeedJobProvisioner();

        provisioner.provisionAndScheduleInitialSeed(jenkinsRule.jenkins, configuration);
        jenkinsRule.waitUntilNoActivity();

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        FreeStyleBuild initialBuild = seedJob.getLastBuild();
        assertNotNull(initialBuild);
        assertEquals(Result.SUCCESS, initialBuild.getResult());
        assertNotNull(jenkinsRule.jenkins.getItemByFullName("gretl-datenportal-afu", WorkflowJob.class));

        int buildCount = seedJob.getBuilds().size();
        provisioner.provisionAndScheduleInitialSeed(jenkinsRule.jenkins, configuration);
        jenkinsRule.waitUntilNoActivity();

        assertEquals(buildCount, seedJob.getBuilds().size());
    }

    @Test
    @WithJenkins
    void doesNotRunInitialSeedWithoutRepository(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryUrl("");
        configuration.setTopicRepositoryPath("");
        configuration.setSeedJobAutoCreate(true);

        GretlDatenportalSeedJobProvisioner provisioner = new GretlDatenportalSeedJobProvisioner();
        provisioner.provisionAndScheduleInitialSeed(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        assertTrue(seedJob.isDisabled());
        assertNull(seedJob.getLastBuild());
    }

    @Test
    @WithJenkins
    void autoCreateFalseDoesNotCreateJob(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), false);

        new GretlDatenportalSeedJobProvisioner().provisionAndScheduleInitialSeed(jenkinsRule.jenkins, configuration);

        assertNull(jenkinsRule.jenkins.getItem(GretlDatenportalSeedJobProvisioner.DEFAULT_SEED_JOB_NAME));
    }

    @Test
    @WithJenkins
    void autoCreateFalseDisablesExistingManagedJob(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), true);
        new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkinsRule.jenkins, configuration);
        assertFalse(seedJob(jenkinsRule).isDisabled());

        configuration.setSeedJobAutoCreate(false);
        new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        assertNotNull(seedJob);
        assertTrue(seedJob.isDisabled());
        assertNull(seedJob.getTrigger(TimerTrigger.class));
    }

    @Test
    @WithJenkins
    void isIdempotentAndDoesNotDuplicateBuilderOrTrigger(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), true);

        GretlDatenportalSeedJobProvisioner provisioner = new GretlDatenportalSeedJobProvisioner();
        provisioner.ensureSeedJob(jenkinsRule.jenkins, configuration);
        seedJob(jenkinsRule).getBuildersList().add(new Shell("echo must be removed"));
        provisioner.ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        assertEquals(1, seedJob.getBuildersList().size());
        assertEquals(1, seedBuilders(seedJob).size());
        assertEquals(1, seedJob.getTriggers().size());
        assertEquals(
                1,
                seedJob.getAllProperties().stream()
                        .filter(GretlDatenportalManagedSeedJobProperty.class::isInstance)
                        .count());
    }

    @Test
    @WithJenkins
    void doesNotOverwriteExistingFreestyleJobWithoutMarker(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        FreeStyleProject existing =
                jenkinsRule.createFreeStyleProject(GretlDatenportalSeedJobProvisioner.DEFAULT_SEED_JOB_NAME);
        existing.setDescription("user managed");
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), true);

        GretlDatenportalSeedJobProvisioner provisioner = new GretlDatenportalSeedJobProvisioner();
        assertThrows(IOException.class, () -> provisioner.ensureSeedJob(jenkinsRule.jenkins, configuration));

        FreeStyleProject afterwards = seedJob(jenkinsRule);
        assertEquals("user managed", afterwards.getDescription());
        assertTrue(seedBuilders(afterwards).isEmpty());
    }

    @Test
    @WithJenkins
    void doesNotOverwriteExistingItemWithDifferentType(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        jenkinsRule.jenkins.createProject(
                WorkflowJob.class, GretlDatenportalSeedJobProvisioner.DEFAULT_SEED_JOB_NAME);
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), true);

        GretlDatenportalSeedJobProvisioner provisioner = new GretlDatenportalSeedJobProvisioner();
        assertThrows(IOException.class, () -> provisioner.ensureSeedJob(jenkinsRule.jenkins, configuration));
    }

    @Test
    @WithJenkins
    void managedSeedJobCanGenerateOrganizationJobs(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        Path repository = tempDir.resolve("repo");
        GitTestSupport.initRepository(repository);
        GitTestSupport.writeSharedJenkinsfile(repository);
        GitTestSupport.addOrganization(repository, "afu", "ch.so.abfall.deponien");
        GitTestSupport.commitAll(repository, "initial topics");

        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryUrl(GitTestSupport.fileUrl(repository));
        configuration.setTopicRepositoryBranch("main");
        configuration.setTopicRepositoryPath("");
        configuration.setSeedJobAutoCreate(true);

        new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        jenkinsRule.buildAndAssertSuccess(seedJob);
        assertNotNull(jenkinsRule.jenkins.getItemByFullName("gretl-datenportal-afu", WorkflowJob.class));
    }

    @Test
    @WithJenkins
    void updatesManagedSeedJobWhenCronChanges(JenkinsRule jenkinsRule) throws Exception {
        resetSeedJob(jenkinsRule);
        GretlDatenportalGlobalConfiguration configuration = configureRepository(tempDir.resolve("repo"), true);

        GretlDatenportalSeedJobProvisioner provisioner = new GretlDatenportalSeedJobProvisioner();
        provisioner.ensureSeedJob(jenkinsRule.jenkins, configuration);

        configuration.setSeedJobCron("H H * * *");
        provisioner.ensureSeedJob(jenkinsRule.jenkins, configuration);

        FreeStyleProject seedJob = seedJob(jenkinsRule);
        assertEquals(1, seedJob.getTriggers().size());
        TimerTrigger trigger = seedJob.getTrigger(TimerTrigger.class);
        assertNotNull(trigger);
        assertEquals("H H * * *", trigger.getSpec());
    }

    private GretlDatenportalGlobalConfiguration configureRepository(Path repository, boolean autoCreate) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryUrl("file://" + repository.toAbsolutePath());
        configuration.setTopicRepositoryBranch("main");
        configuration.setTopicRepositoryPath("");
        configuration.setSeedJobAutoCreate(autoCreate);
        return configuration;
    }

    private FreeStyleProject seedJob(JenkinsRule jenkinsRule) {
        TopLevelItem item = jenkinsRule.jenkins.getItem(GretlDatenportalSeedJobProvisioner.DEFAULT_SEED_JOB_NAME);
        assertInstanceOf(FreeStyleProject.class, item);
        return (FreeStyleProject) item;
    }

    private void resetSeedJob(JenkinsRule jenkinsRule) throws Exception {
        TopLevelItem item = jenkinsRule.jenkins.getItem(GretlDatenportalSeedJobProvisioner.DEFAULT_SEED_JOB_NAME);
        if (item != null) {
            item.delete();
        }
    }

    private List<GretlDatenportalSeedBuilder> seedBuilders(FreeStyleProject project) {
        return project.getBuildersList().stream()
                .filter(GretlDatenportalSeedBuilder.class::isInstance)
                .map(GretlDatenportalSeedBuilder.class::cast)
                .toList();
    }
}
