package ch.so.agi.jenkins.gretldatenportal;

import java.util.Objects;

public final class JobDefinition {
    private final String id;
    private final String title;
    private final String description;
    private final String jobName;
    private final String gradleTask;
    private final String jenkinsfile;
    private final int timeoutMinutes;
    private final boolean jobNameSpecified;
    private final boolean gradleTaskSpecified;
    private final boolean jenkinsfileSpecified;
    private final boolean timeoutMinutesSpecified;

    public JobDefinition(
            String id,
            String title,
            String description,
            String jobName,
            String gradleTask,
            String jenkinsfile) {
        this(id, title, description, jobName, gradleTask, jenkinsfile, 60);
    }

    public JobDefinition(
            String id,
            String title,
            String description,
            String jobName,
            String gradleTask,
            String jenkinsfile,
            int timeoutMinutes) {
        this(
                id,
                title,
                description,
                jobName,
                gradleTask,
                jenkinsfile,
                timeoutMinutes,
                isSpecified(jobName),
                isSpecified(gradleTask),
                isSpecified(jenkinsfile),
                timeoutMinutes > 0);
    }

    public JobDefinition(
            String id,
            String title,
            String description,
            String jobName,
            String gradleTask,
            String jenkinsfile,
            int timeoutMinutes,
            boolean jobNameSpecified,
            boolean gradleTaskSpecified,
            boolean jenkinsfileSpecified,
            boolean timeoutMinutesSpecified) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title == null ? id : title;
        this.description = description == null ? "" : description;
        this.jobName = jobName == null || jobName.isBlank() ? "gretl-datenportal-" + id : jobName;
        this.gradleTask = gradleTask == null || gradleTask.isBlank() ? "publishToDatenportal" : gradleTask;
        this.jenkinsfile = jenkinsfile == null ? "" : jenkinsfile;
        this.timeoutMinutes = timeoutMinutes <= 0 ? 60 : timeoutMinutes;
        this.jobNameSpecified = jobNameSpecified && isSpecified(jobName);
        this.gradleTaskSpecified = gradleTaskSpecified && isSpecified(gradleTask);
        this.jenkinsfileSpecified = jenkinsfileSpecified && isSpecified(jenkinsfile);
        this.timeoutMinutesSpecified = timeoutMinutesSpecified && timeoutMinutes > 0;
    }

    public static JobDefinition defaultFor(String id) {
        return new JobDefinition(id, id, "", null, null, null, 60, false, false, false, false);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getJobName() {
        return jobName;
    }

    public String getGradleTask() {
        return gradleTask;
    }

    public String getJenkinsfile() {
        return jenkinsfile;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public boolean isJobNameSpecified() {
        return jobNameSpecified;
    }

    public boolean isGradleTaskSpecified() {
        return gradleTaskSpecified;
    }

    public boolean isJenkinsfileSpecified() {
        return jenkinsfileSpecified;
    }

    public boolean isTimeoutMinutesSpecified() {
        return timeoutMinutesSpecified;
    }

    private static boolean isSpecified(String value) {
        return value != null && !value.isBlank();
    }
}
