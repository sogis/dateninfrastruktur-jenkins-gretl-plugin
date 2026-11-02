package ch.so.agi.jenkins.gretldatenportal;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class OrganizationUnit {
    private final String id;
    private final Path path;
    private final boolean jobDefinitionPresent;
    private final JobDefinition jobDefinition;
    private final GuiDefinition organizationGui;
    private final NotificationConfiguration notificationConfiguration;
    private final PermissionConfiguration permissionConfiguration;
    private final RepositoryDefaults repositoryDefaults;
    private final List<DatasetEntry> datasets;

    public OrganizationUnit(
            String id,
            Path path,
            boolean jobDefinitionPresent,
            JobDefinition jobDefinition,
            GuiDefinition organizationGui,
            NotificationConfiguration notificationConfiguration,
            List<DatasetEntry> datasets) {
        this(
                id,
                path,
                jobDefinitionPresent,
                jobDefinition,
                organizationGui,
                notificationConfiguration,
                PermissionConfiguration.empty(),
                RepositoryDefaults.empty(),
                datasets);
    }

    public OrganizationUnit(
            String id,
            Path path,
            boolean jobDefinitionPresent,
            JobDefinition jobDefinition,
            GuiDefinition organizationGui,
            NotificationConfiguration notificationConfiguration,
            PermissionConfiguration permissionConfiguration,
            List<DatasetEntry> datasets) {
        this(
                id,
                path,
                jobDefinitionPresent,
                jobDefinition,
                organizationGui,
                notificationConfiguration,
                permissionConfiguration,
                RepositoryDefaults.empty(),
                datasets);
    }

    public OrganizationUnit(
            String id,
            Path path,
            boolean jobDefinitionPresent,
            JobDefinition jobDefinition,
            GuiDefinition organizationGui,
            NotificationConfiguration notificationConfiguration,
            PermissionConfiguration permissionConfiguration,
            RepositoryDefaults repositoryDefaults,
            List<DatasetEntry> datasets) {
        this.id = Objects.requireNonNull(id, "id");
        this.path = Objects.requireNonNull(path, "path");
        this.jobDefinitionPresent = jobDefinitionPresent;
        this.jobDefinition = jobDefinition == null ? JobDefinition.defaultFor(id) : jobDefinition;
        this.organizationGui = organizationGui == null ? GuiDefinition.empty() : organizationGui;
        this.notificationConfiguration = notificationConfiguration == null
                ? NotificationConfiguration.disabled()
                : notificationConfiguration;
        this.permissionConfiguration = permissionConfiguration == null
                ? PermissionConfiguration.empty()
                : permissionConfiguration;
        this.repositoryDefaults = repositoryDefaults == null ? RepositoryDefaults.empty() : repositoryDefaults;
        this.datasets = List.copyOf(Objects.requireNonNull(datasets, "datasets"));
    }

    public String getId() {
        return id;
    }

    public Path getPath() {
        return path;
    }

    public String getPathString() {
        return path.toString();
    }

    public boolean isJobDefinitionPresent() {
        return jobDefinitionPresent;
    }

    public JobDefinition getJobDefinition() {
        return jobDefinition;
    }

    public GuiDefinition getOrganizationGui() {
        return organizationGui;
    }

    public NotificationConfiguration getNotificationConfiguration() {
        return notificationConfiguration;
    }

    public PermissionConfiguration getPermissionConfiguration() {
        return permissionConfiguration;
    }

    public RepositoryDefaults getRepositoryDefaults() {
        return repositoryDefaults;
    }

    public List<DatasetEntry> getDatasets() {
        return datasets;
    }

    public int getDatasetCount() {
        return datasets.size();
    }

    public String getDefaultJobName() {
        return jobDefinition.getJobName();
    }

    public DatasetEntry getDataset(String datasetId) {
        return datasets.stream()
                .filter(dataset -> dataset.getId().equals(datasetId))
                .findFirst()
                .orElse(null);
    }

    public String getFirstDatasetId() {
        return datasets.isEmpty() ? "" : datasets.get(0).getId();
    }
}
