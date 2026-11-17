package ch.so.agi.jenkins.delivery.steps;

import ch.so.agi.jenkins.delivery.upload.IncomingUploadStore;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryStepsTest {
    @TempDir Path home;

    @Test void preservesOriginalBytesAndPermissionsAfterCleanup() throws Exception {
        var store = new IncomingUploadStore(home);
        var id = UUID.randomUUID();
        store.receive(id, 100, (path, limit) -> new IncomingUploadStore.Evidence(0, "", ""));
        var upload = store.existing(id);
        var run = Files.createDirectory(home.resolve("run"));
        var original = "Info: ä\r\nUnknown diagnostic: private value\r\nFinal line"
            .getBytes(StandardCharsets.UTF_8);
        var source = upload.resolve("validation.log");
        Files.write(source, original);
        var output = new ByteArrayOutputStream();
        try (var listener = new StreamTaskListener(output, StandardCharsets.UTF_8)) {
            DeliverySteps.archiveValidationLog(upload, run, listener);
            Files.writeString(source, "Do not overwrite");
            DeliverySteps.archiveValidationLog(upload, run, listener);
            DeliverySteps.removeUpload(store, id, listener);
        }
        var archived = run.resolve("validation.log");
        assertFalse(Files.exists(upload));
        assertArrayEquals(original, Files.readAllBytes(archived));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(archived));
        assertFalse(output.toString(StandardCharsets.UTF_8).contains("private value"));
    }

    @Test void skipsMissingAndSymlinkedLogs() throws Exception {
        var upload = Files.createDirectory(home.resolve("upload"));
        var run = Files.createDirectory(home.resolve("run"));
        var output = new ByteArrayOutputStream();
        try (var listener = new StreamTaskListener(output, StandardCharsets.UTF_8)) {
            DeliverySteps.archiveValidationLog(upload, run, listener);
            var other = Files.writeString(home.resolve("other"), "Private");
            Files.createSymbolicLink(upload.resolve("validation.log"), other);
            DeliverySteps.archiveValidationLog(upload, run, listener);
        }
        assertFalse(Files.exists(run.resolve("validation.log")));
        assertEquals(0, output.size());
    }

    @Test void skipsOversizedLogsWithoutPublishingAPartialCopy() throws Exception {
        var upload = Files.createDirectory(home.resolve("upload"));
        var run = Files.createDirectory(home.resolve("run"));
        var log = upload.resolve("validation.log");
        try (var file = new java.io.RandomAccessFile(log.toFile(), "rw")) {
            file.setLength(64L * 1024 * 1024 + 1);
        }
        var output = new ByteArrayOutputStream();
        try (var listener = new StreamTaskListener(output, StandardCharsets.UTF_8)) {
            DeliverySteps.archiveValidationLog(upload, run, listener);
        }
        try (var entries = Files.list(run)) {
            assertEquals(0, entries.count());
        }
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("überschreitet 64 MiB"));
    }

    @Test void cleanupFailureOnlyAddsWarning() throws Exception {
        var output = new ByteArrayOutputStream();
        var store = new IncomingUploadStore(home);
        var id = UUID.randomUUID();
        store.receive(id, 100, (path, limit) -> new IncomingUploadStore.Evidence(0, "", ""));
        var directory = store.existing(id);
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-x---"));
        try (var listener = new StreamTaskListener(output, StandardCharsets.UTF_8)) {
            assertDoesNotThrow(() -> DeliverySteps.removeUpload(store, id, listener));
        }
        assertTrue(Files.exists(directory.resolve("input.csv")));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains(id.toString()));
    }
}
