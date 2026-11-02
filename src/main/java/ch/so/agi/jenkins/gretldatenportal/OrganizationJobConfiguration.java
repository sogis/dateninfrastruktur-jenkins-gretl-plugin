package ch.so.agi.jenkins.gretldatenportal;

import java.util.Objects;

public final class OrganizationJobConfiguration {
    private final JobDefinition jobDefinition;
    private final GuiDefinition guiDefinition;
    private final NotificationConfiguration notificationConfiguration;
    private final PermissionConfiguration permissionConfiguration;

    public OrganizationJobConfiguration(
            JobDefinition jobDefinition,
            GuiDefinition guiDefinition,
            NotificationConfiguration notificationConfiguration) {
        this(jobDefinition, guiDefinition, notificationConfiguration, PermissionConfiguration.empty());
    }

    public OrganizationJobConfiguration(
            JobDefinition jobDefinition,
            GuiDefinition guiDefinition,
            NotificationConfiguration notificationConfiguration,
            PermissionConfiguration permissionConfiguration) {
        this.jobDefinition = Objects.requireNonNull(jobDefinition, "jobDefinition");
        this.guiDefinition = guiDefinition == null ? GuiDefinition.empty() : guiDefinition;
        this.notificationConfiguration = notificationConfiguration == null
                ? NotificationConfiguration.disabled()
                : notificationConfiguration;
        this.permissionConfiguration = permissionConfiguration == null
                ? PermissionConfiguration.empty()
                : permissionConfiguration;
    }

    public JobDefinition getJobDefinition() {
        return jobDefinition;
    }

    public GuiDefinition getGuiDefinition() {
        return guiDefinition;
    }

    public NotificationConfiguration getNotificationConfiguration() {
        return notificationConfiguration;
    }

    public PermissionConfiguration getPermissionConfiguration() {
        return permissionConfiguration;
    }
}
