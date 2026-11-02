package ch.so.agi.jenkins.gretldatenportal;

import java.nio.file.Path;

final class ConfiguredTopicRepository {
    private final String legacyPath;
    private final String url;
    private final String branch;
    private final TopicRepositoryMode mode;

    private ConfiguredTopicRepository(
            String legacyPath,
            String url,
            String branch,
            TopicRepositoryMode mode) {
        this.legacyPath = blankToEmpty(legacyPath);
        this.url = blankToEmpty(url);
        this.branch = blankToEmpty(branch);
        this.mode = mode == null ? TopicRepositoryMode.MANAGED_GIT : mode;
    }

    static ConfiguredTopicRepository resolve(
            String builderPath,
            String builderUrl,
            String builderBranch,
            GretlDatenportalGlobalConfiguration configuration) {
        String globalPath = configuration == null ? "" : configuration.getTopicRepositoryPath();
        String globalUrl = configuration == null ? "" : configuration.getTopicRepositoryUrl();
        String globalBranch = configuration == null ? "main" : configuration.getTopicRepositoryBranch();
        TopicRepositoryMode globalMode = configuration == null
                ? TopicRepositoryMode.MANAGED_GIT
                : configuration.getTopicRepositoryModeValue();

        if (!isBlank(builderPath)) {
            return new ConfiguredTopicRepository(builderPath, "", "", TopicRepositoryMode.WORKING_TREE);
        }
        if (!isBlank(builderUrl)) {
            return new ConfiguredTopicRepository(
                    "",
                    builderUrl,
                    isBlank(builderBranch) ? globalBranch : builderBranch,
                    TopicRepositoryMode.MANAGED_GIT);
        }
        if (!isBlank(globalUrl)) {
            String path = globalMode == TopicRepositoryMode.WORKING_TREE ? globalPath : "";
            return new ConfiguredTopicRepository(path, globalUrl, globalBranch, globalMode);
        }
        return new ConfiguredTopicRepository(globalPath, "", globalBranch, globalMode);
    }

    static ConfiguredTopicRepository fromGlobalConfiguration(GretlDatenportalGlobalConfiguration configuration) {
        return resolve("", "", "", configuration);
    }

    Path legacyPathAsPath() {
        return hasLegacyPath() ? Path.of(legacyPath) : null;
    }

    boolean hasLegacyPath() {
        return !legacyPath.isBlank();
    }

    boolean hasGitRepository() {
        return !url.isBlank();
    }

    boolean usesWorkingTree() {
        return mode == TopicRepositoryMode.WORKING_TREE;
    }

    String getUrl() {
        return url;
    }

    String getBranch() {
        return branch.isBlank() ? "main" : branch;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
