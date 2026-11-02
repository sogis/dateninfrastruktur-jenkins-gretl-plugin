package ch.so.agi.jenkins.gretldatenportal;

import java.util.Objects;

public final class ChoiceSource {
    private final String type;
    private final String path;

    public ChoiceSource(String type, String path) {
        this.type = Objects.requireNonNull(type, "type");
        this.path = path == null ? "" : path;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public boolean isDatasets() {
        return "datasets".equals(type);
    }
}
