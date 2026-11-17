package ch.so.agi.jenkins.delivery.web;

import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import ch.so.agi.jenkins.delivery.identity.JenkinsOidcActorResolver;
import ch.so.agi.jenkins.delivery.queue.*;
import ch.so.agi.jenkins.delivery.steps.DeliverySteps;
import ch.so.agi.jenkins.delivery.upload.*;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.security.ACL;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
public final class DeliveryRootAction implements RootAction {
    private static final JenkinsOidcActorResolver IDENTITIES = new JenkinsOidcActorResolver();

    @Override public String getUrlName() { return "anlieferung"; }
    @Override public String getDisplayName() { return "CSV-Anlieferung"; }
    @Override public String getIconFileName() { return "symbol-upload"; }

    public String getCrumbField() {
        var issuer = Jenkins.get().getCrumbIssuer();
        return issuer == null ? "" : issuer.getCrumbRequestField();
    }
    public String getCrumb() {
        var issuer = Jenkins.get().getCrumbIssuer();
        return issuer == null ? "" : issuer.getCrumb();
    }
    public boolean isProcessingReady() { return DeliverySteps.ready() && CsvJobProvisioner.current() != null; }

    private JenkinsOidcActorResolver.UserTokenContext actor(StaplerRequest2 request) {
        Jenkins.get().checkPermission(Jenkins.READ);
        return IDENTITIES.current(request);
    }

