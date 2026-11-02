package ch.so.agi.jenkins.gretldatenportal;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.RootAction;
import hudson.model.StringParameterValue;
import hudson.model.TextParameterValue;
import hudson.model.queue.ScheduleResult;
import io.jenkins.plugins.file_parameters.StashedFileParameterDefinition;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.springframework.security.core.Authentication;
import org.apache.commons.fileupload2.core.FileItem;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@Extension
public class GretlDatenportalRootAction implements RootAction {
    private final TopicRepositoryScanner scanner;
    private final DatenportalJobResolver jobResolver;
    private final StartFormValidator startFormValidator;
    private final TopicRepositoryManager topicRepositoryManager;

    public GretlDatenportalRootAction() {
        this(new TopicRepositoryScanner(), new DatenportalJobResolver(), new StartFormValidator(), new TopicRepositoryManager());
    }

    GretlDatenportalRootAction(
            TopicRepositoryScanner scanner,
            DatenportalJobResolver jobResolver,
            StartFormValidator startFormValidator,
            TopicRepositoryManager topicRepositoryManager) {
        this.scanner = scanner;
        this.jobResolver = jobResolver;
        this.startFormValidator = startFormValidator;
        this.topicRepositoryManager = topicRepositoryManager;
    }

    @Override
    public String getIconFileName() {
        return "symbol-jobs";
    }

    @Override
    public String getDisplayName() {
        return getConfiguration().getDisplayName();
    }

    @Override
    public String getUrlName() {
        return getConfiguration().getUrlName();
    }

    public ScanResult getScanResult() {
        Jenkins.get().checkPermission(Jenkins.READ);

        Path repositoryPath;
        try {
            repositoryPath = topicRepositoryManager.resolveRepositoryPath(getConfiguration(), false);
        } catch (Exception ex) {
            return ScanResult.empty(
                    null,
                    new ValidationMessage(
                            ValidationMessage.Severity.ERROR,
                            "Could not prepare topic repository: " + ex.getMessage(),
                            null));
        }
        if (repositoryPath == null) {
            return ScanResult.empty(
                    null,
                    new ValidationMessage(
                            ValidationMessage.Severity.WARNING,
                            "Topic repository is not configured.",
                            null));
        }
        return scanner.scan(repositoryPath);
    }

    public List<OrganizationUnit> getOrganizations() {
        return getScanResult().getOrganizations().stream()
                .filter(organization -> getVisibleWorkflowJob(organization) != null)
                .toList();
    }

    public List<ValidationMessage> getMessages() {
        return getScanResult().getMessages();
    }

    public List<DatenportalJobSummary> getOrganizationJobSummaries() {
        return getOrganizations().stream()
                .map(organization -> new DatenportalJobSummary(organization, canBuildOrganization(organization)))
                .toList();
    }

    public List<DatenportalRunSummary> getExecutedRuns() {
        List<DatenportalRunSummary> runs = collectExecutedRuns(getScanResult());
        runs.sort(DatenportalRunOverview.RUN_ORDER);
        return runs;
    }

