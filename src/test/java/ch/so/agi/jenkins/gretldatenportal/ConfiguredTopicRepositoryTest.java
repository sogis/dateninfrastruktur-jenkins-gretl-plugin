package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class ConfiguredTopicRepositoryTest {
    @Test
    @WithJenkins
    void prefersGitConfigurationOverLegacyPath(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryPath("/tmp/legacy-repo");
        configuration.setTopicRepositoryUrl("file:///tmp/git-repo");
        configuration.setTopicRepositoryBranch("");
        configuration.setTopicRepositoryMode("managed-git");

        ConfiguredTopicRepository repository = ConfiguredTopicRepository.fromGlobalConfiguration(configuration);

        assertTrue(repository.hasGitRepository());
        assertFalse(repository.hasLegacyPath());
        assertEquals("file:///tmp/git-repo", repository.getUrl());
        assertEquals("main", repository.getBranch());
        assertFalse(repository.usesWorkingTree());
    }

    @Test
    @WithJenkins
    void fallsBackToLegacyPathWhenGitUrlIsMissing(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryPath("/tmp/legacy-repo");
        configuration.setTopicRepositoryUrl("");
        configuration.setTopicRepositoryBranch("develop");
        configuration.setTopicRepositoryMode("");

        ConfiguredTopicRepository repository = ConfiguredTopicRepository.fromGlobalConfiguration(configuration);

        assertFalse(repository.hasGitRepository());
        assertTrue(repository.hasLegacyPath());
        assertEquals(Path.of("/tmp/legacy-repo"), repository.legacyPathAsPath());
        assertEquals("develop", repository.getBranch());
        assertTrue(repository.usesWorkingTree());
    }

    @Test
    @WithJenkins
    void workingTreeModeUsesConfiguredPathEvenWhenUrlIsPresent(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryPath("/tmp/working-tree-repo");
        configuration.setTopicRepositoryUrl("file:///tmp/ignored-git-repo");
        configuration.setTopicRepositoryMode("working-tree");

        ConfiguredTopicRepository repository = ConfiguredTopicRepository.fromGlobalConfiguration(configuration);

        assertTrue(repository.hasGitRepository());
        assertTrue(repository.hasLegacyPath());
        assertTrue(repository.usesWorkingTree());
        assertEquals(Path.of("/tmp/working-tree-repo"), repository.legacyPathAsPath());
    }

    @Test
    @WithJenkins
    void rejectsUnknownRepositoryMode(JenkinsRule jenkinsRule) {
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryMode("invalid");

        assertThrows(IllegalArgumentException.class,
                () -> ConfiguredTopicRepository.fromGlobalConfiguration(configuration));
    }
}
