package ch.so.agi.jenkins.gretldatenportal;

import java.util.Objects;

public final class DatasetDefinition {
    private final String id;
    private final String title;
    private final String description;
    private final boolean series;

    public DatasetDefinition(String id, String title, String description, boolean series) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.description = description == null ? "" : description;
        this.series = series;
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

    public boolean isSeries() {
        return series;
    }

    public String getDisplayName() {
        return title + " (" + id + ")";
    }
}
