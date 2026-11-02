package ch.so.agi.jenkins.gretldatenportal;

import java.nio.file.Path;
import java.util.Objects;

public final class DatasetEntry {
    private final String id;
    private final Path path;
    private final DatasetDefinition definition;
    private final boolean definitionValid;
    private final boolean datasetGuiPresent;
    private final GuiDefinition datasetGui;

    public DatasetEntry(
            String id,
            Path path,
            DatasetDefinition definition,
            boolean definitionValid,
            boolean datasetGuiPresent,
            GuiDefinition datasetGui) {
        this.id = Objects.requireNonNull(id, "id");
        this.path = Objects.requireNonNull(path, "path");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.definitionValid = definitionValid;
        this.datasetGuiPresent = datasetGuiPresent;
        this.datasetGui = datasetGui == null ? GuiDefinition.empty() : datasetGui;
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

    public DatasetDefinition getDefinition() {
        return definition;
    }

    public boolean isDefinitionValid() {
        return definitionValid;
    }

    public boolean isDatasetGuiPresent() {
        return datasetGuiPresent;
    }

    public GuiDefinition getDatasetGui() {
        return datasetGui;
    }

    public String getDisplayName() {
        return definition.getDisplayName();
    }
}
