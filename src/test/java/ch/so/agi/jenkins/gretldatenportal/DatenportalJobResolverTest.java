package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatenportalJobResolverTest {
    private final DatenportalJobResolver resolver = new DatenportalJobResolver();

    @Test
    void resolvesSeriesIdOnlyForSeriesDatasets() {
        assertTrue(hasField(resolver.resolve(organization(), dataset(true)).getGuiDefinition(), "SERIES_ID"));
        assertFalse(hasField(resolver.resolve(organization(), dataset(false)).getGuiDefinition(), "SERIES_ID"));
    }

    @Test
    void ignoresSharedOrganizationAndDatasetGuiOverrides() {
        GuiDefinition sharedGui = new GuiDefinition(List.of(field("COMMENT", "Gemeinsamer Kommentar", "")));
        GuiDefinition organizationGui = new GuiDefinition(List.of(field("COMMENT", "Kommentar AFU", "")));
        GuiDefinition datasetGui = new GuiDefinition(List.of(field("COMMENT", "", "Kommentar zum Datensatz")));

        OrganizationUnit organization = organization(sharedGui, organizationGui);
        DatasetEntry dataset = new DatasetEntry(
                "ch.so.dataset",
                Path.of("afu/ch.so.dataset"),
                new DatasetDefinition("ch.so.dataset", "Dataset", "", false),
                true,
                true,
                datasetGui);

        GuiFieldDefinition comment = field(resolver.resolve(organization, dataset).getGuiDefinition(), "COMMENT");

        assertEquals("Kommentar", comment.getLabel());
        assertEquals("", comment.getDescription());
    }

    private OrganizationUnit organization() {
        return organization(GuiDefinition.empty(), GuiDefinition.empty());
    }

    private OrganizationUnit organization(GuiDefinition sharedGui, GuiDefinition organizationGui) {
        return new OrganizationUnit(
                "afu",
                Path.of("afu"),
                true,
                JobDefinition.defaultFor("afu"),
                organizationGui,
                NotificationConfiguration.disabled(),
                PermissionConfiguration.empty(),
                new RepositoryDefaults(
                        Path.of("shared"),
                        true,
                        "",
                        null,
                        "",
                        sharedGui,
                        NotificationConfiguration.empty()),
                List.of());
    }

    private DatasetEntry dataset(boolean series) {
        return new DatasetEntry(
                "ch.so.dataset",
                Path.of("afu/ch.so.dataset"),
                new DatasetDefinition("ch.so.dataset", "Dataset", "", series),
                true,
                false,
                GuiDefinition.empty());
    }

    private boolean hasField(GuiDefinition guiDefinition, String id) {
        return guiDefinition.getFields().stream().anyMatch(field -> field.getId().equals(id));
    }

    private GuiFieldDefinition field(GuiDefinition definition, String id) {
        return definition.getFields().stream()
                .filter(field -> field.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private GuiFieldDefinition field(String id, String label, String description) {
        return new GuiFieldDefinition(
                id,
                label,
                description,
                ParameterType.STRING,
                false,
                "",
                false,
                List.of(),
                null,
                null,
                null,
                "",
                List.of(),
                null);
    }
}
