package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.ParametersDefinitionProperty;
import hudson.model.ParametersAction;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TextParameterDefinition;
import hudson.model.TextParameterValue;
import io.jenkins.plugins.file_parameters.StashedFileParameterDefinition;
import io.jenkins.plugins.file_parameters.StashedFileParameterValue;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class RunDetailsTest {
    @Test
    void joinsLogLinesWithLineBreaks() {
        String text = RunDetails.joinLogLines(List.of(
                "Started by user admin",
                "[Pipeline] Start of Pipeline",
                "Finished: FAILURE"));

        assertEquals("Started by user admin\n[Pipeline] Start of Pipeline\nFinished: FAILURE", text);
    }

    @Test
    void returnsEmptyTextForMissingLogLines() {
        assertEquals("", RunDetails.joinLogLines(null));
    }

    @Test
    void readsCarriageReturnSeparatedLogLines() throws IOException {
        List<String> lines = RunDetails.readLogLines(
                new StringReader("Started by user admin\r[Pipeline] Start of Pipeline\rFinished: FAILURE"), 200);

        assertEquals(List.of(
                "Started by user admin",
                "[Pipeline] Start of Pipeline",
                "Finished: FAILURE"), lines);
    }

    @Test
    void keepsOnlyTailWhenReadingMixedLineBreaks() throws IOException {
        List<String> lines = RunDetails.readLogLines(new StringReader("first\r\nsecond\nthird\rfourth"), 2);

        assertEquals(List.of("third", "fourth"), lines);
    }

    @Test
    @WithJenkins
    void displaysOriginalNamesForStashedFileParameters(JenkinsRule jenkinsRule) throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "gretl-datenportal-agi");
        job.setDefinition(new CpsFlowDefinition("echo 'done'", true));
        job.addProperty(new ParametersDefinitionProperty(List.of(
                new StringParameterDefinition("DATASET", ""),
                new TextParameterDefinition("COMMENT", "", ""),
                new StashedFileParameterDefinition("METADATA_FILE"),
                new StashedFileParameterDefinition("DATA_FILE"))));
        WorkflowRun run = job.scheduleBuild2(0, new ParametersAction(List.of(
                new StringParameterValue("DATASET", "ch.so.av_nachfuehrungsstatistik.umsatz"),
                new TextParameterValue("COMMENT", "Kommentar"),
                stashedFileParameter("METADATA_FILE", "umsatz-metadaten.xtf"),
                stashedFileParameter("DATA_FILE", "umsatz.csv")))).get();

        RunDetails details = new RunDetails(run);
        assertEquals("ch.so.av_nachfuehrungsstatistik.umsatz", parameterValue(details.getParameters(), "DATASET"));
        assertEquals("Kommentar", parameterValue(details.getParameters(), "COMMENT"));
        assertEquals("umsatz-metadaten.xtf", parameterValue(details.getParameters(), "METADATA_FILE"));
        assertEquals("umsatz.csv", parameterValue(details.getParameters(), "DATA_FILE"));

        JSONArray parameters = details.toJson("https://jenkins.example", "gretl-datenportal").getJSONArray("parameters");
        assertEquals("umsatz-metadaten.xtf", jsonParameterValue(parameters, "METADATA_FILE"));
        assertEquals("umsatz.csv", jsonParameterValue(parameters, "DATA_FILE"));
    }

    private static StashedFileParameterValue stashedFileParameter(String name, String filename) throws IOException {
        DiskFileItem fileItem = DiskFileItem.builder()
                .setFieldName(name)
                .setFileName(filename)
                .get();
        try (OutputStream output = fileItem.getOutputStream()) {
            output.write(1);
        }
        return new StashedFileParameterValue(name, fileItem);
    }

    private static String parameterValue(List<RunDetails.RunParameter> parameters, String name) {
        return parameters.stream()
                .filter(parameter -> parameter.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    private static String jsonParameterValue(JSONArray parameters, String name) {
        for (Object parameter : parameters) {
            JSONObject value = (JSONObject) parameter;
            if (name.equals(value.getString("name"))) {
                return value.getString("value");
            }
        }
        throw new AssertionError("Missing parameter: " + name);
    }
}
