package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineScriptResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void usesExplicitOrganizationJenkinsfile() throws IOException {
        Path orgDir = Files.createDirectories(tempDir.resolve("statistikdienst"));
        Path script = Files.createDirectories(orgDir.resolve("pipelines")).resolve("local.Jenkinsfile");
        Files.writeString(script, "pipeline { /* explicit */ }", StandardCharsets.UTF_8);

        OrganizationUnit organization = organization(orgDir, "pipelines/local.Jenkinsfile");

        assertEquals("pipeline { /* explicit */ }", new PipelineScriptResolver().resolve(organization));
    }

    @Test
    void usesImplicitOrganizationJenkinsfile() throws IOException {
        Path orgDir = Files.createDirectories(tempDir.resolve("statistikdienst"));
        Files.writeString(orgDir.resolve("Jenkinsfile"), "pipeline { /* implicit */ }", StandardCharsets.UTF_8);

        OrganizationUnit organization = organization(orgDir, "");

        assertEquals("pipeline { /* implicit */ }", new PipelineScriptResolver().resolve(organization));
    }

    @Test
    void organizationJenkinsfileWinsOverSharedJenkinsfile() throws IOException {
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Files.writeString(sharedDir.resolve("Jenkinsfile"), "pipeline { /* shared */ }", StandardCharsets.UTF_8);
        Path orgDir = Files.createDirectories(tempDir.resolve("statistikdienst"));
        Files.writeString(orgDir.resolve("Jenkinsfile"), "pipeline { /* organization */ }", StandardCharsets.UTF_8);

        OrganizationUnit organization = organization(orgDir, "", RepositoryDefaults.forSharedPath(sharedDir));

        assertEquals("pipeline { /* organization */ }", new PipelineScriptResolver().resolve(organization));
    }

    @Test
    void usesConfiguredSharedJenkinsfile() throws IOException {
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Path script = Files.createDirectories(sharedDir.resolve("pipelines")).resolve("default.Jenkinsfile");
        Files.writeString(
                script,
                "pipeline { /* shared explicit @@TIMEOUT_MINUTES@@ @@GRADLE_TASK@@ @@POST_BLOCK@@ */ }",
                StandardCharsets.UTF_8);
        Path orgDir = Files.createDirectories(tempDir.resolve("afu"));

        RepositoryDefaults defaults = new RepositoryDefaults(
                sharedDir,
                true,
                "",
                null,
                "pipelines/default.Jenkinsfile",
                GuiDefinition.empty(),
                NotificationConfiguration.empty());
        OrganizationUnit organization = organization(orgDir, "", defaults);

        assertEquals(
                "pipeline { /* shared explicit @@TIMEOUT_MINUTES@@ @@GRADLE_TASK@@ @@POST_BLOCK@@ */ }",
                new PipelineScriptResolver().resolve(organization));
    }

    @Test
    void usesImplicitSharedJenkinsfile() throws IOException {
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Files.writeString(sharedDir.resolve("Jenkinsfile"), sharedTemplate(), StandardCharsets.UTF_8);
        Path orgDir = Files.createDirectories(tempDir.resolve("afu"));

        OrganizationUnit organization = organization(orgDir, "", RepositoryDefaults.forSharedPath(sharedDir));

        assertEquals(
                new PipelineJobRenderer().renderTemplate(
                        sharedTemplate(),
                        organization,
                        organization.getNotificationConfiguration()),
                new PipelineScriptResolver().resolve(organization));
    }

    @Test
    void implicitSharedJenkinsfileKeepsOrganizationSpecificSettings() throws IOException {
        Path orgDir = Files.createDirectories(tempDir.resolve("afu"));
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Files.writeString(sharedDir.resolve("Jenkinsfile"), sharedTemplate(), StandardCharsets.UTF_8);
        NotificationConfiguration notifications = new NotificationConfiguration(
                true,
                NotificationConfiguration.Mode.APPEND,
                List.of("alerts@example.invalid"),
                true,
                false,
                true,
                false,
                false);
        OrganizationUnit organization = new OrganizationUnit(
                "afu",
                orgDir,
                true,
                new JobDefinition("afu", "AFU", "", "gretl-datenportal-afu", "publishCustom", "", 45),
                GuiDefinition.empty(),
                notifications,
                PermissionConfiguration.empty(),
                RepositoryDefaults.forSharedPath(sharedDir),
                List.of(new DatasetEntry(
                        "ch.so.dataset",
                        orgDir.resolve("ch.so.dataset"),
                        new DatasetDefinition("ch.so.dataset", "Dataset", "", false),
                        true,
                        false,
                        GuiDefinition.empty())));

        String script = new PipelineScriptResolver().resolve(organization);

        assertTrue(script.contains("timeout(time: 45, unit: 'MINUTES')"));
        assertTrue(script.contains("./gradlew publishCustom"));
        assertTrue(script.contains("to: 'alerts@example.invalid'"));
    }

    @Test
    void rejectsMissingSharedJenkinsfileWhenOrganizationNeedsRepoDefault() throws IOException {
        Path orgDir = Files.createDirectories(tempDir.resolve("afu"));

        OrganizationUnit organization = organization(orgDir, "", RepositoryDefaults.forSharedPath(tempDir.resolve("shared")));

        IOException exception = assertThrows(IOException.class, () -> new PipelineScriptResolver().resolve(organization));

        assertTrue(exception.getMessage().contains("shared/Jenkinsfile"));
    }

    @Test
    void rejectsConfiguredJenkinsfileOutsideOrganizationFolder() throws IOException {
        Path orgDir = Files.createDirectories(tempDir.resolve("afu"));

        OrganizationUnit organization = organization(orgDir, "../Jenkinsfile");

        assertThrows(IOException.class, () -> new PipelineScriptResolver().resolve(organization));
    }

    @Test
    void rejectsConfiguredSharedJenkinsfileOutsideSharedFolder() throws IOException {
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Path orgDir = Files.createDirectories(tempDir.resolve("afu"));

        RepositoryDefaults defaults = new RepositoryDefaults(
                sharedDir,
                true,
                "",
                null,
                "../Jenkinsfile",
                GuiDefinition.empty(),
                NotificationConfiguration.empty());
        OrganizationUnit organization = organization(orgDir, "", defaults);

        assertThrows(IOException.class, () -> new PipelineScriptResolver().resolve(organization));
    }

    private OrganizationUnit organization(Path path, String jenkinsfile) {
        return organization(path, jenkinsfile, RepositoryDefaults.empty());
    }

    private OrganizationUnit organization(Path path, String jenkinsfile, RepositoryDefaults repositoryDefaults) {
        return new OrganizationUnit(
                path.getFileName().toString(),
                path,
                true,
                new JobDefinition(
                        path.getFileName().toString(),
                        path.getFileName().toString(),
                        "",
                        "gretl-datenportal-" + path.getFileName(),
                        "publishToDatenportal",
                        jenkinsfile,
                        60),
                GuiDefinition.empty(),
                NotificationConfiguration.disabled(),
                PermissionConfiguration.empty(),
                repositoryDefaults,
                List.of(new DatasetEntry(
                        "ch.so.dataset",
                        path.resolve("ch.so.dataset"),
                        new DatasetDefinition("ch.so.dataset", "Dataset", "", false),
                        true,
                        false,
                        GuiDefinition.empty())));
    }

    private String sharedTemplate() {
        return """
                pipeline {
                    agent any
                    options {
                        timeout(time: @@TIMEOUT_MINUTES@@, unit: 'MINUTES')
                    }
                    stages {
                        stage('Run') {
                            steps {
                                sh "./gradlew @@GRADLE_TASK@@"
                            }
                        }
                    }
                @@POST_BLOCK@@
                }
                """;
    }
}
