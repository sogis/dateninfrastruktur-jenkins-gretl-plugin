package ch.so.agi.jenkins.gretldatenportal;

import java.util.Objects;

public final class ResolvedDatenportalJob {
    private final OrganizationUnit organization;
    private final DatasetEntry dataset;
    private final GuiDefinition guiDefinition;
    private final NotificationConfiguration notificationConfiguration;

    public ResolvedDatenportalJob(
            OrganizationUnit organization,
            DatasetEntry dataset,
            GuiDefinition guiDefinition,
            NotificationConfiguration notificationConfiguration) {
        this.organization = Objects.requireNonNull(organization, "organization");
        this.dataset = Objects.requireNonNull(dataset, "dataset");
        this.guiDefinition = Objects.requireNonNull(guiDefinition, "guiDefinition");
        this.notificationConfiguration = notificationConfiguration == null
                ? NotificationConfiguration.disabled()
                : notificationConfiguration;
    }

    public OrganizationUnit getOrganization() {
        return organization;
    }

    public DatasetEntry getDataset() {
        return dataset;
    }

    public GuiDefinition getGuiDefinition() {
        return guiDefinition;
    }

    public NotificationConfiguration getNotificationConfiguration() {
        return notificationConfiguration;
    }
}
