package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class GretlDatenportalRootActionRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    @WithJenkins
    void scansOrganizationsFromManagedCheckoutWhenOnlyGitConfigurationIsSet(JenkinsRule jenkinsRule) throws Exception {
        Path sourceRepository = tempDir.resolve("source-repo");
        GitTestSupport.initRepository(sourceRepository);
        GitTestSupport.writeSharedJenkinsfile(sourceRepository);
        GitTestSupport.addOrganization(sourceRepository, "afu", "ch.so.abfall.deponien");
        GitTestSupport.commitAll(sourceRepository, "initial topics");

        GretlDatenportalGlobalConfiguration configuration = GretlDatenportalGlobalConfiguration.get();
        configuration.setTopicRepositoryPath("");
        configuration.setTopicRepositoryUrl(GitTestSupport.fileUrl(sourceRepository));
        configuration.setTopicRepositoryBranch("main");

        GretlDatenportalRootAction action = new GretlDatenportalRootAction();
        ScanResult scanResult = action.getScanResult();

        assertFalse(scanResult.hasErrors());
        assertEquals(1, scanResult.getOrganizations().size());
        assertEquals("afu", scanResult.getOrganizations().get(0).getId());
    }
}
