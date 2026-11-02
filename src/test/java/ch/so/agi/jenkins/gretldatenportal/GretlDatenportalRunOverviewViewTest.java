package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import java.nio.file.Path;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class GretlDatenportalRunOverviewViewTest {
    @TempDir
    Path topicRepository;

    @Test
    @WithJenkins
    void rendersPagedHistoryAndSubmitsFiltersWithoutJavascript(JenkinsRule j) throws Exception {
        GitTestSupport.addOrganization(topicRepository, "afu", "ch.so.current");
        var configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryPath(topicRepository.toString());
        configuration.setTopicRepositoryMode("working-tree");
        configuration.setSeedJobCron("");
        configuration.setDisplayName("GRETL Datenportal Jobs");
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "gretl-datenportal-afu");
        job.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("ORGANISATION", "afu"),
                new StringParameterDefinition("DATASET", "ch.so.current")));
        job.setDefinition(new CpsFlowDefinition(
                "if (params.DATASET == 'ch.so.historical') { error 'fixture failure' } else { echo 'fixture success' }", true));
        for (int i = 1; i <= 13; i++) {
            String dataset = i <= 2 ? "ch.so.historical" : "ch.so.current";
            var run = job.scheduleBuild2(0, new ParametersAction(
                    new StringParameterValue("ORGANISATION", "afu"),
                    new StringParameterValue("DATASET", dataset))).get();
            j.assertBuildStatus(i <= 2 ? Result.FAILURE : Result.SUCCESS, run);
        }
        try (var client = j.createWebClient()) {
            client.getOptions().setJavaScriptEnabled(false);
            HtmlPage first = client.goTo("gretl-datenportal/");
            assertTrue(first.getTitleText().contains("Datenportal Jobs"));
            assertFalse(first.getTitleText().contains("GRETL"));
            assertEquals(1, first.getByXPath("//h1[text()='Datenportal Jobs']").size());
            assertEquals(10, first.getElementsByTagName("article").size());
            assertTrue(first.asNormalizedText().contains("1–10 von 13"));
            assertEquals(1, first.getByXPath("//select[@name='dataset']/option[@value='ch.so.historical']").size());

            HtmlPage second = first.getAnchorByText("Weiter").click();
            assertEquals(3, second.getElementsByTagName("article").size());
            assertTrue(second.asNormalizedText().contains("11–13 von 13"));
            HtmlForm filters = second.getFirstByXPath("//form[@data-gdp-run-filters]");
            filters.getSelectByName("organization").setSelectedAttribute("afu", true);
            filters.getSelectByName("dataset").setSelectedAttribute("ch.so.historical", true);
            filters.getSelectByName("status").setSelectedAttribute("FAILURE", true);
            HtmlButton submit = filters.getFirstByXPath(".//button[@type='submit']");
            HtmlPage filtered = submit.click();
            assertEquals(2, filtered.getElementsByTagName("article").size());
            assertTrue(filtered.asNormalizedText().contains("1–2 von 2"));
            assertFalse(filtered.getUrl().getQuery().contains("page="));
            assertEquals(3, filtered.getByXPath("//select/option[@selected='selected']").size());

            HtmlPage noMatches = client.goTo("gretl-datenportal/?organization=afu&dataset=ch.so.historical&status=SUCCESS");
            assertTrue(noMatches.asNormalizedText().contains("Keine passenden Ausführungen gefunden."));
            assertFalse(noMatches.asNormalizedText().contains("Noch keine Jobs ausgeführt."));
        }
    }
}
