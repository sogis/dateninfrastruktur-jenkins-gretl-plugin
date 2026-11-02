package ch.so.agi.jenkins.gretldatenportal;

import java.util.List;

public final class DefaultGuiDefinitionFactory {
    public GuiDefinition create(boolean includeSeriesId) {
        List<GuiFieldDefinition> fields = new java.util.ArrayList<>();
        fields.add(field("ORGANISATION", "Organisation", ParameterType.STRING, true, "", true));
        fields.add(new GuiFieldDefinition(
                "DATASET",
                "Datensatz",
                "",
                ParameterType.CHOICE,
                true,
                "",
                false,
                List.of(),
                new ChoiceSource("datasets", ""),
                null,
                null,
                "",
                List.of(),
                null));
        fields.add(new GuiFieldDefinition("PUBLICATION_MODE", "Verarbeitung", "", ParameterType.CHOICE,
                true, "delivery", false, List.of("delivery", "repository-metadata"), null, null, null, "", List.of(), null));
        fields.add(field("RELOAD_PORTAL", "Portal nach Veröffentlichung neu laden", ParameterType.BOOLEAN, false, "false", false));
        fields.add(new GuiFieldDefinition(
                "METADATA_FILE",
                "Metadaten hochladen",
                "",
                ParameterType.FILE,
                false,
                "",
                false,
                List.of(),
                null,
                null,
                null,
                "stashedFile",
                List.of("xtf", "xml"),
                10));
        fields.add(new GuiFieldDefinition(
                "DATA_FILE",
                "Daten hochladen",
                "",
                ParameterType.FILE,
                false,
                "",
                false,
                List.of(),
                null,
                null,
                null,
                "stashedFile",
                List.of("csv"),
                100));
        if (includeSeriesId) {
            fields.add(field("SERIES_ID", "Ausgabe", ParameterType.STRING, false, "", false));
        }
        fields.add(field("COMMENT", "Kommentar", ParameterType.TEXT, false, "", false));
        return new GuiDefinition(fields);
    }

    private GuiFieldDefinition field(
            String id,
            String label,
            ParameterType type,
            boolean required,
            String defaultValue,
            boolean readOnly) {
        return new GuiFieldDefinition(
                id,
                label,
                "SERIES_ID".equals(id)
                        ? "Bezeichnung der gelieferten Ausgabe, z. B. 2025. Nur bei Datenlieferung erforderlich."
                        : "",
                type,
                required,
                defaultValue,
                readOnly,
                List.of(),
                null,
                null,
                null,
                "",
                List.of(),
                null);
    }
}
