package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StartFormValidatorTest {
    private final StartFormValidator validator = new StartFormValidator();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsRepositoryMetadataWithoutUploadsOrIssue(boolean series) {
        assertFalse(hasErrors(validator.validate(job(series), submission(
                Map.of("ORGANISATION", "afu", "DATASET", "ch.so.dataset", "PUBLICATION_MODE", "repository-metadata"), Map.of()))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"METADATA_FILE", "DATA_FILE"})
    void rejectsUploadsInRepositoryMode(String field) {
        assertTrue(hasErrors(validator.validate(job(false), submission(
                Map.of("ORGANISATION", "afu", "DATASET", "ch.so.dataset", "PUBLICATION_MODE", "repository-metadata"),
                Map.of(field, new UploadedFileInfo(field.equals("DATA_FILE") ? "data.csv" : "metadata.xtf", 100))))));
    }

    @Test void rejectsUnknownMode() {
        assertTrue(hasErrors(validator.validate(job(false), submission(
                Map.of("ORGANISATION", "afu", "DATASET", "ch.so.dataset", "PUBLICATION_MODE", "unknown"),
                Map.of("METADATA_FILE", new UploadedFileInfo("metadata.xtf",100))))));
    }

    @Test
    void acceptsSeriesMetadataWithoutIssue() {
        List<ValidationMessage> messages = validator.validate(
                job(true),
                submission(
                        Map.of(
                                "ORGANISATION", "afu",
                                "DATASET", "ch.so.dataset"),
                        Map.of("METADATA_FILE", new UploadedFileInfo("metadata.xtf", 100))));

        assertFalse(hasErrors(messages));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026", "   "})
    void acceptsMetadataOnlyWithIrrelevantIssue(String issue) {
        assertFalse(hasErrors(validator.validate(job(true), submission(
                Map.of("ORGANISATION", "afu", "DATASET", "ch.so.dataset", "SERIES_ID", issue),
                Map.of("METADATA_FILE", new UploadedFileInfo("metadata.xtf", 100))))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsDataWithIssueIncludingUnknownEditions(boolean includeMetadata) {
        Map<String, UploadedFileInfo> files = new java.util.HashMap<>();
        files.put("DATA_FILE", new UploadedFileInfo("data.csv", 100));
        if (includeMetadata) {
            files.put("METADATA_FILE", new UploadedFileInfo("metadata.xtf", 100));
        }
        assertFalse(hasErrors(validator.validate(job(true), submission(
                Map.of("ORGANISATION", "afu", "DATASET", "ch.so.dataset", "SERIES_ID", "2099-neu"),
                files))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsWhitespaceIssueForDataDelivery(boolean includeMetadata) {
        Map<String, UploadedFileInfo> files = new java.util.HashMap<>();
        files.put("DATA_FILE", new UploadedFileInfo("data.csv", 100));
        if (includeMetadata) {
            files.put("METADATA_FILE", new UploadedFileInfo("metadata.xtf", 100));
        }
        List<ValidationMessage> messages = validator.validate(job(true), submission(
                Map.of("ORGANISATION", "afu", "DATASET", "ch.so.dataset", "SERIES_ID", "   "),
                files));
        assertTrue(messages.stream().anyMatch(message -> message.getMessage().contains("SERIES_ID")));
    }

    @Test
    void acceptsXmlMetadataUpload() {
        List<ValidationMessage> messages = validator.validate(
                job(false),
                submission(
                        Map.of(
                                "ORGANISATION", "afu",
                                "DATASET", "ch.so.dataset"),
                        Map.of("METADATA_FILE", new UploadedFileInfo("metadata.xml", 100))));

        assertFalse(hasErrors(messages));
    }

    @Test
    void rejectsMissingUploads() {
        List<ValidationMessage> messages = validator.validate(
                job(false),
                submission(Map.of(
                        "ORGANISATION", "afu",
                        "DATASET", "ch.so.dataset"), Map.of()));

        assertTrue(messages.stream().anyMatch(message -> message.getMessage().contains("At least one")));
    }

    @Test
    void rejectsMissingSeriesId() {
        List<ValidationMessage> messages = validator.validate(
                job(true),
                submission(
                        Map.of(
                                "ORGANISATION", "afu",
                                "DATASET", "ch.so.dataset"),
                        Map.of("DATA_FILE", new UploadedFileInfo("data.csv", 100))));

        assertTrue(messages.stream().anyMatch(message -> message.getMessage().contains("SERIES_ID")));
    }

    @Test
    void doesNotRequireProductionConfirmationAnymore() {
        List<ValidationMessage> messages = validator.validate(
                job(false),
                submission(
                        Map.of(
                                "ORGANISATION", "afu",
                                "DATASET", "ch.so.dataset"),
                        Map.of("DATA_FILE", new UploadedFileInfo("data.csv", 100))));

        assertFalse(hasErrors(messages));
    }

    @Test
    void rejectsJsonMetadataUpload() {
        List<ValidationMessage> messages = validator.validate(
                job(false),
                submission(
                        Map.of(
                                "ORGANISATION", "afu",
                                "DATASET", "ch.so.dataset"),
                        Map.of("METADATA_FILE", new UploadedFileInfo("metadata.json", 100))));

        assertTrue(messages.stream().anyMatch(message -> message.getMessage().contains("METADATA_FILE")));
    }

    @Test
    void rejectsNonCsvDataUpload() {
        List<ValidationMessage> messages = validator.validate(
                job(false),
                submission(
                        Map.of(
                                "ORGANISATION", "afu",
                                "DATASET", "ch.so.dataset"),
                        Map.of("DATA_FILE", new UploadedFileInfo("data.xlsx", 100))));

        assertTrue(messages.stream().anyMatch(message -> message.getMessage().contains("DATA_FILE")));
    }

    private ResolvedDatenportalJob job(boolean series) {
        OrganizationUnit organization = new OrganizationUnit(
                "afu",
                Path.of("afu"),
                true,
                JobDefinition.defaultFor("afu"),
                GuiDefinition.empty(),
                NotificationConfiguration.disabled(),
                List.of());
        DatasetEntry dataset = new DatasetEntry(
                "ch.so.dataset",
                Path.of("afu/ch.so.dataset"),
                new DatasetDefinition("ch.so.dataset", "Dataset", "", series),
                true,
                false,
                GuiDefinition.empty());
        GuiDefinition gui = new DefaultGuiDefinitionFactory().create(series);
        return new ResolvedDatenportalJob(organization, dataset, gui, NotificationConfiguration.disabled());
    }

    private StartFormSubmission submission(Map<String, String> values, Map<String, UploadedFileInfo> files) {
        return new StartFormSubmission(values, files);
    }

    private boolean hasErrors(List<ValidationMessage> messages) {
        return messages.stream().anyMatch(ValidationMessage::isError);
    }
}