    public DatenportalRunOverview getRunOverview() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        String attribute = GretlDatenportalRootAction.class.getName() + ".runOverview";
        if (request != null && request.getAttribute(attribute) instanceof DatenportalRunOverview overview) {
            return overview;
        }
        ScanResult scanResult = getScanResult();
        DatenportalRunOverview overview = new DatenportalRunOverview(
                scanResult,
                collectExecutedRuns(scanResult),
                currentParameter("organization"),
                currentParameter("dataset"),
                currentParameter("status"),
                currentParameter("page"));
        if (request != null) {
            request.setAttribute(attribute, overview);
        }
        return overview;
    }

    private List<DatenportalRunSummary> collectExecutedRuns(ScanResult scanResult) {
        List<DatenportalRunSummary> runs = new ArrayList<>();
        for (OrganizationUnit organization : scanResult.getOrganizations()) {
            WorkflowJob workflowJob = getVisibleWorkflowJob(organization);
            if (workflowJob == null) {
                continue;
            }
            for (WorkflowRun run : workflowJob.getBuilds()) {
                runs.add(new DatenportalRunSummary(run, organization));
            }
        }
        return runs;
    }

    public List<String> getRunFilterOrganizations() {
        Set<String> organizations = new LinkedHashSet<>();
        getExecutedRuns().stream()
                .map(DatenportalRunSummary::getOrganizationValue)
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .forEach(organizations::add);
        return List.copyOf(organizations);
    }

    public List<String> getRunFilterDatasets() {
        Set<String> datasets = new LinkedHashSet<>();
        getExecutedRuns().stream()
                .map(DatenportalRunSummary::getDatasetValue)
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .forEach(datasets::add);
        return List.copyOf(datasets);
    }

    public OrganizationUnit getSelectedOrganization() {
        String organizationId = currentParameter("organization");
        return getOrganization(organizationId);
    }

    public DatasetEntry getSelectedDataset() {
        OrganizationUnit organization = getSelectedOrganization();
        if (organization == null) {
            return null;
        }
        String datasetId = currentParameter("dataset");
        if (datasetId.isBlank()) {
            return null;
        }
        return organization.getDataset(datasetId);
    }

    public List<DatasetEntry> getSelectedOrganizationDatasets() {
        OrganizationUnit organization = getSelectedOrganization();
        return organization == null ? List.of() : organization.getDatasets();
    }

    public ResolvedDatenportalJob getSelectedResolvedJob() {
        OrganizationUnit organization = getSelectedOrganization();
        DatasetEntry dataset = getSelectedDataset();
        if (organization == null || dataset == null) {
            return null;
        }
        return jobResolver.resolve(organization, dataset);
    }

    public List<GuiFieldDefinition> getSelectedGuiFields() {
        ResolvedDatenportalJob resolvedJob = getSelectedResolvedJob();
        return resolvedJob == null ? List.of() : resolvedJob.getGuiDefinition().getFields();
    }

    public WorkflowRun getSelectedRun() {
        return findRun(getReadableWorkflowJob(currentParameter("job")), currentParameter("build"));
    }

    public RunDetails getSelectedRunDetails() {
        return resolveRunDetails(currentParameter("job"), currentParameter("build"), currentParameter("queue"));
    }

    public void doBuild(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.READ);
        OrganizationUnit organization = findOrganization(req.getParameter("ORGANISATION"));
        if (organization == null) {
            rsp.sendError(404, "Unknown organization.");
            return;
        }
        WorkflowJob workflowJob = getVisibleWorkflowJob(organization);
        if (workflowJob == null) {
            Item item = Jenkins.get().getItem(organization.getJobDefinition().getJobName());
            if (!(item instanceof WorkflowJob)) {
                rsp.sendError(
                        404,
                        "Generated Pipeline job '" + organization.getJobDefinition().getJobName()
                                + "' does not exist yet. Run the GRETL Datenportal seed job first.");
            } else {
                rsp.sendError(403, "You do not have permission to read this GRETL Datenportal organization.");
            }
            return;
        }
        DatasetEntry dataset = organization.getDataset(req.getParameter("DATASET"));
        if (dataset == null) {
            rsp.sendError(404, "Unknown dataset.");
            return;
        }

        ResolvedDatenportalJob resolvedJob = jobResolver.resolve(organization, dataset);
        StartFormSubmission submission = createSubmission(req);
        List<ValidationMessage> validationMessages = startFormValidator.validate(resolvedJob, submission);
        if (!validationMessages.isEmpty()) {
            rsp.sendError(400, validationMessages.get(0).getMessage());
            return;
        }

        workflowJob.checkPermission(Item.BUILD);
        if (!canBuildOrganization(organization)) {
            rsp.sendError(403, "You do not have permission to build this GRETL Datenportal organization.");
            return;
        }
        List<ParameterValue> parameterValues = createParameterValues(req);
        ScheduleResult scheduleResult = Queue.getInstance().schedule2(
                workflowJob,
                0,
                new CauseAction(new Cause.UserIdCause()),
                new ParametersAction(parameterValues));
        if (!scheduleResult.isAccepted() || scheduleResult.getItem() == null) {
            rsp.sendError(409, "Build konnte nicht in die Jenkins-Queue eingereiht werden.");
            return;
        }

        rsp.sendRedirect2(runDetailsUrl(
                baseUrl(req),
                workflowJob.getFullName(),
                null,
                scheduleResult.getItem().getId()));
    }

    public void doRunStatus(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.READ);

        RunDetails details = resolveRunDetails(req.getParameter("job"), req.getParameter("build"), req.getParameter("queue"));
        if (details == null) {
            rsp.sendError(404, "Build wurde nicht gefunden.");
            return;
        }

        JSONObject payload = details.toJson(baseUrl(req), getUrlName());
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.getWriter().write(payload.toString());
    }

    private GretlDatenportalGlobalConfiguration getConfiguration() {
        if (Jenkins.getInstanceOrNull() == null) {
            return new GretlDatenportalGlobalConfiguration();
        }
        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        if (configuration == null) {
            return new GretlDatenportalGlobalConfiguration();
        }
        return configuration;
    }

    private OrganizationUnit getOrganization(String organizationId) {
        OrganizationUnit organization = findOrganization(organizationId);
        if (organization == null || getVisibleWorkflowJob(organization) == null) {
            return null;
        }
        return organization;
    }

    private OrganizationUnit findOrganization(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return null;
        }
        return getScanResult().getOrganizations().stream()
                .filter(organization -> organization.getId().equals(organizationId))
                .findFirst()
                .orElse(null);
    }

    private boolean canBuildOrganization(OrganizationUnit organization) {
        if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return true;
        }
        return organization.getPermissionConfiguration().canBuild(authentication());
    }

    private WorkflowJob getVisibleWorkflowJob(OrganizationUnit organization) {
        if (organization == null) {
            return null;
        }
        return getReadableWorkflowJob(organization.getJobDefinition().getJobName());
    }

    private Authentication authentication() {
        return Jenkins.getAuthentication2();
    }

    RunDetails resolveRunDetails(String jobName, String buildNumber, String queueId) {
        WorkflowJob workflowJob = getReadableWorkflowJob(jobName);
        if (workflowJob == null) {
            return null;
        }

        WorkflowRun selectedRun = findRun(workflowJob, buildNumber);
        if (selectedRun != null) {
            return new RunDetails(selectedRun);
        }

        Long selectedQueueId = parseLong(queueId);
        if (selectedQueueId == null) {
            return null;
        }

        WorkflowRun queuedRun = findRunByQueueId(workflowJob, selectedQueueId);
        if (queuedRun != null) {
            return new RunDetails(queuedRun);
        }

        Queue.Item currentQueueItem = Queue.getInstance().getItem(selectedQueueId);
        if (matchesJob(currentQueueItem, workflowJob)) {
            return new RunDetails(workflowJob, currentQueueItem, selectedQueueId);
        }

        for (Queue.Item leftItem : Queue.getInstance().getLeftItems()) {
            if (matchesJob(leftItem, workflowJob) && leftItem.getId() == selectedQueueId) {
                return new RunDetails(workflowJob, leftItem, selectedQueueId);
            }
        }
        return null;
    }

    private String currentParameter(String name) {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        return request == null || request.getParameter(name) == null ? "" : request.getParameter(name);
    }

    String runDetailsUrl(String baseUrl, String jobFullName, Integer buildNumber, Long queueId) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/")
                .append(getUrlName())
                .append("/run?job=")
                .append(URLEncoder.encode(jobFullName, java.nio.charset.StandardCharsets.UTF_8));
        if (buildNumber != null) {
            url.append("&build=").append(buildNumber);
        } else if (queueId != null) {
            url.append("&queue=").append(queueId);
        }
        return url.toString();
    }

    private WorkflowJob getReadableWorkflowJob(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return null;
        }
        WorkflowJob workflowJob = Jenkins.get().getItemByFullName(jobName, WorkflowJob.class);
        if (workflowJob == null || !workflowJob.hasPermission(Item.READ)) {
            return null;
        }
        return workflowJob;
    }

    private WorkflowRun findRun(WorkflowJob workflowJob, String buildNumber) {
        if (workflowJob == null || buildNumber == null || buildNumber.isBlank()) {
            return null;
        }
        try {
            return workflowJob.getBuildByNumber(Integer.parseInt(buildNumber));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private WorkflowRun findRunByQueueId(WorkflowJob workflowJob, long queueId) {
        for (WorkflowRun run = workflowJob.getLastBuild(); run != null; run = run.getPreviousBuild()) {
            if (run.getQueueId() == queueId) {
                return run;
            }
        }
        return null;
    }

    private boolean matchesJob(Queue.Item item, WorkflowJob workflowJob) {
        return item != null && workflowJob != null && item.getTask() == workflowJob;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String baseUrl(StaplerRequest2 req) {
        String rootUrl = Jenkins.get().getRootUrl();
        if (rootUrl != null && !rootUrl.isBlank()) {
            return rootUrl.endsWith("/") ? rootUrl.substring(0, rootUrl.length() - 1) : rootUrl;
        }
        return req.getContextPath();
    }

    private StartFormSubmission createSubmission(StaplerRequest2 req) throws IOException, ServletException {
        Map<String, String> values = new HashMap<>();
        for (String key : List.of("ORGANISATION", "DATASET", "COMMENT", "SERIES_ID", "PUBLICATION_MODE", "RELOAD_PORTAL")) {
            values.put(key, req.getParameter(key) == null ? "" : req.getParameter(key));
        }
        Map<String, UploadedFileInfo> files = new HashMap<>();
        addFileInfo(req, files, "METADATA_FILE");
        addFileInfo(req, files, "DATA_FILE");
        return new StartFormSubmission(values, files);
    }

    private void addFileInfo(StaplerRequest2 req, Map<String, UploadedFileInfo> files, String field)
            throws IOException, ServletException {
        FileItem fileItem = req.getFileItem2(field);
        if (fileItem != null && fileItem.getSize() > 0) {
            files.put(field, new UploadedFileInfo(fileItem.getName(), fileItem.getSize()));
        }
    }

    private List<ParameterValue> createParameterValues(StaplerRequest2 req) throws IOException, ServletException {
        List<ParameterValue> values = new ArrayList<>();
        values.add(new StringParameterValue("ORGANISATION", req.getParameter("ORGANISATION")));
        values.add(new StringParameterValue("DATASET", req.getParameter("DATASET")));
        values.add(new TextParameterValue("COMMENT", req.getParameter("COMMENT") == null ? "" : req.getParameter("COMMENT")));
        values.add(new StringParameterValue("SERIES_ID", req.getParameter("SERIES_ID") == null ? "" : req.getParameter("SERIES_ID")));
        values.add(new StringParameterValue("PUBLICATION_MODE", req.getParameter("PUBLICATION_MODE") == null ? "delivery" : req.getParameter("PUBLICATION_MODE")));
        values.add(new hudson.model.BooleanParameterValue("RELOAD_PORTAL", Boolean.parseBoolean(req.getParameter("RELOAD_PORTAL"))));
        addFileParameter(values, req, "METADATA_FILE");
        addFileParameter(values, req, "DATA_FILE");
        return values;
    }

    private void addFileParameter(List<ParameterValue> values, StaplerRequest2 req, String name)
            throws IOException, ServletException {
        FileItem fileItem = req.getFileItem2(name);
        if (fileItem != null && fileItem.getSize() > 0) {
            ParameterValue value = new StashedFileParameterDefinition(name).createValue(req);
            if (value != null) {
                values.add(value);
            }
        }
    }
}
