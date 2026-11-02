package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefaultGuiDefinitionFactoryTest {
    private final DefaultGuiDefinitionFactory factory = new DefaultGuiDefinitionFactory();

    @Test
    void rendersOnlyFixedFieldsByDefault() {
        GuiDefinition gui = factory.create(false);

        assertFalse(hasField(gui, "MODE"));
        assertFalse(hasField(gui, "ENVIRONMENT"));
        assertFalse(hasField(gui, "DRY_RUN"));
        assertFalse(hasField(gui, "CONFIRM_PRODUCTION"));
        assertFalse(field(gui, "COMMENT").isRequired());
    }

    @Test
    void rendersSeriesIdOnlyForSeriesDatasets() {
        assertTrue(hasField(factory.create(true), "SERIES_ID"));
        GuiFieldDefinition issue = field(factory.create(true), "SERIES_ID");
        assertEquals("Ausgabe", issue.getLabel());
        assertFalse(issue.isRequired());
        assertTrue(issue.getDescription().contains("Nur bei Datenlieferung erforderlich"));
        assertFalse(hasField(factory.create(false), "SERIES_ID"));
    }

    @Test
    void restrictsDefaultUploadsToXtfXmlAndCsv() {
        GuiDefinition gui = factory.create(false);

        assertTrue(field(gui, "METADATA_FILE").getAllowedExtensions().contains("xtf"));
        assertTrue(field(gui, "METADATA_FILE").getAllowedExtensions().contains("xml"));
        assertFalse(field(gui, "METADATA_FILE").getAllowedExtensions().contains("json"));
        assertFalse(field(gui, "METADATA_FILE").getAllowedExtensions().contains("yaml"));
        assertTrue(field(gui, "DATA_FILE").getAllowedExtensions().contains("csv"));
        assertFalse(field(gui, "DATA_FILE").getAllowedExtensions().contains("xlsx"));
    }

    private boolean hasField(GuiDefinition guiDefinition, String id) {
        return guiDefinition.getFields().stream().anyMatch(field -> field.getId().equals(id));
    }

    private GuiFieldDefinition field(GuiDefinition guiDefinition, String id) {
        return guiDefinition.getFields().stream()
                .filter(field -> field.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
