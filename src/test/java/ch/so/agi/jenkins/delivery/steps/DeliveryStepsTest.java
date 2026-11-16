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

    @Test void logsBoundedDiagnosticsWithoutValues() throws Exception {
        var output = new ByteArrayOutputStream();
        var log = home.resolve("validation.log");
        Files.writeString(log, """
            Info: dataFile </private/config>
            Error: line 4: Probe.Snapshot.Row: tid o3: Unique constraint Probe.Snapshot.Row.Constraint1 is violated! Values sensitive,row already exist in Object: o2
            Error: line 5: Probe.Snapshot.Row: tid o4: value <secret-sentinel> is not a number in attribute code
            Error: line 6: Probe.Snapshot.Row: tid o5: Attribute code requires a value
            """ + "Error: line 7: Probe.Snapshot.Row: tid o6: value <private> is not a number in attribute code\n".repeat(5000));
        try (var listener = new StreamTaskListener(output, StandardCharsets.UTF_8)) {
            DeliverySteps.logValidation(log, listener);
        }
        var messages = output.toString(StandardCharsets.UTF_8);
        assertTrue(messages.contains("Zeile 4. Unique constraint Probe.Snapshot.Row.Constraint1 is violated!"));
        assertTrue(messages.contains("Zeile 5. Value is not a number in attribute code"));
        assertTrue(messages.contains("Zeile 6. Attribute code requires a value"));
        assertEquals(50, messages.lines().filter(line -> line.startsWith("CSV-Validierung")).count());
        assertTrue(messages.contains("wurden begrenzt"));
        for (var privateValue : new String[]{"private", "secret-sentinel", "sensitive,row"}) {
            assertFalse(messages.contains(privateValue));
        }
    }

    @Test void explainsHeaderMismatchWithoutEchoingHeader() throws Exception {
        var output = new ByteArrayOutputStream();
        var log = home.resolve("validation.log");
        Files.writeString(log, "Error: attributes of headerrecord: [private-zone, private-code]"
            + " not found in iliModel: Probe\n");
        try (var listener = new StreamTaskListener(output, StandardCharsets.UTF_8)) {
            DeliverySteps.logValidation(log, listener);
        }
        var message = output.toString(StandardCharsets.UTF_8);
        assertTrue(message.contains("Die Spaltennamen passen nicht zum gebundenen Modell."));
        assertFalse(message.contains("private-zone") || message.contains("private-code"));
    }

    @Test void missingLogOnlyAddsWarning() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var listener = new StreamTaskListener(output, StandardCharsets.UTF_8)) {
            assertDoesNotThrow(() -> DeliverySteps.logValidation(home.resolve("validation.log"), listener));
        }
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("konnten nicht gelesen werden"));
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
