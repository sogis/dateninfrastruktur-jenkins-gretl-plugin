package ch.so.agi.jenkins.gretldatenportal;

import hudson.BulkChange;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.TopLevelItem;
import hudson.tasks.Builder;
import hudson.tasks.LogRotator;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

public class GretlDatenportalSeedJobProvisioner {
    static final String DEFAULT_SEED_JOB_NAME = "gretl-datenportal-seed";
    static final String DEFAULT_SEED_JOB_CRON = "H/15 * * * *";
    private static final String SEED_JOB_DESCRIPTION =
            "Plugin-managed seed job for GRETL Datenportal generated jobs. Do not edit manually; "
                    + "configure it under Manage Jenkins -> System -> GRETL Datenportal Jobs.";
    private static final Logger LOGGER =
            Logger.getLogger(GretlDatenportalSeedJobProvisioner.class.getName());

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void provisionAfterJobsLoaded() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            LOGGER.log(Level.FINE, "Skipping seed job provisioning because Jenkins is not available.");
            return;
        }
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        if (configuration == null) {
            LOGGER.log(Level.FINE, "Skipping seed job provisioning because configuration is not available.");
            return;
        }
        try {
            new GretlDatenportalSeedJobProvisioner().provisionAndScheduleInitialSeed(jenkins, configuration);
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not provision GRETL Datenportal seed job.", e);
        }
    }

    public static void provisionFromConfiguration(GretlDatenportalGlobalConfiguration configuration) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null || configuration == null) {
            return;
        }
        try {
            new GretlDatenportalSeedJobProvisioner().provisionAndScheduleInitialSeed(jenkins, configuration);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void provisionAndScheduleInitialSeed(
            Jenkins jenkins, GretlDatenportalGlobalConfiguration configuration) throws IOException {
        ensureSeedJob(jenkins, configuration);
        scheduleInitialSeed(jenkins, configuration);
    }

    void ensureSeedJob(Jenkins jenkins, GretlDatenportalGlobalConfiguration configuration) throws IOException {
        if (!configuration.isSeedJobAutoCreate()) {
            Item existing = jenkins.getItem(DEFAULT_SEED_JOB_NAME);
            if (existing instanceof FreeStyleProject project && isManagedSeedJob(project)) {
                try (BulkChange bulkChange = new BulkChange(project)) {
                    configureTimerTrigger(project, configuration.getSeedJobCron(), false);
                    configureDisabledState(project, false);
                    project.save();
                    bulkChange.commit();
                }
            }
            return;
        }

        FreeStyleProject project = getOrCreateManagedSeedJob(jenkins);
        try (BulkChange bulkChange = new BulkChange(project)) {
            configureProject(project, configuration);
            project.save();
            bulkChange.commit();
        }
    }

    private FreeStyleProject getOrCreateManagedSeedJob(Jenkins jenkins) throws IOException {
        TopLevelItem existing = jenkins.getItem(DEFAULT_SEED_JOB_NAME);
        if (existing == null) {
            FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, DEFAULT_SEED_JOB_NAME);
            markManaged(project);
            return project;
        }
        if (existing instanceof FreeStyleProject project) {
            if (isManagedSeedJob(project)) {
                return project;
            }
            throw new IOException("Cannot manage GRETL Datenportal seed job '" + DEFAULT_SEED_JOB_NAME
                    + "': item already exists and is not plugin-managed.");
        }
        throw new IOException("Cannot create GRETL Datenportal seed job '" + DEFAULT_SEED_JOB_NAME
                + "': item already exists with another type.");
    }

    private boolean isManagedSeedJob(FreeStyleProject project) {
        return project.getProperty(GretlDatenportalManagedSeedJobProperty.class) != null;
    }

    private void markManaged(FreeStyleProject project) throws IOException {
        if (!isManagedSeedJob(project)) {
            project.addProperty(new GretlDatenportalManagedSeedJobProperty());
        }
    }

    private void configureProject(FreeStyleProject project, GretlDatenportalGlobalConfiguration configuration)
            throws IOException {
        configureDescription(project);
        markManaged(project);
        configureBuilders(project);
        configureBuildDiscarder(project, configuration.getSeedJobBuildsToKeep());
        boolean active = configuration.isSeedJobAutoCreate() && hasConfiguredTopicRepository(configuration);
        configureDisabledState(project, active);
        configureTimerTrigger(project, configuration.getSeedJobCron(), active);
    }

    private void configureDescription(FreeStyleProject project) throws IOException {
        project.setDescription(SEED_JOB_DESCRIPTION);
    }

    private void configureBuilders(FreeStyleProject project) throws IOException {
        DescribableList<Builder, ?> builders = project.getBuildersList();
        builders.clear();
        builders.add(new GretlDatenportalSeedBuilder("", "", ""));
    }

    private void configureBuildDiscarder(FreeStyleProject project, int buildsToKeep) throws IOException {
        project.setBuildDiscarder(new LogRotator(-1, buildsToKeep, -1, -1));
    }

    private void configureDisabledState(FreeStyleProject project, boolean active) throws IOException {
        if (active) {
            if (project.isDisabled()) {
                project.setDisabled(false);
            }
        } else if (!project.isDisabled()) {
            project.setDisabled(true);
        }
    }

    private void configureTimerTrigger(FreeStyleProject project, String cron, boolean active) throws IOException {
        Trigger<?> existing = project.getTrigger(TimerTrigger.class);
        if (existing != null) {
            project.removeTrigger(existing.getDescriptor());
        }
        if (!active) {
            return;
        }
        if (cron == null || cron.isBlank()) {
            return;
        }
        TimerTrigger trigger = new TimerTrigger(cron.trim());
        project.addTrigger(trigger);
        trigger.start(project, true);
    }

    private boolean hasConfiguredTopicRepository(GretlDatenportalGlobalConfiguration configuration) {
        return configuration.isTopicRepositoryConfigured();
    }

    private void scheduleInitialSeed(Jenkins jenkins, GretlDatenportalGlobalConfiguration configuration) {
        if (!configuration.isSeedJobAutoCreate() || !hasConfiguredTopicRepository(configuration)) {
            return;
        }

        Item item = jenkins.getItem(DEFAULT_SEED_JOB_NAME);
        if (!(item instanceof FreeStyleProject project) || !isManagedSeedJob(project)) {
            return;
        }
        if (project.isDisabled()
                || project.getLastBuild() != null
                || project.isBuilding()
                || Queue.getInstance().getItem(project) != null) {
            return;
        }

        LOGGER.log(Level.INFO, "Scheduling initial GRETL Datenportal seed job.");
        try {
            if (project.scheduleBuild2(0) == null) {
                LOGGER.log(Level.WARNING, "Could not schedule initial GRETL Datenportal seed job.");
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not schedule initial GRETL Datenportal seed job.", e);
        }
    }
}
