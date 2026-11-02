package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatenportalJobSummaryTest {
    @Test
    void disablesStartWhenUserCannotBuild() {
        DatenportalJobSummary summary = new DatenportalJobSummary(organization(true), false);

        assertFalse(summary.isStartable());
        assertFalse(summary.isCanBuild());
        assertEquals("Keine Berechtigung", summary.getStatusLabel());
    }

    @Test
    void startableJobHasNoReadyBadgeLabel() {
        DatenportalJobSummary summary = new DatenportalJobSummary(organization(true), true);

        assertTrue(summary.isStartable());
        assertEquals("ready", summary.getStatus());
        assertEquals("", summary.getStatusLabel());
    }

    private OrganizationUnit organization(boolean jobDefinitionPresent) {
        return new OrganizationUnit(
                "afu",
                Path.of("afu"),
                jobDefinitionPresent,
                new JobDefinition("afu", "AFU Datenportal publizieren", "", "gretl-datenportal-afu", "", ""),
                GuiDefinition.empty(),
                NotificationConfiguration.disabled(),
                List.of(new DatasetEntry(
                        "ch.so.dataset",
                        Path.of("afu/ch.so.dataset"),
                        new DatasetDefinition("ch.so.dataset", "Dataset", "", false),
                        true,
                        false,
                        GuiDefinition.empty())));
    }
}
