package ch.so.agi.jenkins.gretldatenportal;

import java.util.Objects;

public final class PipelineJobRenderer {
    private static final String TIMEOUT_PLACEHOLDER = "@@TIMEOUT_MINUTES@@";
    private static final String GRADLE_TASK_PLACEHOLDER = "@@GRADLE_TASK@@";
    private static final String POST_BLOCK_PLACEHOLDER = "@@POST_BLOCK@@";

    private final EmailNotificationService emailNotificationService;

    public PipelineJobRenderer() {
        this(new EmailNotificationService());
    }

    public PipelineJobRenderer(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    // Only the repo-wide shared/Jenkinsfile is treated as a template.
    public String renderTemplate(
            String template,
            OrganizationUnit organization,
            NotificationConfiguration notificationConfiguration) {
        Objects.requireNonNull(template, "template");
        JobDefinition jobDefinition = organization.getJobDefinition();
        String postBlock = emailNotificationService.renderPostBlock(notificationConfiguration);
        if (postBlock.isBlank()) {
            postBlock = "post { always { echo 'GRETL Datenportal job finished.' } }";
        }

        return template
                .replace(TIMEOUT_PLACEHOLDER, Integer.toString(jobDefinition.getTimeoutMinutes()))
                .replace(GRADLE_TASK_PLACEHOLDER, escapeGroovy(jobDefinition.getGradleTask()))
                .replace(POST_BLOCK_PLACEHOLDER, indent(postBlock, 4));
    }

    private static String indent(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        return value.lines().map(line -> prefix + line).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String escapeGroovy(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
