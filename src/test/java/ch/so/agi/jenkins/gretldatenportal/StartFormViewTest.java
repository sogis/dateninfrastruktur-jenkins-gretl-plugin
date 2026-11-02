package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StartFormViewTest {
    @Test
    void reloadsFormWhenDatasetChangesThroughJavascriptHook() throws IOException {
        String jelly = Files.readString(
                Path.of("src/main/resources/ch/so/agi/jenkins/gretldatenportal/GretlDatenportalRootAction/start.jelly"),
                StandardCharsets.UTF_8);

        assertTrue(jelly.contains("gretl-datenportal.js"));
        assertTrue(jelly.contains("data-gdp-dataset-selector=\"true\""));
        assertTrue(jelly.contains("data-gdp-dataset-start-url=\"${rootURL}/${it.urlName}/start?organization=${organization.id}&amp;dataset="));
        assertTrue(jelly.contains("Bitte Datensatz auswählen"));
        assertTrue(jelly.contains("class=\"gdp-start-form\""));
        assertTrue(jelly.contains("name=\"DATASET\""));
        assertTrue(jelly.contains(">Job starten</button>"));
        assertFalse(jelly.contains("gdp-eyebrow"));
        assertFalse(jelly.contains("gdp-header-actions"));
        assertFalse(jelly.contains("gdp-badge--info"));
        assertFalse(jelly.contains("${organization.defaultJobName}"));
        assertFalse(jelly.contains("Datensatz auswählen, Parameter setzen und den Jenkins Build starten."));
    }

    @Test
    void rendersStaticFieldsWithoutConditionHooks() throws IOException {
        String jelly = Files.readString(
                Path.of("src/main/resources/ch/so/agi/jenkins/gretldatenportal/GretlDatenportalRootAction/start.jelly"),
                StandardCharsets.UTF_8);

        assertTrue(jelly.contains("data-gdp-file-input=\"true\""));
        assertTrue(jelly.contains("data-gdp-file-name=\"${field.id}\""));
        assertTrue(jelly.contains("data-gdp-field=\"${field.id}\""));
        assertTrue(jelly.contains("Keine Datei ausgewählt."));
        assertTrue(jelly.contains("Erlaubt: .xtf, .xml"));
        assertTrue(jelly.contains("acceptUploadText\" value=\".xtf,.xml"));
        assertTrue(jelly.contains("Erlaubt: .csv"));
        assertFalse(jelly.contains("Erlaubt: .json"));
        assertFalse(jelly.contains("gdp-upload__icon"));
        assertFalse(jelly.contains("data-gdp-visible-param"));
        assertFalse(jelly.contains("data-gdp-required-param"));
        assertFalse(jelly.contains("CONFIRM_PRODUCTION"));
        assertFalse(jelly.contains("gdp-context-strip"));
        assertFalse(jelly.contains("Build Preview"));
        assertFalse(jelly.contains("Parameter-Hilfe"));
        assertFalse(jelly.contains("selected=\"${"));
        assertFalse(jelly.contains("checked=\"${"));
    }
}
