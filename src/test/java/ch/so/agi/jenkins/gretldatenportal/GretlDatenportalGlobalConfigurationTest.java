package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.FormValidation;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class GretlDatenportalGlobalConfigurationTest {

    @Test
    @WithJenkins
    void displayNameUsesNewDefaultAndPreservesCustomNames(JenkinsRule j) {
        var configuration = GretlDatenportalGlobalConfiguration.get();
        assertEquals("Datenportal Jobs", configuration.getDisplayName());
        for (String oldDefault : new String[] {null, "", "  ", "GRETL Datenportal Jobs"}) {
            configuration.setDisplayName(oldDefault);
            configuration.load();
            assertEquals("Datenportal Jobs", configuration.getDisplayName());
        }
        configuration.setDisplayName("Eigene Datenlieferungen");
        configuration.load();
        assertEquals("Eigene Datenlieferungen", configuration.getDisplayName());
    }

    @Test
    @WithJenkins
    void writeBackDefaultsToOffAndConfigurationSurvivesReload(JenkinsRule j) throws Exception {
        var config = GretlDatenportalGlobalConfiguration.get();
        assertFalse(config.isTopicRepositoryWriteBackEnabled());
        config.setTopicRepositoryCredentialsId("git-token");
        config.setTopicRepositoryWriteBackEnabled(true);
        config.setTopicRepositoryCommitterName("Delivery Bot");
        config.setTopicRepositoryCommitterEmail("bot@example.invalid");
        config.save();
        config.load();
        assertEquals("git-token", config.getTopicRepositoryCredentialsId());
        assertTrue(config.isTopicRepositoryWriteBackEnabled());
        assertEquals("Delivery Bot", config.getTopicRepositoryCommitterName());
        assertEquals("bot@example.invalid", config.getTopicRepositoryCommitterEmail());
    }

    @Test
    @WithJenkins
    void defaultsAreReturnedForUnsetSeedJobFields(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();

        assertTrue(configuration.isSeedJobAutoCreate());
        assertEquals("H/15 * * * *", configuration.getSeedJobCron());
        assertEquals(20, configuration.getSeedJobBuildsToKeep());
        assertEquals("gretl-datenportal-seed-operators", configuration.getSeedJobOperatorsTeam());
        assertEquals("managed-git", configuration.getTopicRepositoryMode());
    }

    @Test
    @WithJenkins
    void blankCronIsPreservedButDefaultReplacesNull(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();

        configuration.setSeedJobCron("");
        assertEquals("", configuration.getSeedJobCron());

        configuration.setSeedJobCron("  H H * * *  ");
        assertEquals("H H * * *", configuration.getSeedJobCron());
    }

    @Test
    @WithJenkins
    void buildsToKeepFallsBackToDefaultForNonPositiveValues(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();

        configuration.setSeedJobBuildsToKeep(0);
        assertEquals(20, configuration.getSeedJobBuildsToKeep());

        configuration.setSeedJobBuildsToKeep(-5);
        assertEquals(20, configuration.getSeedJobBuildsToKeep());

        configuration.setSeedJobBuildsToKeep(7);
        assertEquals(7, configuration.getSeedJobBuildsToKeep());
    }

    @Test
    @WithJenkins
    void topicRepositoryConfiguredReflectsUrlOrPath(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();

        configuration.setTopicRepositoryUrl("");
        configuration.setTopicRepositoryPath("");
        assertFalse(configuration.isTopicRepositoryConfigured());

        configuration.setTopicRepositoryUrl("file:///tmp/repo");
        assertTrue(configuration.isTopicRepositoryConfigured());

        configuration.setTopicRepositoryUrl("");
        configuration.setTopicRepositoryPath("/tmp/repo");
        assertTrue(configuration.isTopicRepositoryConfigured());
    }

    @Test
    @WithJenkins
    void repositoryModeIsTrimmedAndValidated(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();

        configuration.setTopicRepositoryMode("  working-tree  ");
        assertEquals("working-tree", configuration.getTopicRepositoryMode());
        assertEquals(FormValidation.Kind.OK, configuration.doCheckTopicRepositoryMode("managed-git").kind);
        assertEquals(FormValidation.Kind.OK, configuration.doCheckTopicRepositoryMode("working-tree").kind);
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckTopicRepositoryMode("other").kind);
    }

    @Test
    @WithJenkins
    void cronValidationAllowsBlankAndRejectsInvalid(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();

        assertEquals(FormValidation.Kind.OK, configuration.doCheckSeedJobCron("").kind);
        assertEquals(FormValidation.Kind.OK, configuration.doCheckSeedJobCron("H/15 * * * *").kind);
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckSeedJobCron("not a cron").kind);
    }

    @Test
    @WithJenkins
    void buildsToKeepValidationRequiresPositiveInteger(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();

        assertEquals(FormValidation.Kind.OK, configuration.doCheckSeedJobBuildsToKeep("20").kind);
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckSeedJobBuildsToKeep("0").kind);
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckSeedJobBuildsToKeep("-3").kind);
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckSeedJobBuildsToKeep("abc").kind);
    }

    @Test
    @WithJenkins
    void seedJobOperatorsTeamIsTrimmedAndValidated(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();

        configuration.setSeedJobOperatorsTeam("  operators  ");
        assertEquals("operators", configuration.getSeedJobOperatorsTeam());
        assertEquals(FormValidation.Kind.OK, configuration.doCheckSeedJobOperatorsTeam("operators").kind);
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckSeedJobOperatorsTeam("not valid").kind);
    }
}
