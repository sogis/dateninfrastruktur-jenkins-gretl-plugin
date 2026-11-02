package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class PrepareDatenportalWorkspaceStepTest {
    @TempDir Path temporary;

    @Test @WithJenkins
    void previewUsesSeederSnapshotAndNeverPromotesItsChanges(JenkinsRule j) throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        Files.writeString(source.resolve("state.txt"), "seeded");
        var config = GretlDatenportalGlobalConfiguration.get();
        config.setTopicRepositoryWriteBackEnabled(true);
        var repository = ConfiguredTopicRepository.resolve(source.toString(), "", "", config);
        Path snapshot = new TopicRepositoryManager().resolveRepositoryPath(repository, true, null);
        Files.writeString(source.resolve("state.txt"), "not seeded yet");
        var job = job(j, repository);
        job.setDefinition(new CpsFlowDefinition("""
                node {
                    def repo = prepareDatenportalWorkspace()
                    assert repo.mode == 'working-tree'
                    assert repo.writeBack == 'false'
                    dir(repo.path) {
                        assert readFile('state.txt') == 'seeded'
                        writeFile file: 'state.txt', text: 'candidate'
                    }
                }
                """, true));
        j.buildAndAssertSuccess(job);
        j.buildAndAssertSuccess(job);
        assertEquals("seeded", Files.readString(snapshot.resolve("state.txt")));
        assertEquals("not seeded yet", Files.readString(source.resolve("state.txt")));
    }

    @Test @WithJenkins
    void managedCheckoutUsesResolvedOverridesAndFreshRevisionOnEveryBuild(JenkinsRule j) throws Exception {
        Path source = temporary.resolve("repository");
        GitTestSupport.initRepository(source);
        Files.writeString(source.resolve("state.txt"), "first");
        GitTestSupport.commitAll(source, "first");
        var config = GretlDatenportalGlobalConfiguration.get();
        config.setTopicRepositoryUrl("https://example.invalid/wrong-global-repo.git");
        var repository = ConfiguredTopicRepository.resolve("", GitTestSupport.fileUrl(source), "main", config);
        var job = job(j, repository);
        job.setDefinition(new CpsFlowDefinition("""
                node {
                    def repo = prepareDatenportalWorkspace()
                    assert repo.mode == 'managed-git'
                    assert repo.writeBack == 'false'
                    assert repo.branch == 'main'
                    assert repo.baseRevision.size() == 40
                    dir(repo.path) { echo readFile('state.txt') }
                }
                """, true));
        j.assertLogContains("first", j.buildAndAssertSuccess(job));
        Files.writeString(source.resolve("state.txt"), "second");
        GitTestSupport.commitAll(source, "second");
        j.assertLogContains("second", j.buildAndAssertSuccess(job));
    }

    private WorkflowJob job(JenkinsRule j, ConfiguredTopicRepository repository) throws Exception {
        var job = j.createProject(WorkflowJob.class);
        var property = new GretlDatenportalManagedJobProperty();
        property.configureRepository(repository);
        job.addProperty(property);
        return job;
    }
}