    private static UUID uuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            throw DeliveryProblem.invalid();
        }
        return UUID.fromString(value);
    }

    public void doIndex(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        try {
            var user = actor(request);
            String search = Objects.requireNonNullElse(request.getParameter("search"), "");
            if (search.length() > 100) throw DeliveryProblem.invalid();
            request.setAttribute("eligible", new DeliveryApi().eligible(user, search));
            request.setAttribute("search", search);
            view(request, response, "index");
        } catch (DeliveryProblem problem) {
            failure(request, response, problem);
        }
    }

    @RequirePOST
    public void doUpload(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
        try {
            var user = actor(request);
            var config = DeliveryPluginConfiguration.get();
            config.validate();
            if (!Collections.list(request.getHeaders("Origin")).equals(List.of(config.getPublicOrigin()))) {
                throw new DeliveryProblem(403, "NOT_AUTHORIZED");
            }
            var issuer = Jenkins.get().getCrumbIssuer();
            if (issuer == null) throw new DeliveryProblem(403, "NOT_AUTHORIZED");
            var crumbs = Collections.list(request.getHeaders(issuer.getCrumbRequestField()));
            if (crumbs.size() != 1 || crumbs.getFirst().length() > 256
                || !issuer.validateCrumb(request, issuer.getDescriptor().getCrumbSalt(), crumbs.getFirst())) {
                throw new DeliveryProblem(403, "NOT_AUTHORIZED");
            }

            var job = CsvJobProvisioner.current();
            if (job == null || !DeliverySteps.ready()) throw new DeliveryProblem(503, "PROCESSOR_NOT_READY");
            var dataset = uuid(request.getHeader("X-Delivery-Dataset"));
            var issue = uuid(request.getHeader("X-Delivery-Issue"));
            String mode = Objects.requireNonNullElse(request.getHeader("X-Delivery-Mode"), "");
            if (!Set.of("replace", "append").contains(mode)) throw DeliveryProblem.invalid();

            var id = UUID.randomUUID();
            var store = new IncomingUploadStore(Jenkins.get().getRootDir().toPath());
            var upload = store.receive(id, config.getMaxUploadBytes(), (path, limit) -> MultipartCsv.read(request, path, limit));
            var api = new DeliveryApi();
            boolean queued = false;
            try {
                api.create(user, id, new DeliveryApi.Create(dataset, issue, mode,
                    upload.originalName(), upload.size(), upload.sha256()));
                try (var elevated = ACL.as2(ACL.SYSTEM2)) {
                    queued = DeliveryQueueDecision.schedule(Jenkins.get().getQueue(), job, new DeliveryQueueAction(id, user.subject())).isAccepted();
                } catch (RuntimeException error) {
                    api.cancel(user, id);
                    throw DeliveryProblem.unavailable();
                }
                if (!queued) {
                    api.cancel(user, id);
                    throw DeliveryProblem.unavailable();
                }
            } finally {
                if (!queued) store.remove(id);
            }
            json(response, 201, Map.of("statusUrl", request.getContextPath() + "/anlieferung/status?id=" + id));
        } catch (DeliveryProblem problem) {
            json(response, problem.status(), Map.of("code", problem.code(), "message", problem.getMessage()));
        } catch (IOException error) {
            var problem = DeliveryProblem.unavailable();
            json(response, problem.status(), Map.of("code", problem.code(), "message", problem.getMessage()));
        }
    }

    public void doStatus(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        try {
            var user = actor(request);
            var id = uuid(request.getParameter("id"));
            var state = new DeliveryApi().state(user, id);
            request.setAttribute("delivery", state);
            request.setAttribute("deliveryLabel", stateLabel(state.status()));
            showRun(request, findRun(id), user.subject());
            view(request, response, "status");
        } catch (DeliveryProblem problem) {
            failure(request, response, problem);
        }
    }

    public void doValidationLog(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        InputStream input;
        try {
            var user = actor(request);
            var id = uuid(request.getParameter("id"));
            new DeliveryApi().state(user, id);
            var log = validationLog(findRun(id), user.subject());
            if (log == null) {
                response.setStatus(404);
                return;
            }
            input = Files.newInputStream(log, LinkOption.NOFOLLOW_LINKS);
        } catch (DeliveryProblem problem) {
            response.setStatus(problem.status());
            return;
        } catch (IOException error) {
            response.setStatus(404);
            return;
        }
        try (input) {
            response.setContentType("text/plain; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"validation.log\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            input.transferTo(response.getOutputStream());
        }
    }

    // Call after Admin has authorised access to this exact submission.
    static WorkflowRun findRun(UUID id) {
        var job = CsvJobProvisioner.ownedJob();
        if (job == null) return null;
        try (var elevated = ACL.as2(ACL.SYSTEM2)) {
            for (var run : job.getBuilds()) {
                var markers = run.getActions(DeliveryQueueAction.class);
                if (markers.size() == 1 && id.equals(markers.getFirst().submissionId())) return run;
            }
        }
        return null;
    }

    static Path validationLog(WorkflowRun run, String subject) {
        if (run == null || !run.getAction(DeliveryQueueAction.class).submittedBy(subject)) return null;
        var path = run.getRootDir().toPath().resolve(DeliverySteps.VALIDATION_LOG);
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? path : null;
    }

    private void showRun(StaplerRequest2 request, WorkflowRun run, String subject) {
        if (run == null) return;
        request.setAttribute("runNumber", run.getNumber());
        request.setAttribute("validationLogAvailable", validationLog(run, subject) != null);
        try {
            var log = run.getLog(100).stream().map(line -> line.substring(0, Math.min(1024, line.length())));
            request.setAttribute("runLog", String.join("\n", log.toList()));
        } catch (IOException ignored) {
            // The Admin status remains usable when Jenkins logs have been removed.
        }
    }

    private void view(StaplerRequest2 request, StaplerResponse2 response, String name) throws Exception {
        response.setHeader("Cache-Control", "no-store");
        request.getView(this, name + ".jelly").forward(request, response);
    }

    private void failure(StaplerRequest2 request, StaplerResponse2 response, DeliveryProblem problem) throws Exception {
        response.setStatus(problem.status());
        request.setAttribute("problem", problem.getMessage());
        request.setAttribute("loginRequired", problem.status() == 401);
        view(request, response, "problem");
    }

    private static void json(StaplerResponse2 response, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(DeliveryApi.JSON.writeValueAsBytes(value));
    }

    public String stateLabel(String state) {
        return switch (state) {
            case "pending" -> "Wartet auf Verarbeitung";
            case "processing" -> "Verarbeitung läuft";
            case "accepted" -> "Angenommen";
            case "rejected" -> "CSV abgelehnt";
            case "failed" -> "Technischer Fehler";
            case "cancelled" -> "Abgebrochen";
            default -> "Status unbekannt";
        };
    }
}
