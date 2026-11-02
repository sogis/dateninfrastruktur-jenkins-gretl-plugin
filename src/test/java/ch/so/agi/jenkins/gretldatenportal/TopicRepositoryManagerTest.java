package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TopicRepositoryManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void clonesAndUpdatesManagedCheckoutFromLocalGitRepository() throws IOException, InterruptedException {
        Path sourceRepository = tempDir.resolve("source-repo");
        GitTestSupport.initRepository(sourceRepository);
        GitTestSupport.writeSharedJenkinsfile(sourceRepository);
        GitTestSupport.addOrganization(sourceRepository, "afu", "ch.so.abfall.deponien");
        GitTestSupport.commitAll(sourceRepository, "initial topics");

        Path checkoutPath = tempDir.resolve("managed/topic-repo");
        TopicRepositoryManager manager = new TopicRepositoryManager();

        Path initialCheckout = manager.ensureManagedCheckout(
                GitTestSupport.fileUrl(sourceRepository),
                "main",
                checkoutPath,
                false,
                null);

        ScanResult initialScan = new TopicRepositoryScanner().scan(initialCheckout);
        assertEquals(1, initialScan.getOrganizations().size());
        assertTrue(initialCheckout.resolve("afu/gretl-datenportal-job.yaml").toFile().isFile());

        GitTestSupport.addOrganization(sourceRepository, "statistikdienst", "ch.so.statistik.bevoelkerung");
        GitTestSupport.commitAll(sourceRepository, "add statistikdienst");

        Path updatedCheckout = manager.ensureManagedCheckout(
                GitTestSupport.fileUrl(sourceRepository),
                "main",
                checkoutPath,
                true,
                null);

        ScanResult updatedScan = new TopicRepositoryScanner().scan(updatedCheckout);
        assertEquals(2, updatedScan.getOrganizations().size());
        assertTrue(updatedCheckout.resolve("statistikdienst/gretl-datenportal-job.yaml").toFile().isFile());
    }

    @Test
    void snapshotsUncommittedAndUntrackedWorkingTreeChangesWithoutCopyingExcludedFiles()
            throws IOException, InterruptedException {
        Path sourceRepository = tempDir.resolve("source-repo");
        GitTestSupport.initRepository(sourceRepository);
        GitTestSupport.writeSharedJenkinsfile(sourceRepository);
        GitTestSupport.addOrganization(sourceRepository, "afu", "ch.so.abfall.deponien");
        GitTestSupport.commitAll(sourceRepository, "initial topics");

        Path trackedFile = sourceRepository.resolve("shared/Jenkinsfile");
        Files.writeString(trackedFile, "uncommitted pipeline change", StandardCharsets.UTF_8);
        Files.writeString(sourceRepository.resolve("shared/local-only.txt"), "untracked change", StandardCharsets.UTF_8);
        Files.createDirectories(sourceRepository.resolve(".gradle/caches"));
        Files.writeString(sourceRepository.resolve(".gradle/caches/ignored.txt"), "ignored", StandardCharsets.UTF_8);
        Files.createDirectories(sourceRepository.resolve("build/reports"));
        Files.writeString(sourceRepository.resolve("build/reports/ignored.txt"), "ignored", StandardCharsets.UTF_8);

        Path checkoutPath = tempDir.resolve("managed/topic-repo");
        TopicRepositoryManager manager = new TopicRepositoryManager();
        Path snapshot = manager.ensureWorkingTreeSnapshot(sourceRepository, checkoutPath, true, null);

        assertEquals("uncommitted pipeline change", Files.readString(snapshot.resolve("shared/Jenkinsfile")));
        assertEquals("untracked change", Files.readString(snapshot.resolve("shared/local-only.txt")));
        assertTrue(Files.notExists(snapshot.resolve(".git")));
        assertTrue(Files.notExists(snapshot.resolve(".gradle")));
        assertTrue(Files.notExists(snapshot.resolve("build")));

        Files.writeString(trackedFile, "second uncommitted change", StandardCharsets.UTF_8);
        Path unchangedSnapshot = manager.ensureWorkingTreeSnapshot(sourceRepository, checkoutPath, false, null);
        assertEquals(snapshot, unchangedSnapshot);
        assertEquals("uncommitted pipeline change", Files.readString(unchangedSnapshot.resolve("shared/Jenkinsfile")));

        manager.ensureWorkingTreeSnapshot(sourceRepository, checkoutPath, true, null);
        assertEquals("second uncommitted change", Files.readString(snapshot.resolve("shared/Jenkinsfile")));
    }
}
