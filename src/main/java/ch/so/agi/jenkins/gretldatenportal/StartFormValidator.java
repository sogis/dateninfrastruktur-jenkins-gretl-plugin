package ch.so.agi.jenkins.gretldatenportal;

import java.util.ArrayList;
import java.util.List;

public final class StartFormValidator {
    private static final long METADATA_FILE_MAX_BYTES = 10L * 1024L * 1024L;
    private static final long DATA_FILE_MAX_BYTES = 100L * 1024L * 1024L;

    public List<ValidationMessage> validate(ResolvedDatenportalJob job, StartFormSubmission submission) {
        List<ValidationMessage> messages = new ArrayList<>();
        OrganizationUnit organization = job.getOrganization();
        DatasetEntry dataset = job.getDataset();

        if (!organization.getId().equals(submission.value("ORGANISATION"))) {
            messages.add(error("ORGANISATION must match the selected organization."));
        }
        if (!dataset.getId().equals(submission.value("DATASET"))) {
            messages.add(error("DATASET must belong to the selected organization."));
        }

        if (submission.hasFile("METADATA_FILE")) {
            validateFile("METADATA_FILE", submission.file("METADATA_FILE"), List.of("xtf", "xml"), METADATA_FILE_MAX_BYTES, messages);
        }
        if (submission.hasFile("DATA_FILE")) {
            validateFile("DATA_FILE", submission.file("DATA_FILE"), List.of("csv"), DATA_FILE_MAX_BYTES, messages);
        }

        String mode = submission.value("PUBLICATION_MODE");
        if (mode == null || mode.isBlank()) mode = "delivery";
        if (!List.of("delivery", "repository-metadata").contains(mode)) messages.add(error("Unknown PUBLICATION_MODE."));
        if ("repository-metadata".equals(mode) && (submission.hasFile("METADATA_FILE") || submission.hasFile("DATA_FILE")))
            messages.add(error("Repository metadata mode does not accept uploads."));
        if ("delivery".equals(mode) && !submission.hasFile("METADATA_FILE") && !submission.hasFile("DATA_FILE")) {
            messages.add(error("At least one of METADATA_FILE or DATA_FILE is required."));
        }
        if (dataset.getDefinition().isSeries() && submission.hasFile("DATA_FILE")
                && isBlank(submission.value("SERIES_ID"))) {
            messages.add(error("Ausgabe (SERIES_ID) is required when supplying data for a series."));
        }
        return messages;
    }

    private void validateFile(
            String fieldId,
            UploadedFileInfo file,
            List<String> allowedExtensions,
            long maxBytes,
            List<ValidationMessage> messages) {
        if (!allowedExtensions.isEmpty()
                && !allowedExtensions.contains(file.getExtension())) {
            messages.add(error(fieldId + " has an invalid file extension."));
        }
        if (file.getSizeBytes() > maxBytes) {
            messages.add(error(fieldId + " exceeds the maximum file size."));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ValidationMessage error(String message) {
        return new ValidationMessage(ValidationMessage.Severity.ERROR, message, null);
    }
}
