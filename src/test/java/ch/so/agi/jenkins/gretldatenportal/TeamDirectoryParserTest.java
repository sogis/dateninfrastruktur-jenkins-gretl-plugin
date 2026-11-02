package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamDirectoryParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesTeamsAndNormalizesDuplicateUsers() throws IOException {
        Path yaml = tempDir.resolve(TopicRepositoryScanner.TEAMS_FILE);
        Files.writeString(
                yaml,
                """
                teams:
                  datenportal-read:
                    users:
                      - sziegler
                      - sziegler
                      - " mmuster "
                """,
                StandardCharsets.UTF_8);

        TeamDirectory directory = new TeamDirectoryParser().parse(yaml);

        assertEquals(1, directory.getUsersByTeam().size());
        assertEquals(2, directory.getUsers("datenportal-read").size());
        assertTrue(directory.hasTeam("datenportal-read"));
    }

    @Test
    void rejectsEmptyTeams() throws IOException {
        Path yaml = tempDir.resolve(TopicRepositoryScanner.TEAMS_FILE);
        Files.writeString(
                yaml,
                """
                teams:
                  empty:
                    users: []
                """,
                StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new TeamDirectoryParser().parse(yaml));
    }

    @Test
    void rejectsEmptyUserIds() throws IOException {
        Path yaml = tempDir.resolve(TopicRepositoryScanner.TEAMS_FILE);
        Files.writeString(
                yaml,
                """
                teams:
                  operators:
                    users:
                      - ""
                """,
                StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new TeamDirectoryParser().parse(yaml));
    }
}
