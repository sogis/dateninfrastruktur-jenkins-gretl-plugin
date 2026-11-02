package ch.so.agi.jenkins.gretldatenportal;

import java.nio.file.Path;
import java.util.Objects;

public final class ValidationMessage {
    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    private final Severity severity;
    private final String message;
    private final String path;

    public ValidationMessage(Severity severity, String message, Path path) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.message = Objects.requireNonNull(message, "message");
        this.path = path == null ? null : path.toString();
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
