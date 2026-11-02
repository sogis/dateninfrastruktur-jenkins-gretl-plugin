package ch.so.agi.jenkins.gretldatenportal;

public final class DatenportalJobSummary {
    private final OrganizationUnit organization;
    private final boolean canBuild;

    public DatenportalJobSummary(OrganizationUnit organization, boolean canBuild) {
        this.organization = organization;
        this.canBuild = canBuild;
    }

    public String getOrganization() {
        return organization.getId();
    }

    public String getTitle() {
        return organization.getJobDefinition().getTitle();
    }

    public String getDescription() {
        return organization.getJobDefinition().getDescription();
    }

    public String getJobName() {
        return organization.getDefaultJobName();
    }

    public int getDatasetCount() {
        return organization.getDatasetCount();
    }

    public String getFirstDatasetId() {
        return organization.getFirstDatasetId();
    }

    public boolean isStartable() {
        return canBuild && organization.isJobDefinitionPresent() && organization.getDatasetCount() > 0;
    }

    public boolean isCanBuild() {
        return canBuild;
    }

    public String getStatus() {
        if (!canBuild) {
            return "no-permission";
        }
        if (!organization.isJobDefinitionPresent()) {
            return "missing-job";
        }
        if (organization.getDatasetCount() == 0) {
            return "no-datasets";
        }
        return "ready";
    }

    public String getStatusLabel() {
        return switch (getStatus()) {
            case "no-permission" -> "Keine Berechtigung";
            case "missing-job" -> "Jobdefinition fehlt";
            case "no-datasets" -> "Keine Datensätze";
            default -> "";
        };
    }
}
