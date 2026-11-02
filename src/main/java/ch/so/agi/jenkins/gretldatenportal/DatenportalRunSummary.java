package ch.so.agi.jenkins.gretldatenportal;

import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public final class DatenportalRunSummary {
    private static final DateTimeFormatter START_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final WorkflowRun run;
    private final String organization;
    private final String dataset;
    private final String datasetTitle;

    public DatenportalRunSummary(WorkflowRun run) {
        this(run, run.getAction(ParametersAction.class), "");
    }

    public DatenportalRunSummary(WorkflowRun run, OrganizationUnit organizationUnit) {
        this(run, run.getAction(ParametersAction.class), datasetTitle(run.getAction(ParametersAction.class), organizationUnit));
    }

    DatenportalRunSummary(WorkflowRun run, ParametersAction parameters) {
        this(run, parameters, "");
    }

    DatenportalRunSummary(WorkflowRun run, ParametersAction parameters, String datasetTitle) {
        this.run = run;
        this.organization = parameterValue(parameters, "ORGANISATION");
        this.dataset = parameterValue(parameters, "DATASET");
        this.datasetTitle = datasetTitle == null ? "" : datasetTitle;
    }

    public int getBuildNumber() {
        return run.getNumber();
    }

    public String getJobName() {
        return run.getParent().getName();
    }

    public String getJobFullName() {
        return run.getParent().getFullName();
    }

    public String getJobNameEncoded() {
        return URLEncoder.encode(getJobFullName(), StandardCharsets.UTF_8);
    }

    public String getOrganization() {
        return blankToDash(organization);
    }

    public String getOrganizationValue() {
        return organization;
    }

    public String getDataset() {
        return blankToDash(dataset);
    }

    public String getDatasetTitle() {
        if (!datasetTitle.isBlank()) {
            return datasetTitle;
        }
        if (!dataset.isBlank()) {
            return dataset;
        }
        return getJobName();
    }

    public String getDatasetValue() {
        return dataset;
    }

    public String getStatus() {
        return run.getResult() == null ? "RUNNING" : run.getResult().toString();
    }

    public String getStatusLabel() {
        return statusLabel(getStatus());
    }

    public String getStartTime() {
        return START_TIME_FORMATTER.format(Instant.ofEpochMilli(getStartTimeMillis()));
    }

    public long getStartTimeMillis() {
        return run.getTimeInMillis();
    }

    public String getDuration() {
        return run.getDurationString();
    }

    public String getDisplayTitle() {
        return getDatasetTitle() + " | " + getOrganization() + " (#" + getBuildNumber() + ")";
    }

    static String parameterValue(ParametersAction parameters, String name) {
        if (parameters == null) {
            return "";
        }
        ParameterValue parameter = parameters.getParameter(name);
        Object value = parameter == null ? null : parameter.getValue();
        return value == null ? "" : value.toString();
    }

    static String statusLabel(String status) {
        return switch (status) {
            case "SUCCESS" -> "Erfolgreich";
            case "FAILURE" -> "Fehlgeschlagen";
            case "UNSTABLE" -> "Instabil";
            case "ABORTED" -> "Abgebrochen";
            case "QUEUED" -> "Wartet";
            case "RUNNING" -> "Läuft";
            case "NOT_BUILT" -> "Nicht gebaut";
            default -> status;
        };
    }

    private static String datasetTitle(ParametersAction parameters, OrganizationUnit organization) {
        if (organization == null) {
            return "";
        }
        DatasetEntry dataset = organization.getDataset(parameterValue(parameters, "DATASET"));
        return dataset == null ? "" : dataset.getDefinition().getTitle();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "–" : value;
    }
}
