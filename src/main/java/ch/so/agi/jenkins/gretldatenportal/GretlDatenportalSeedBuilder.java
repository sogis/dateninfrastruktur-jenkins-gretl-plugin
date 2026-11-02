package ch.so.agi.jenkins.gretldatenportal;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;

public class GretlDatenportalSeedBuilder extends Builder implements SimpleBuildStep {
    private final String topicRepositoryPath;
    private final String topicRepositoryUrl;
    private final String topicRepositoryBranch;
    private transient TopicRepositoryManager topicRepositoryManager;

    @DataBoundConstructor
    public GretlDatenportalSeedBuilder(
            String topicRepositoryPath,
            String topicRepositoryUrl,
            String topicRepositoryBranch) {
        this(topicRepositoryPath, topicRepositoryUrl, topicRepositoryBranch, new TopicRepositoryManager());
    }

    GretlDatenportalSeedBuilder(
            String topicRepositoryPath,
            String topicRepositoryUrl,
            String topicRepositoryBranch,
            TopicRepositoryManager topicRepositoryManager) {
        this.topicRepositoryPath = topicRepositoryPath == null ? "" : topicRepositoryPath;
        this.topicRepositoryUrl = topicRepositoryUrl == null ? "" : topicRepositoryUrl;
        this.topicRepositoryBranch = topicRepositoryBranch == null ? "" : topicRepositoryBranch;
        this.topicRepositoryManager = Objects.requireNonNull(topicRepositoryManager, "topicRepositoryManager");
    }

    public String getTopicRepositoryPath() {
        return topicRepositoryPath;
    }

    public String getTopicRepositoryUrl() {
        return topicRepositoryUrl;
    }

    public String getTopicRepositoryBranch() {
        return topicRepositoryBranch;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        synchronized (TopicRepositoryManager.REPOSITORY_LOCK) {
            ConfiguredTopicRepository repository = ConfiguredTopicRepository.resolve(topicRepositoryPath, topicRepositoryUrl,
                    topicRepositoryBranch, GretlDatenportalGlobalConfiguration.get());
            Path repositoryPath = topicRepositoryManager().resolveRepositoryPath(repository, true, listener.getLogger()::println);
            if (repositoryPath == null) {
                throw new AbortException("Topic repository path is not configured.");
            }

            ScanResult scanResult = new TopicRepositoryScanner().scan(repositoryPath);
            for (ValidationMessage message : scanResult.getMessages()) {
                listener.getLogger().printf("%s: %s%s%n",
                        message.getSeverity(),
                        message.getMessage(),
                        message.getPath() == null ? "" : " (" + message.getPath() + ")");
            }
            if (scanResult.hasErrors()) {
                if (hasAuthorizationErrors(scanResult)) {
                    lockManagedOrganizationJobs();
                }
                throw new AbortException("Topic repository validation failed.");
            }

            GretlDatenportalAuthorizationSynchronizer authorizationSynchronizer =
                    new GretlDatenportalAuthorizationSynchronizer();
            FreeStyleProject seedJob = resolveManagedSeedJob();
            if (seedJob != null) {
                authorizationSynchronizer.synchronizeSeedJob(
                        seedJob,
                        scanResult.getTeamDirectory(),
                        GretlDatenportalGlobalConfiguration.get().getSeedJobOperatorsTeam());
            }

            List<String> generated = new GretlDatenportalJobGenerator().generate(scanResult, repository);
            listener.getLogger().println("Generated GRETL Datenportal jobs: " + generated);
        }
    }

    private FreeStyleProject resolveManagedSeedJob() {
        if (Jenkins.getInstanceOrNull() == null) {
            return null;
        }
        Item item = Jenkins.get().getItem(GretlDatenportalSeedJobProvisioner.DEFAULT_SEED_JOB_NAME);
        if (item instanceof FreeStyleProject project
                && project.getProperty(GretlDatenportalManagedSeedJobProperty.class) != null) {
            return project;
        }
        return null;
    }

    private boolean hasAuthorizationErrors(ScanResult scanResult) {
        return scanResult.getMessages().stream()
                .filter(ValidationMessage::isError)
                .map(ValidationMessage::getMessage)
                .anyMatch(message -> message.contains(TopicRepositoryScanner.TEAMS_FILE)
                        || message.contains("permissions."));
    }

    private void lockManagedOrganizationJobs() throws IOException {
        if (Jenkins.getInstanceOrNull() == null) {
            return;
        }
        GretlDatenportalAuthorizationSynchronizer synchronizer =
                new GretlDatenportalAuthorizationSynchronizer();
        for (WorkflowJob job : Jenkins.get().getAllItems(WorkflowJob.class)) {
            if (isManagedJob(job)) {
                disable(job);
                synchronizer.lock(job);
            }
        }
    }

    private boolean isManagedJob(WorkflowJob job) {
        return job.getProperty(GretlDatenportalManagedJobProperty.class) != null
                || job.getName().startsWith("gretl-datenportal-");
    }

    @SuppressRestrictedWarnings(DoNotUse.class)
    private void disable(WorkflowJob job) {
        job.setDisabled(true);
    }

    private Path resolveRepositoryPath(TaskListener listener) throws IOException, InterruptedException {
        ConfiguredTopicRepository repository = ConfiguredTopicRepository.resolve(
                topicRepositoryPath,
                topicRepositoryUrl,
                topicRepositoryBranch,
                GretlDatenportalGlobalConfiguration.get());
        return topicRepositoryManager().resolveRepositoryPath(repository, true, listener.getLogger()::println);
    }

    private TopicRepositoryManager topicRepositoryManager() {
        if (topicRepositoryManager == null) {
            topicRepositoryManager = new TopicRepositoryManager();
        }
        return topicRepositoryManager;
    }

    @Extension
    @Symbol("gretlDatenportalJobGenerator")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Generate GRETL Datenportal Jobs";
        }
    }
}
