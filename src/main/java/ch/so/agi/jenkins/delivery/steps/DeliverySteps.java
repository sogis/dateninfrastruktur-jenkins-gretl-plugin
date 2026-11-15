package ch.so.agi.jenkins.delivery.steps;

import ch.so.agi.jenkins.delivery.client.DeliveryApi;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import ch.so.agi.jenkins.delivery.identity.DeliveryCredentials;
import ch.so.agi.jenkins.delivery.identity.ServiceTokenClient;
import ch.so.agi.jenkins.delivery.queue.CsvJobProvisioner;
import ch.so.agi.jenkins.delivery.queue.DeliveryQueueAction;
import ch.so.agi.jenkins.delivery.upload.IncomingUploadStore;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

public final class DeliverySteps extends Step {
    private static final Path CLI = Path.of("/opt/gretl/bin/gretl");
    private static final Path JOB = Path.of("/opt/dateninfrastruktur/csv-delivery/job.groovy");
    private static final String CLICKHOUSE = "http://clickhouse:8123";
    private static final String CLICKHOUSE_USER = "ch_delivery";

    @DataBoundConstructor
    public DeliverySteps() {}

    public static boolean ready() {
        return Files.isExecutable(CLI) && Files.isRegularFile(JOB);
    }

    @Override public StepExecution start(StepContext context) { return new Execution(context); }

