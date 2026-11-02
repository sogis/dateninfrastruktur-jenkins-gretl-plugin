package ch.so.agi.jenkins.gretldatenportal;

import java.nio.file.Path;

public final class RepositoryDefaults {
    private final Path sharedPath;
    private final boolean defaultsFilePresent;
    private final String gradleTask;
    private final Integer timeoutMinutes;
    private final String jenkinsfile;
    private final GuiDefinition guiDefinition;
    private final NotificationConfiguration notificationConfiguration;

    public RepositoryDefaults(
            Path sharedPath,
            boolean defaultsFilePresent,
            String gradleTask,
            Integer timeoutMinutes,
            String jenkinsfile,
            GuiDefinition guiDefinition,
            NotificationConfiguration notificationConfiguration) {
        this.sharedPath = sharedPath;
        this.defaultsFilePresent = defaultsFilePresent;
        this.gradleTask = gradleTask == null ? "" : gradleTask.trim();
        this.timeoutMinutes = timeoutMinutes == null || timeoutMinutes <= 0 ? null : timeoutMinutes;
        this.jenkinsfile = jenkinsfile == null ? "" : jenkinsfile.trim();
        this.guiDefinition = guiDefinition == null ? GuiDefinition.empty() : guiDefinition;
        this.notificationConfiguration = notificationConfiguration == null
                ? NotificationConfiguration.empty()
                : notificationConfiguration;
    }

    public static RepositoryDefaults empty() {
        return new RepositoryDefaults(null, false, "", null, "", GuiDefinition.empty(), NotificationConfiguration.empty());
    }

    public static RepositoryDefaults forSharedPath(Path sharedPath) {
        return new RepositoryDefaults(
                sharedPath,
                false,
                "",
                null,
                "",
                GuiDefinition.empty(),
                NotificationConfiguration.empty());
    }

    public Path getSharedPath() {
        return sharedPath;
    }

    public boolean hasSharedPath() {
        return sharedPath != null;
    }

    public boolean isDefaultsFilePresent() {
        return defaultsFilePresent;
    }

    public String getGradleTask() {
        return gradleTask;
    }

    public boolean hasGradleTask() {
        return !gradleTask.isBlank();
    }

    public Integer getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public boolean hasTimeoutMinutes() {
        return timeoutMinutes != null;
    }

    public String getJenkinsfile() {
        return jenkinsfile;
    }

    public boolean hasJenkinsfile() {
        return !jenkinsfile.isBlank();
    }

    public GuiDefinition getGuiDefinition() {
        return guiDefinition;
    }

    public NotificationConfiguration getNotificationConfiguration() {
        return notificationConfiguration;
    }
}
