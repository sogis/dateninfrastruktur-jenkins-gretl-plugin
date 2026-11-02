package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TopicRepositoryScannerTest {
    @TempDir
    Path tempDir;

    private final TopicRepositoryScanner scanner = new TopicRepositoryScanner();

    @Test
    void scansValidRepository() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        writeDataset("afu", "ch.so.gewaesser.wasserqualitaet", "Wasserqualitaet", true);
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        assertEquals(1, result.getOrganizations().size());
        OrganizationUnit organization = result.getOrganizations().get(0);
        assertEquals("afu", organization.getId());
        assertTrue(organization.isJobDefinitionPresent());
        assertEquals(2, organization.getDatasets().size());
        assertEquals("gretl-datenportal-afu", organization.getDefaultJobName());
        assertTrue(organization.getDatasets().stream()
                .anyMatch(dataset -> dataset.getId().equals("ch.so.gewaesser.wasserqualitaet")
                        && dataset.getDefinition().isSeries()));
    }

    @Test
    void reportsMissingDatasetMetadataFile() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        Files.createDirectories(tempDir.resolve("afu/ch.so.missing.definition"));

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("missing a dataset metadata file")));
        assertEquals(1, result.getOrganizations().get(0).getDatasets().size());
        assertFalse(result.getOrganizations().get(0).getDatasets().get(0).isDefinitionValid());
    }

    @Test
    void reportsInvalidDatasetMetadataXml() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        Path datasetDir = Files.createDirectories(tempDir.resolve("afu/ch.so.invalid.series"));
        Files.writeString(
                datasetDir.resolve("dataset.xtf"),
                "<broken>",
                StandardCharsets.UTF_8);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("Could not read or parse dataset metadata XML/XTF")));
    }

    @Test
    void reportsDatasetIdMismatch() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        Path datasetDir = Files.createDirectories(tempDir.resolve("afu/ch.so.folder.name"));
        Files.writeString(
                datasetDir.resolve("wrong-id.xtf"),
                GitTestSupport.datasetXml("ch.so.other.name", "Wrong ID", "Description", false),
                StandardCharsets.UTF_8);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("identifier must match")));
    }

    @Test
    void reportsMultipleDatasetMetadataFiles() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        Path datasetDir = Files.createDirectories(tempDir.resolve("afu/ch.so.multiple"));
        Files.writeString(
                datasetDir.resolve("first.xtf"),
                GitTestSupport.datasetXml("ch.so.multiple", "Multiple", "Description", false),
                StandardCharsets.UTF_8);
        Files.writeString(
                datasetDir.resolve("second.xml"),
                GitTestSupport.datasetXml("ch.so.multiple", "Multiple", "Description", false),
                StandardCharsets.UTF_8);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("exactly one dataset metadata file")));
    }

    @Test
    void reportsMissingRequiredDatasetElements() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        Path datasetDir = Files.createDirectories(tempDir.resolve("afu/ch.so.missing.description"));
        Files.writeString(
                datasetDir.resolve("dataset.xtf"),
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ili:transfer xmlns="http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523" xmlns:ili="http://www.interlis.ch/xtf/2.4/INTERLIS">
                  <ili:datasection>
                    <Metadata ili:bid="b1">
                      <Dataset ili:tid="ch.so.missing.description">
                        <identifier>ch.so.missing.description</identifier>
                        <title>Missing Description</title>
                      </Dataset>
                    </Metadata>
                  </ili:datasection>
                </ili:transfer>
                """,
                StandardCharsets.UTF_8);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("description")));
    }

    @Test
    void reportsInvalidDatasetRootType() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        Path datasetDir = Files.createDirectories(tempDir.resolve("afu/ch.so.invalid.type"));
        Files.writeString(
                datasetDir.resolve("dataset.xtf"),
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ili:transfer xmlns="http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523" xmlns:ili="http://www.interlis.ch/xtf/2.4/INTERLIS">
                  <ili:datasection>
                    <Metadata ili:bid="b1">
                      <Other ili:tid="ch.so.invalid.type">
                        <identifier>ch.so.invalid.type</identifier>
                        <title>Other</title>
                        <description>Other</description>
                      </Other>
                    </Metadata>
                  </ili:datasection>
                </ili:transfer>
                """,
                StandardCharsets.UTF_8);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("Dataset or DatasetSeries")));
    }

    @Test
    void ignoresHiddenDirectoriesAndReportsEmptyOrganization() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("leere-org");
        Files.createDirectories(tempDir.resolve(".ignored/ch.so.hidden"));

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        assertEquals(1, result.getOrganizations().size());
        assertEquals("leere-org", result.getOrganizations().get(0).getId());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("contains no dataset folders")));
    }

    @Test
    void ignoresTopLevelTechnicalDirectoriesWithoutWarnings() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);
        Files.createDirectories(tempDir.resolve("gradle/wrapper"));

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        assertEquals(1, result.getOrganizations().size());
        assertTrue(result.getMessages().stream()
                .noneMatch(message -> message.getPath() != null && message.getPath().contains("/gradle")));
    }

    @Test
    void appliesSharedDefaultsAndDoesNotScanSharedAsOrganization() throws IOException {
        writeSharedJenkinsfile();
        writeSharedDefaults(
                """
                execution:
                  gradleTask: publishShared
                  timeoutMinutes: 25
                notifications:
                  email:
                    recipients:
                      - data@example.invalid
                """);
        writeOrganization("afu");
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        assertEquals(1, result.getOrganizations().size());
        OrganizationUnit organization = result.getOrganizations().get(0);
        assertEquals("afu", organization.getId());
        assertEquals("publishShared", organization.getJobDefinition().getGradleTask());
        assertEquals(25, organization.getJobDefinition().getTimeoutMinutes());
        assertEquals(
                "data@example.invalid",
                organization.getNotificationConfiguration().getEmailRecipientsCsv());
    }

    @Test
    void organizationExecutionOverridesSharedDefaults() throws IOException {
        writeSharedJenkinsfile();
        writeSharedDefaults(
                """
                execution:
                  gradleTask: publishShared
                  timeoutMinutes: 25
                """);
        writeOrganization(
                "afu",
                """
                permissions:
                  read:
                    - team: datenportal-read
                  build:
                    - team: datenportal-build
                execution:
                  gradleTask: publishAfu
                  timeoutMinutes: 45
                """);
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        OrganizationUnit organization = result.getOrganizations().get(0);
        assertEquals("publishAfu", organization.getJobDefinition().getGradleTask());
        assertEquals(45, organization.getJobDefinition().getTimeoutMinutes());
    }

    @Test
    void rejectsGuiInSharedDefaults() throws IOException {
        writeSharedJenkinsfile();
        writeSharedDefaults(
                """
                gui:
                  fields:
                    - id: COMMENT
                      label: Gemeinsamer Kommentar
                """);
        writeOrganization("afu");
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("unsupported gui configuration")));
    }

    @Test
    void rejectsGuiInOrganizationJobDefinition() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization(
                "afu",
                """
                permissions:
                  read:
                    - team: datenportal-read
                  build:
                    - team: datenportal-build
                gui:
                  fields:
                    - id: COMMENT
                      label: Kommentar AFU
                """);
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("unsupported gui configuration")));
        assertTrue(result.getOrganizations().isEmpty());
    }

    @Test
    void rejectsDatasetGuiOverrides() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);
        Files.writeString(
                tempDir.resolve("afu/ch.so.abfall.deponien/dataset-gui.yaml"),
                """
                gui:
                  fields:
                    - id: COMMENT
                      label: Kommentar
                """,
                StandardCharsets.UTF_8);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("dataset-gui.yaml is no longer supported")));
        assertFalse(result.getOrganizations().get(0).getDatasets().get(0).isDefinitionValid());
    }

    @Test
    void allowsSharedDirectoryWithoutDefaultsFileWhenSharedJenkinsfileExists() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        assertEquals(1, result.getOrganizations().size());
        assertEquals("publishToDatenportal", result.getOrganizations().get(0).getJobDefinition().getGradleTask());
    }

    @Test
    void reportsMissingOrganizationJobDefinition() throws IOException {
        writeSharedJenkinsfile();
        writeSharedTeams();
        writeDataset("afu", "ch.so.gewaesser.wasserqualitaet", "Wasserqualitaet", false);

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getSeverity() == ValidationMessage.Severity.WARNING
                        && message.getMessage().contains("no gretl-datenportal-job.yaml")));
        assertTrue(result.getOrganizations().isEmpty());
    }

    @Test
    void ignoresTechnicalDirectoriesInsideOrganization() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization("afu");
        writeDataset("afu", "ch.so.gewaesser.wasserqualitaet", "Wasserqualitaet", false);
        Files.createDirectories(tempDir.resolve("afu/gradle/wrapper"));
        Files.createDirectories(tempDir.resolve("afu/build"));
        Files.createDirectories(tempDir.resolve("afu/scripts"));

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        assertEquals(1, result.getOrganizations().size());
        assertEquals(1, result.getOrganizations().get(0).getDatasets().size());
        assertTrue(result.getMessages().stream()
                .noneMatch(message -> message.getPath() != null && message.getPath().contains("/afu/gradle")));
    }

    @Test
    void ignoresOrganizationWhenPermissionsBlockIsMissing() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization(
                "afu",
                "");
        writeDataset("afu", "ch.so.gewaesser.wasserqualitaet", "Wasserqualitaet", false);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getOrganizations().isEmpty());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("permissions.read must contain at least one team.")));
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("permissions.build must contain at least one team.")));
    }

    @Test
    void ignoresOrganizationWhenReadPermissionsAreMissing() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization(
                "afu",
                """
                permissions:
                  build:
                    - team: datenportal-build
                """);
        writeDataset("afu", "ch.so.gewaesser.wasserqualitaet", "Wasserqualitaet", false);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getOrganizations().isEmpty());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("permissions.read must contain at least one team.")));
    }

    @Test
    void ignoresOrganizationWhenBuildPermissionsAreMissing() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization(
                "afu",
                """
                permissions:
                  read:
                    - team: datenportal-read
                """);
        writeDataset("afu", "ch.so.gewaesser.wasserqualitaet", "Wasserqualitaet", false);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getOrganizations().isEmpty());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("permissions.build must contain at least one team.")));
    }

    @Test
    void reportsMissingSharedJenkinsfileWhenOrganizationNeedsRepoDefault() throws IOException {
        Files.createDirectories(tempDir.resolve("shared"));
        writeOrganization("afu");
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);

        ScanResult result = scanner.scan(tempDir);

        assertTrue(result.hasErrors());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("missing shared/Jenkinsfile")));
    }

    @Test
    void doesNotRequireSharedJenkinsfileWhenAllOrganizationsHaveLocalOverrides() throws IOException {
        writeOrganization("statistikdienst");
        writeOrganizationJenkinsfile("statistikdienst", "pipeline { /* organization */ }");
        writeDataset("statistikdienst", "ch.so.statistik.bevoelkerung", "Bevoelkerung", false);
        Files.createDirectories(tempDir.resolve("statistikdienst/ch.so.statistik.bevoelkerung/examples"));

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
    }

    @Test
    void ignoresOrganizationYamlIdWhenItDiffersFromFolderName() throws IOException {
        writeSharedJenkinsfile();
        writeOrganization(
                "afu",
                """
                id: wrong-id
                permissions:
                  read:
                    - team: datenportal-read
                  build:
                    - team: datenportal-build
                """);
        writeDataset("afu", "ch.so.abfall.deponien", "Deponien", false);

        ScanResult result = scanner.scan(tempDir);

        assertFalse(result.hasErrors());
        assertEquals(1, result.getOrganizations().size());
        assertEquals("afu", result.getOrganizations().get(0).getId());
        assertEquals("afu", result.getOrganizations().get(0).getJobDefinition().getId());
    }

    private void writeOrganization(String id) throws IOException {
        writeOrganization(
                id,
                """
                permissions:
                  read:
                    - team: datenportal-read
                  build:
                    - team: datenportal-build
                """.formatted(id.toUpperCase()));
    }

    private void writeOrganization(String id, String yaml) throws IOException {
        writeSharedTeams();
        Path orgDir = Files.createDirectories(tempDir.resolve(id));
        Files.writeString(
                orgDir.resolve("gretl-datenportal-job.yaml"),
                yaml,
                StandardCharsets.UTF_8);
    }

    private void writeSharedDefaults(String yaml) throws IOException {
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Files.writeString(
                sharedDir.resolve("gretl-datenportal-defaults.yaml"),
                yaml,
                StandardCharsets.UTF_8);
    }

    private void writeSharedTeams() throws IOException {
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Path teamsFile = sharedDir.resolve(TopicRepositoryScanner.TEAMS_FILE);
        if (!Files.exists(teamsFile)) {
            Files.writeString(
                    teamsFile,
                    """
                    teams:
                      datenportal-read:
                        users:
                          - read-user
                      datenportal-build:
                        users:
                          - build-user
                      gretl-datenportal-seed-operators:
                        users:
                          - seed-user
                    """,
                    StandardCharsets.UTF_8);
        }
    }

    private void writeSharedJenkinsfile() throws IOException {
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Files.writeString(
                sharedDir.resolve("Jenkinsfile"),
                """
                pipeline {
                    /* shared */
                }
                """,
                StandardCharsets.UTF_8);
    }

    private void writeOrganizationJenkinsfile(String orgId, String script) throws IOException {
        Path orgDir = Files.createDirectories(tempDir.resolve(orgId));
        Files.writeString(orgDir.resolve("Jenkinsfile"), script, StandardCharsets.UTF_8);
    }

    private void writeDataset(String orgId, String datasetId, String title, boolean series) throws IOException {
        Path datasetDir = Files.createDirectories(tempDir.resolve(orgId).resolve(datasetId));
        Files.writeString(
                datasetDir.resolve(datasetId + "_datasheet.xtf"),
                GitTestSupport.datasetXml(datasetId, title, "Description for " + datasetId, series),
                StandardCharsets.UTF_8);
    }
}
