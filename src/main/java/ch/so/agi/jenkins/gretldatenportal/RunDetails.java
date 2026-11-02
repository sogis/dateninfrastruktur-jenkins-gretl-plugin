package ch.so.agi.jenkins.gretldatenportal;

import hudson.console.ConsoleNote;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.file_parameters.AbstractFileParameterValue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.nio.charset.StandardCharsets;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public final class RunDetails {
    private static final DateTimeFormatter START_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final int MAX_LOG_LINES = 200;

    private final WorkflowJob job;
    private final WorkflowRun run;
    private final Queue.Item queueItem;
    private final Long queueId;

    public RunDetails(WorkflowRun run) {
        this.job = run.getParent();
        this.run = run;
        this.queueItem = null;
        this.queueId = run.getQueueId() == Run.QUEUE_ID_UNKNOWN ? null : run.getQueueId();
    }

    public RunDetails(WorkflowJob job, Queue.Item queueItem, Long queueId) {
        this.job = job;
        this.run = null;
        this.queueItem = queueItem;
        this.queueId = queueId;
    }

    public WorkflowRun getRun() {
        return run;
    }

    public WorkflowJob getJob() {
        return job;
    }

    public boolean hasRun() {
        return run != null;
    }

    public Long getQueueId() {
        return queueId;
    }

    public String getStatus() {
        if (run == null) {
            return "QUEUED";
        }
        return run.getResult() == null ? "RUNNING" : run.getResult().toString();
    }

    public String getStatusLabel() {
        return DatenportalRunSummary.statusLabel(getStatus());
    }

    public boolean isComplete() {
        return run != null && run.getResult() != null;
    }

    public String getHeadline() {
        return hasRun() ? getJobFullDisplayName() + " #" + run.getNumber() : getJobFullDisplayName();
    }

    public String getPrimaryLabel() {
        if (hasRun()) {
            return "Build #" + run.getNumber();
        }
        return queueId == null ? "Build startet" : "Queue #" + queueId;
    }

    public String getJobFullDisplayName() {
        return job == null ? "" : job.getFullDisplayName();
    }

    public String getJobFullName() {
        return job == null ? "" : job.getFullName();
    }

    public String getJobNameEncoded() {
        return URLEncoder.encode(getJobFullName(), StandardCharsets.UTF_8);
    }

    public Integer getBuildNumber() {
        return hasRun() ? run.getNumber() : null;
    }

    public String getStartTime() {
        long startMillis = hasRun()
                ? run.getTimeInMillis()
                : queueItem == null ? 0L : queueItem.getInQueueSince();
        return startMillis <= 0L ? "–" : START_TIME_FORMATTER.format(Instant.ofEpochMilli(startMillis));
    }

    public String getDuration() {
        if (hasRun()) {
            return run.getDurationString();
        }
        return queueItem == null ? "–" : queueItem.getInQueueForString();
    }

    public List<RunParameter> getParameters() {
        if (!hasRun()) {
            return List.of();
        }
        ParametersAction action = run.getAction(ParametersAction.class);
        if (action == null) {
            return List.of();
        }
        return action.getParameters().stream().map(RunParameter::from).toList();
    }

    public List<? extends Run.Artifact> getArtifacts() {
        if (!hasRun()) {
            return List.of();
        }
        return run.getArtifacts();
    }

    public boolean hasArtifacts() {
        return hasRun() && !run.getArtifacts().isEmpty();
    }

    public List<String> getLogLines() {
        if (!hasRun()) {
            return List.of();
        }
        try {
            return readLogLines(run.getLogReader(), MAX_LOG_LINES);
        } catch (IOException ex) {
            return List.of("Log konnte nicht gelesen werden: " + ex.getMessage());
        }
    }

    public String getLogText() {
        return joinLogLines(getLogLines());
    }

    public boolean hasLogLines() {
        return !getLogLines().isEmpty();
    }

    public String getConsoleUrl(String rootUrl) {
        if (!hasRun()) {
            return "";
        }
        return normalizedBaseUrl(rootUrl) + "/" + run.getUrl() + "console";
    }

    public String getArtifactsUrl(String rootUrl) {
        if (!hasRun()) {
            return "";
        }
        return normalizedBaseUrl(rootUrl) + "/" + run.getUrl() + "artifact/";
    }

    public String getDetailsUrl(String rootUrl, String urlName) {
        StringBuilder builder = new StringBuilder(normalizedBaseUrl(rootUrl))
                .append("/")
                .append(urlName)
                .append("/run?job=")
                .append(getJobNameEncoded());
        if (hasRun()) {
            builder.append("&build=").append(run.getNumber());
        } else if (queueId != null) {
            builder.append("&queue=").append(queueId);
        }
        return builder.toString();
    }

    public JSONObject toJson(String rootUrl, String urlName) {
        JSONObject payload = new JSONObject();
        payload.element("status", getStatus());
        payload.element("statusLabel", getStatusLabel());
        payload.element("complete", isComplete());
        payload.element("jobFullDisplayName", getJobFullDisplayName());
        payload.element("jobFullName", getJobFullName());
        payload.element("buildNumber", getBuildNumber());
        payload.element("queueId", queueId);
        payload.element("headline", getHeadline());
        payload.element("primaryLabel", getPrimaryLabel());
        payload.element("startTime", getStartTime());
        payload.element("duration", getDuration());
        payload.element("parameters", jsonParameters());
        payload.element("artifacts", jsonArtifacts(rootUrl));
        payload.element("logText", getLogText());
        payload.element("consoleUrl", getConsoleUrl(rootUrl));
        payload.element("artifactsUrl", getArtifactsUrl(rootUrl));
        payload.element("detailsUrl", getDetailsUrl(rootUrl, urlName));
        return payload;
    }

    private JSONArray jsonParameters() {
        JSONArray parameters = new JSONArray();
        for (RunParameter parameter : getParameters()) {
            JSONObject parameterJson = new JSONObject();
            parameterJson.element("name", parameter.getName());
            parameterJson.element("value", parameter.getValue());
            parameters.add(parameterJson);
        }
        return parameters;
    }

    public static final class RunParameter {
        private final String name;
        private final String value;

        private RunParameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        static RunParameter from(ParameterValue parameter) {
            return new RunParameter(parameter.getName(), displayValue(parameter));
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        private static String displayValue(ParameterValue parameter) {
            if (parameter instanceof AbstractFileParameterValue fileParameter) {
                return nullToEmpty(fileParameter.getFilename());
            }
            Object value = parameter.getValue();
            return value == null ? "" : value.toString();
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    private JSONArray jsonArtifacts(String rootUrl) {
        JSONArray artifacts = new JSONArray();
        if (!hasRun()) {
            return artifacts;
        }
        for (Run.Artifact artifact : run.getArtifacts()) {
            JSONObject artifactJson = new JSONObject();
            artifactJson.element("fileName", artifact.getFileName());
            artifactJson.element("relativePath", artifact.relativePath);
            artifactJson.element("url", normalizedBaseUrl(rootUrl) + "/" + run.getUrl() + "artifact/" + artifact.relativePath);
            artifacts.add(artifactJson);
        }
        return artifacts;
    }

    private static String normalizedBaseUrl(String rootUrl) {
        if (rootUrl == null || rootUrl.isBlank()) {
            return "";
        }
        return rootUrl.endsWith("/") ? rootUrl.substring(0, rootUrl.length() - 1) : rootUrl;
    }

    static List<String> readLogLines(Reader reader, int maxLines) throws IOException {
        if (reader == null || maxLines <= 0) {
            return List.of();
        }

        Deque<String> tail = new ArrayDeque<>(maxLines);
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (tail.size() == maxLines) {
                    tail.removeFirst();
                }
                tail.addLast(line);
            }
        }
        return ConsoleNote.removeNotes(new ArrayList<>(tail));
    }

    static String joinLogLines(List<String> lines) {
        return String.join("\n", lines == null ? List.of() : lines);
    }
}
