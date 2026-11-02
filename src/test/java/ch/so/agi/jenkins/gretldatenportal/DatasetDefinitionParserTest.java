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

class DatasetDefinitionParserTest {
    @TempDir
    Path tempDir;

    private final DatasetDefinitionParser parser = new DatasetDefinitionParser();

    @Test
    void parsesDatasetXtf() throws IOException {
        Path file = write(
                "dataset.xtf",
                xtf("Dataset", "ch.so.dataset", "Dataset", "Beschreibung"));

        DatasetDefinitionParser.ParseResult result = parser.parse(file);

        assertTrue(result.isValid());
        assertEquals("ch.so.dataset", result.getDefinition().getId());
        assertEquals("Dataset", result.getDefinition().getTitle());
        assertEquals("Beschreibung", result.getDefinition().getDescription());
        assertFalse(result.getDefinition().isSeries());
    }

    @Test
    void parsesDatasetSeriesXtf() throws IOException {
        Path file = write(
                "dataset-series.xml",
                xtf("DatasetSeries", "ch.so.series", "Series", "Serienbeschreibung"));

        DatasetDefinitionParser.ParseResult result = parser.parse(file);

        assertTrue(result.isValid());
        assertTrue(result.getDefinition().isSeries());
    }

    @Test
    void rejectsMissingDescription() throws IOException {
        Path file = write(
                "missing-description.xtf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ili:transfer xmlns="http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523" xmlns:ili="http://www.interlis.ch/xtf/2.4/INTERLIS">
                  <ili:datasection>
                    <Metadata ili:bid="b1">
                      <Dataset ili:tid="ch.so.dataset">
                        <identifier>ch.so.dataset</identifier>
                        <title>Dataset</title>
                      </Dataset>
                    </Metadata>
                  </ili:datasection>
                </ili:transfer>
                """);

        DatasetDefinitionParser.ParseResult result = parser.parse(file);

        assertFalse(result.isValid());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("description")));
    }

    @Test
    void rejectsInvalidMetadataStructure() throws IOException {
        Path file = write(
                "invalid-structure.xtf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ili:transfer xmlns="http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523" xmlns:ili="http://www.interlis.ch/xtf/2.4/INTERLIS">
                  <ili:datasection>
                    <Metadata ili:bid="b1">
                      <Other ili:tid="ch.so.dataset" />
                    </Metadata>
                  </ili:datasection>
                </ili:transfer>
                """);

        DatasetDefinitionParser.ParseResult result = parser.parse(file);

        assertFalse(result.isValid());
        assertTrue(result.getMessages().stream()
                .anyMatch(message -> message.getMessage().contains("Dataset or DatasetSeries")));
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String xtf(String datasetType, String identifier, String title, String description) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <ili:transfer xmlns="http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523" xmlns:ili="http://www.interlis.ch/xtf/2.4/INTERLIS">
                  <ili:datasection>
                    <Metadata ili:bid="b1">
                      <%1$s ili:tid="%2$s">
                        <identifier>%2$s</identifier>
                        <title>%3$s</title>
                        <description>%4$s</description>
                      </%1$s>
                    </Metadata>
                  </ili:datasection>
                </ili:transfer>
                """.formatted(datasetType, identifier, title, description);
    }
}
