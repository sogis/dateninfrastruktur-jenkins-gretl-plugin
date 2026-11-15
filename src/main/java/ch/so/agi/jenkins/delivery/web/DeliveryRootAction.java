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
import java.util.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
public final class DeliveryRootAction implements RootAction {
    private static final JenkinsOidcActorResolver IDENTITIES = new JenkinsOidcActorResolver();

    @Override public String getUrlName() { return "anlieferung"; }
    @Override public String getDisplayName() { return "CSV-Anlieferung"; }
    @Override public String getIconFileName() { return "symbol-upload"; }

    public String getCrumbField() { return Jenkins.get().getCrumbIssuer().getCrumbRequestField(); }
    public String getCrumb() { return Jenkins.get().getCrumbIssuer().getCrumb(); }
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
                    queued = DeliveryQueueDecision.schedule(Jenkins.get().getQueue(), job, new DeliveryQueueAction(id)).isAccepted();
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
            request.setAttribute("delivery", new DeliveryApi().state(user, id));
            view(request, response, "status");
        } catch (DeliveryProblem problem) {
            failure(request, response, problem);
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
