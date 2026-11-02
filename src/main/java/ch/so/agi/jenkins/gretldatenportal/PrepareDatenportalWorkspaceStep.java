package ch.so.agi.jenkins.gretldatenportal;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public final class PrepareDatenportalWorkspaceStep extends Step {
    @DataBoundConstructor public PrepareDatenportalWorkspaceStep() { }
    @Override public StepExecution start(StepContext context) { return new Execution(context); }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Map<String, String>> {
        private static final long serialVersionUID = 1L;
        Execution(StepContext context) { super(context); }
        @Override protected Map<String, String> run() throws Exception {
            var workspace = getContext().get(FilePath.class);
            var run = getContext().get(Run.class);
            var listener = getContext().get(TaskListener.class);
            if (workspace.isRemote()) throw new IOException("Datenportal currently requires a controller-local workspace.");
            var property = (GretlDatenportalManagedJobProperty) run.getParent().getProperty(GretlDatenportalManagedJobProperty.class);
            if (property == null || property.repositoryMode() == null) throw new IOException("Repository context missing; run the seeder first.");
            var config = GretlDatenportalGlobalConfiguration.get();
            boolean snapshot = "working-tree".equals(property.repositoryMode());
            boolean write = !snapshot && config.isTopicRepositoryWriteBackEnabled();
            if ("dev".equals(System.getenv("JENKINS_RUNTIME_MODE")) && write && !property.repositoryUrl().startsWith("file://"))
                throw new IOException("Dev runtime may only write to a local test Git repository.");
            var target = workspace.child("topic-repo");
            target.deleteRecursive();
            var manager = new TopicRepositoryManager();
            Path path = Path.of(target.getRemote());
            if (snapshot) {
                synchronized (TopicRepositoryManager.REPOSITORY_LOCK) {
                    Path source = Jenkins.get().getRootDir().toPath().resolve(TopicRepositoryManager.MANAGED_REPOSITORY_RELATIVE_PATH);
                    if (!Files.isDirectory(source)) throw new IOException("Seeder snapshot missing.");
                    Files.createDirectories(path);
                    manager.copyWorkingTree(source, path);
                }
            } else {
                String url = property.repositoryUrl();
                if (!url.startsWith("https://") && !url.startsWith("file://")) throw new IOException("Only HTTPS and local file Git URLs are supported.");
                if (java.net.URI.create(url).getUserInfo() != null) throw new IOException("Git URL must not contain credentials.");
                manager.ensureManagedCheckout(url, property.repositoryBranch(), path, true, listener.getLogger()::println);
            }
            var result = new LinkedHashMap<String, String>();
            result.put("path", target.getRemote()); result.put("mode", property.repositoryMode());
            result.put("url", snapshot ? "" : property.repositoryUrl()); result.put("branch", property.repositoryBranch());
            result.put("writeBack", Boolean.toString(write));
            result.put("baseRevision", snapshot ? "" : TopicGit.run(path, "rev-parse", "HEAD"));
            result.put("credentialsId", snapshot ? "" : config.getTopicRepositoryCredentialsId());
            result.put("committerName", config.getTopicRepositoryCommitterName());
            result.put("committerEmail", config.getTopicRepositoryCommitterEmail());
            if (write && (result.get("committerName").isBlank() || result.get("committerEmail").isBlank()))
                throw new IOException("Git committer name and email are required for write-back.");
            return result;
        }
    }
    @Extension public static final class DescriptorImpl extends StepDescriptor {
        @Override public String getFunctionName() { return "prepareDatenportalWorkspace"; }
        @Override public String getDisplayName() { return "Prepare isolated Datenportal working directory"; }
        @Override public Set<? extends Class<?>> getRequiredContext() { return Set.of(FilePath.class, Run.class, TaskListener.class); }
    }
}