    @Extension
    public static final class Descriptor extends StepDescriptor {
        @Override public String getFunctionName() { return "diRunCsv"; }
        @Override public String getDisplayName() { return "GRETL CSV-Anlieferung ausführen"; }
        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, FilePath.class, TaskListener.class);
        }
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;
        Execution(StepContext context) { super(context); }

        @Override protected Void run() throws Exception {
            if (!(getContext().get(Run.class) instanceof WorkflowRun run)
                || !CsvJobProvisioner.usable(run.getParent())
                || run.getActions(DeliveryQueueAction.class).size() != 1
                || !(run.getExecution() instanceof CpsFlowExecution flow)
                || !CsvJobProvisioner.EXECUTOR.equals(flow.getAuthentication2().getName())
                || getContext().get(FilePath.class).isRemote()) {
                throw new AbortException("Kein gebundener technischer CSV-Lauf.");
            }
            UUID id = run.getAction(DeliveryQueueAction.class).submissionId();
            var listener = getContext().get(TaskListener.class);
            var store = new IncomingUploadStore(Jenkins.get().getRootDir().toPath());
            Path dir = store.existing(id);
            var config = DeliveryPluginConfiguration.get();
            try {
                if (!ready() || !Files.isRegularFile(dir.resolve("input.csv"), LinkOption.NOFOLLOW_LINKS)) {
                    throw new AbortException("GRETL oder die private Upload-Datei fehlt.");
                }
                prepare(dir, id, config);
                var launcher = new Launcher.LocalLauncher(listener);
                var cookie = EnvVars.createCookie();
                var environment = new EnvVars(cookie);
                environment.put("JAVA_HOME", "/opt/java/gretl");
                environment.put("JAVA_TOOL_OPTIONS", "-Duser.timezone=UTC");
                try {
                    var process = launcher.launch()
                        .cmds(CLI.toString(), "run", JOB.toString(),
                            "--params-file", "params.json", "--config", "runtime.json", "--report", "run.json")
                        .envs(environment).pwd(dir.toFile()).stdout(OutputStream.nullOutputStream()).start();
                    int exit = process.joinWithTimeout(31, TimeUnit.MINUTES, listener);
                    logReport(dir.resolve("run.json"), listener);
                    if (exit != 0) {
                        throw new AbortException("GRETL-Lauf fehlgeschlagen. Details stehen im Joblog.");
                    }
                } finally {
                    launcher.kill(cookie);
                }
            } catch (InterruptedException error) {
                reportFailure(id, listener);
                throw error;
            } catch (Exception error) {
                reportFailure(id, listener);
                if (error instanceof AbortException abort) throw abort;
                listener.getLogger().println("GRETL wurde unterbrochen: " + error.getClass().getSimpleName());
                throw new AbortException("GRETL-Lauf konnte nicht ausgeführt werden.");
            } finally {
                store.remove(id);
            }
            return null;
        }
    }

    private static void logReport(Path report, TaskListener listener) {
        try {
            if (!Files.isRegularFile(report) || Files.size(report) > 1024 * 1024) {
                listener.getLogger().println("Kein GRETL-Runbericht verfügbar.");
                return;
            }
            var result = DeliveryApi.JSON.readTree(Files.readAllBytes(report));
            for (var task : result.path("execution").path("tasks")) {
                String id = task.path("taskId").asString("");
                String status = task.path("status").asString("");
                if (id.matches("[A-Za-z][A-Za-z0-9_]{0,63}") && status.matches("[A-Z_]{1,24}")) {
                    listener.getLogger().println("GRETL " + id + " " + status);
                }
            }
            for (var error : result.path("errors")) {
                String code = error.path("code").asString("");
                if (code.matches("[A-Z0-9_]{1,80}")) listener.getLogger().println("GRETL " + code);
            }
        } catch (IOException | RuntimeException error) {
            listener.getLogger().println("GRETL-Runbericht konnte nicht gelesen werden.");
        }
    }

    private static void prepare(Path dir, UUID id, DeliveryPluginConfiguration config) throws IOException {
        var credentials = Map.of(
            "oauthClientSecret", DeliveryCredentials.OAUTH,
            "s3SecretKey", DeliveryCredentials.STORAGE_SECRET,
            "clickhousePassword", DeliveryCredentials.CLICKHOUSE);
        var secrets = new java.util.HashMap<String, Map<String, String>>();
        for (var credential : credentials.entrySet()) {
            writePrivate(dir.resolve(credential.getKey()), DeliveryCredentials.read(credential.getValue()));
            secrets.put(credential.getKey(), Map.of("file", credential.getKey()));
        }
        writePrivate(dir.resolve("params.json"), DeliveryApi.JSON.writeValueAsString(Map.of(
            "submissionId", id.toString(), "inputFile", "input.csv",
            "adminUrl", config.backendBaseUri().toString().replaceFirst("/$", ""),
            "tokenUrl", config.serviceTokenUri().toString(), "localHttp", "true",
            "s3Endpoint", "http://garage:3900",
            "s3AccessKey", DeliveryCredentials.read(DeliveryCredentials.STORAGE_ID),
            "clickhouseUrl", CLICKHOUSE, "clickhouseUser", CLICKHOUSE_USER)));
        writePrivate(dir.resolve("runtime.json"), DeliveryApi.JSON.writeValueAsString(Map.of(
            "schemaVersion", 1,
            "runtime", Map.of("workRoot", "work", "reportRoot", "reports", "timeout", "PT30M"),
            "secrets", secrets,
            "taskConfig", Map.of("clickhouse", Map.of(
                "jdbcUrl", "jdbc:clickhouse://" + CLICKHOUSE.substring("http://".length())
                    + "/default?jdbc_ignore_unsupported_values=true",
                "user", CLICKHOUSE_USER, "passwordSecret", "clickhousePassword")))));
    }

    private static void writePrivate(Path path, String content) throws IOException {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(path, content);
    }

    private static void reportFailure(UUID id, TaskListener listener) {
        try {
            new DeliveryApi().fail(ServiceTokenClient.acquire(), id);
        } catch (ch.so.agi.jenkins.delivery.client.DeliveryProblem error) {
            listener.getLogger().println("Admin hat die Fehlermeldung nicht bestätigt (HTTP " + error.status() + ").");
        } catch (RuntimeException error) {
            listener.getLogger().println("Admin hat keine technische Fehlermeldung bestätigt.");
        }
    }
}
