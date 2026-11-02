package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatenportalRunSummaryTest {
    @Test
    void extractsStringParametersForRunSummary() {
        ParametersAction parameters = new ParametersAction(List.of(
                new StringParameterValue("ORGANISATION", "afu"),
                new StringParameterValue("DATASET", "ch.so.dataset")));

        assertEquals("afu", DatenportalRunSummary.parameterValue(parameters, "ORGANISATION"));
        assertEquals("ch.so.dataset", DatenportalRunSummary.parameterValue(parameters, "DATASET"));
    }

    @Test
    void handlesMissingParametersAndRunningStatusLabel() {
        assertEquals("", DatenportalRunSummary.parameterValue(null, "DATASET"));
        assertEquals("Läuft", DatenportalRunSummary.statusLabel("RUNNING"));
        assertEquals("Wartet", DatenportalRunSummary.statusLabel("QUEUED"));
        assertEquals("Fehlgeschlagen", DatenportalRunSummary.statusLabel("FAILURE"));
    }
}
