package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RootActionViewAssetsTest {
    @Test
    void rootViewIncludesOnlyExecutedRunsAndNavigationToJobs() throws IOException {
        String jelly = readView("index.jelly");

        assertTrue(jelly.contains("gretl-datenportal.css"));
        assertTrue(jelly.contains("gretl-datenportal.js"));
        assertTrue(jelly.contains("<h1>${it.displayName}</h1>"));
        assertTrue(jelly.contains("<div class=\"gdp-header-actions gdp-header-actions--below-title\">"));
        assertTrue(jelly.contains(
                "<h1>${it.displayName}</h1>\n                        <div class=\"gdp-header-actions gdp-header-actions--below-title\">"));
        assertFalse(jelly.contains("<p class=\"gdp-eyebrow\">GRETL</p>"));
        assertFalse(jelly.contains("Ausgeführte Datenportal-Jobs prüfen."));
        assertFalse(jelly.contains("Alle Builds der GRETL-Datenportal-Jobs, neueste Ausführung zuerst."));
        assertFalse(jelly.contains("gdp-summary"));
        assertFalse(jelly.contains("Themen-Repo"));
        assertFalse(jelly.contains("repositoryPathString"));
        assertFalse(jelly.contains("Ausgeführte Jobs"));
        assertFalse(jelly.contains("gdp-panel gdp-run-overview"));
        assertTrue(jelly.contains("<section class=\"gdp-run-overview\">"));
        assertTrue(jelly.contains("data-gdp-run-filters=\"true\""));
        assertTrue(jelly.contains("name=\"organization\""));
        assertTrue(jelly.contains("name=\"dataset\""));
        assertTrue(jelly.contains("method=\"get\""));
        assertTrue(jelly.contains("overview.nextPageQuery"));
        assertTrue(jelly.contains("overview.previousPageQuery"));
        assertTrue(jelly.contains("items=\"${overview.runs}\""));
        assertFalse(jelly.contains("data-gdp-filter-item"));
        assertFalse(jelly.contains("items=\"${it.executedRuns}\""));
        assertTrue(jelly.contains("href=\"${rootURL}/${it.urlName}/jobs\""));
        assertFalse(jelly.contains("gdp-job-list"));
    }

    @Test
    void jobsViewIncludesStartOverviewWithoutDatasetParameter() throws IOException {
        String jelly = readView("jobs.jelly");

        assertTrue(jelly.contains("Jobs starten"));
        assertTrue(jelly.contains(
                "href=\"${rootURL}/${it.urlName}\">Zurück zur Datenportal-Startseite</a>"));
        assertTrue(jelly.contains("Pro Organisationseinheit gibt es einen GRETL-Datenportal-Job."));
        assertTrue(jelly.contains("gdp-job-list"));
        assertTrue(jelly.contains("gdp-job-row"));
        assertTrue(jelly.contains("start?organization=${job.organization}\""));
        assertFalse(jelly.contains("&amp;dataset="));
        assertFalse(jelly.contains("${job.firstDatasetId}"));
        assertFalse(jelly.contains("<p class=\"gdp-eyebrow\">GRETL</p>"));
        assertFalse(jelly.contains("gdp-panel gdp-job-overview"));
        assertFalse(jelly.contains("gdp-job-card__icon"));
        assertFalse(jelly.contains("${job.description}"));
        assertFalse(jelly.contains("Datenportal-Ausführungen"));
    }

    @Test
    void runViewIncludesLogExcerptAndConsoleLink() throws IOException {
        String jelly = readView("run.jelly");

        assertTrue(jelly.contains("gretl-datenportal.css"));
        assertTrue(jelly.contains("gretl-datenportal.js"));
        assertTrue(jelly.contains("Jobstatus"));
        assertTrue(jelly.contains(
                "href=\"${rootURL}/${it.urlName}\">Zurück zur Datenportal-Startseite</a>"));
        assertFalse(jelly.contains("gdp-eyebrow"));
        assertTrue(jelly.contains("data-gdp-status=\"${details.status}\""));
        assertTrue(jelly.contains("data-gdp-run-status-view=\"true\""));
        assertTrue(jelly.contains("data-gdp-run-status-url=\"true\""));
        assertTrue(jelly.contains("Logausgabe"));
        assertTrue(jelly.contains("details.logText"));
        assertFalse(jelly.contains("items=\"${details.logLines}\""));
        assertTrue(jelly.contains("Vollständiges Log öffnen"));
    }

    @Test
    void javascriptEnhancesUploadsAndSubmitsRunFilters() throws IOException {
        String script = Files.readString(Path.of("src/main/webapp/js/gretl-datenportal.js"), StandardCharsets.UTF_8);

        assertTrue(script.contains("data-gdp-file-input"));
        assertFalse(script.contains("data-gdp-visible-param"));
        assertFalse(script.contains("data-gdp-required-param"));
        assertTrue(script.contains("data-gdp-run-filters"));
        assertTrue(script.contains("form.requestSubmit()"));
        assertFalse(script.contains("data-gdp-filter-item"));
        assertTrue(script.contains("data-gdp-run-status-view"));
        assertTrue(script.contains("window.fetch"));
        assertTrue(script.contains("5000"));

        String css = Files.readString(Path.of("src/main/webapp/css/gretl-datenportal.css"), StandardCharsets.UTF_8);
        assertTrue(css.contains("[hidden]"));
        assertTrue(css.contains("display: none !important"));
        assertTrue(css.contains("overflow-y: auto"));
        assertTrue(css.contains("overflow-x: hidden"));
        assertTrue(css.contains("data-gdp-status=\"QUEUED\""));
        assertTrue(css.contains("data-gdp-status=\"RUNNING\""));
    }

    private String readView(String name) throws IOException {
        return Files.readString(
                Path.of("src/main/resources/ch/so/agi/jenkins/gretldatenportal/GretlDatenportalRootAction", name),
                StandardCharsets.UTF_8);
    }
}
