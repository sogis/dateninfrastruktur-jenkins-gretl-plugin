package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class GitTestSupport {
    private GitTestSupport() {
    }

    static void initRepository(Path repositoryPath) throws IOException, InterruptedException {
        Files.createDirectories(repositoryPath);
        run(repositoryPath.getParent(), "git", "init", "-b", "main", repositoryPath.toString());
        run(repositoryPath, "git", "config", "user.email", "local@example.invalid");
        run(repositoryPath, "git", "config", "user.name", "Local Test");
    }

    static void commitAll(Path repositoryPath, String message) throws IOException, InterruptedException {
        run(repositoryPath, "git", "add", ".");
        run(repositoryPath, "git", "commit", "-m", message);
    }

    static void addOrganization(Path repositoryPath, String organizationId, String datasetId)
            throws IOException {
        writeTeams(repositoryPath);
        Path organizationPath = Files.createDirectories(repositoryPath.resolve(organizationId));
        Files.writeString(
                organizationPath.resolve("gretl-datenportal-job.yaml"),
                """
                title: %s Datenportal publizieren
                description: Lokaler Test fuer %s.
                permissions:
                  read:
                    - team: datenportal-read
                  build:
                    - team: datenportal-build
                """.formatted(organizationId.toUpperCase(), organizationId),
                StandardCharsets.UTF_8);

        Path datasetPath = Files.createDirectories(organizationPath.resolve(datasetId));
        Files.writeString(
                datasetPath.resolve(datasetId + "_datasheet.xtf"),
                datasetXml(datasetId, datasetId, "Test description", false),
                StandardCharsets.UTF_8);
    }

    static String datasetXml(String datasetId, String title, String description, boolean series) {
        String datasetType = series ? "DatasetSeries" : "Dataset";
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
                """.formatted(datasetType, datasetId, title, description);
    }

    static void writeSharedJenkinsfile(Path repositoryPath) throws IOException {
        Path sharedPath = Files.createDirectories(repositoryPath.resolve("shared"));
        Files.writeString(
                sharedPath.resolve("Jenkinsfile"),
                """
                pipeline {
                    agent any
                    options {
                        timeout(time: @@TIMEOUT_MINUTES@@, unit: 'MINUTES')
                    }
                    stages {
                        stage('Run') {
                            steps {
                                echo '@@GRADLE_TASK@@'
                            }
                        }
                    }
                @@POST_BLOCK@@
                }
                """,
                StandardCharsets.UTF_8);
    }

    static void writeTeams(Path repositoryPath) throws IOException {
        Path sharedPath = Files.createDirectories(repositoryPath.resolve("shared"));
        Path teamsFile = sharedPath.resolve(TopicRepositoryScanner.TEAMS_FILE);
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

    static String fileUrl(Path path) {
        return path.toUri().toString();
    }

    private static void run(Path workdir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();

        String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed (" + String.join(" ", List.of(command)) + "): " + output);
        }
    }
}
