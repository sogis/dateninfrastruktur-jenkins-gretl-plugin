package ch.so.agi.jenkins.gretldatenportal;

import hudson.model.ChoiceParameterDefinition;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.TextParameterDefinition;
import hudson.model.Descriptor.FormException;
import io.jenkins.plugins.file_parameters.StashedFileParameterDefinition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

public final class GretlDatenportalJobGenerator {
    private final PipelineScriptResolver pipelineScriptResolver;
    private final GretlDatenportalAuthorizationSynchronizer authorizationSynchronizer;

    public GretlDatenportalJobGenerator() {
        this(new PipelineScriptResolver(), new GretlDatenportalAuthorizationSynchronizer());
    }

    public GretlDatenportalJobGenerator(PipelineJobRenderer pipelineJobRenderer) {
        this(new PipelineScriptResolver(pipelineJobRenderer), new GretlDatenportalAuthorizationSynchronizer());
    }

    public GretlDatenportalJobGenerator(PipelineScriptResolver pipelineScriptResolver) {
        this(pipelineScriptResolver, new GretlDatenportalAuthorizationSynchronizer());
    }

    GretlDatenportalJobGenerator(
            PipelineScriptResolver pipelineScriptResolver,
            GretlDatenportalAuthorizationSynchronizer authorizationSynchronizer) {
        this.pipelineScriptResolver = pipelineScriptResolver;
        this.authorizationSynchronizer = authorizationSynchronizer;
    }

    public List<String> generate(ScanResult scanResult) throws IOException {
        return generate(scanResult, ConfiguredTopicRepository.fromGlobalConfiguration(GretlDatenportalGlobalConfiguration.get()));
    }

    List<String> generate(ScanResult scanResult, ConfiguredTopicRepository repository) throws IOException {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Item.CONFIGURE);

        List<String> generatedJobNames = new ArrayList<>();
        Set<String> activeJobNames = new HashSet<>();
        for (OrganizationUnit organization : scanResult.getOrganizations()) {
            if (organization.getDatasets().isEmpty()) {
                continue;
            }
            WorkflowJob job = getOrCreateJob(jenkins, organization.getJobDefinition().getJobName());
            job.setDescription(organization.getJobDefinition().getDescription());
            job.removeProperty(ParametersDefinitionProperty.class);
            job.addProperty(new ParametersDefinitionProperty(parameterDefinitions(organization)));
            try {
                job.setDefinition(new CpsFlowDefinition(
                        pipelineScriptResolver.resolve(organization),
                        true));
            } catch (FormException ex) {
                throw new IOException("Could not render Pipeline job '" + job.getName() + "'.", ex);
            }
            authorizationSynchronizer.synchronize(job, organization.getPermissionConfiguration());
            job.getProperty(GretlDatenportalManagedJobProperty.class).configureRepository(repository);
            job.save();
            generatedJobNames.add(job.getFullName());
            activeJobNames.add(job.getFullName());
        }
        lockRemovedJobs(jenkins, activeJobNames);
        return generatedJobNames;
    }

    private void lockRemovedJobs(Jenkins jenkins, Set<String> activeJobNames) throws IOException {
        for (WorkflowJob job : jenkins.getAllItems(WorkflowJob.class)) {
            if (!isManagedJob(job)
                    || activeJobNames.contains(job.getFullName())) {
                continue;
            }
            disable(job);
            authorizationSynchronizer.lock(job);
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

    private WorkflowJob getOrCreateJob(Jenkins jenkins, String name) throws IOException {
        Item existing = jenkins.getItem(name);
        if (existing instanceof WorkflowJob workflowJob) {
            return workflowJob;
        }
        if (existing != null) {
            throw new IOException("Cannot create Pipeline job '" + name + "': item already exists with another type.");
        }
        return jenkins.createProject(WorkflowJob.class, name);
    }

    private List<ParameterDefinition> parameterDefinitions(OrganizationUnit organization) {
        List<ParameterDefinition> parameters = new ArrayList<>();
        parameters.add(new StringParameterDefinition("ORGANISATION", organization.getId(), "Organisationseinheit"));
        parameters.add(new ChoiceParameterDefinition(
                "DATASET",
                organization.getDatasets().stream().map(DatasetEntry::getId).toArray(String[]::new),
                "Datensatz"));
        parameters.add(new ChoiceParameterDefinition("PUBLICATION_MODE", new String[]{"delivery", "repository-metadata"}, "Lieferung verarbeiten oder Datenblatt aus Repository übernehmen"));
        parameters.add(new hudson.model.BooleanParameterDefinition("RELOAD_PORTAL", false, "Portal nach Veröffentlichung neu laden"));
        parameters.add(new StashedFileParameterDefinition("METADATA_FILE"));
        parameters.add(new StashedFileParameterDefinition("DATA_FILE"));
        parameters.add(new TextParameterDefinition("COMMENT", "", "Kommentar"));
        parameters.add(new StringParameterDefinition("SERIES_ID", "", "Ausgabe, z. B. 2025; nur bei Datenlieferungen zu einer Serie erforderlich"));
        return parameters;
    }
}
