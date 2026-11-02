package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineJobRendererTest {
    private static final String TEMPLATE = """
            pipeline {
                agent any

                options {
                    timeout(time: @@TIMEOUT_MINUTES@@, unit: 'MINUTES')
                    disableConcurrentBuilds()
                }

                stages {
                    stage('Validate parameters') {
                        steps {
                            script {
                                if (!(params.ORGANISATION ?: '').trim()) {
                                    error('ORGANISATION muss gesetzt sein.')
                                }
                                if (!(params.DATASET ?: '').trim()) {
                                    error('DATASET muss gesetzt sein.')
                                }
                                currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.ORGANISATION} / ${params.DATASET}"
                                currentBuild.description = "Datenportal: ${params.ORGANISATION} / ${params.DATASET}"
                            }
                        }
                    }

                    stage('Run GRETL Datenportal Job') {
                        steps {
                            script {
                                def gradleArgs = [
                                    "-Porganisation=${params.ORGANISATION}",
                                    "-Pdataset=${params.DATASET}"
                                ]
                                if ((params.SERIES_ID ?: '').trim()) {
                                    gradleArgs << "-PseriesId=${params.SERIES_ID}"
                                }

                                def metadataPresent = false
                                def dataPresent = false

                                try {
                                    withFileParameter('METADATA_FILE') {
                                        if (env.METADATA_FILE) {
                                            metadataPresent = true
                                            gradleArgs << "-PmetadataFile=${env.METADATA_FILE}"
                                            gradleArgs << "-PmetadataFileName=${env.METADATA_FILE_FILENAME ?: 'METADATA_FILE'}"
                                        }
                                    }
                                } catch (ignored) {
                                    echo 'No METADATA_FILE uploaded.'
                                }

                                try {
                                    withFileParameter('DATA_FILE') {
                                        if (env.DATA_FILE) {
                                            dataPresent = true
                                            gradleArgs << "-PdataFile=${env.DATA_FILE}"
                                            gradleArgs << "-PdataFileName=${env.DATA_FILE_FILENAME ?: 'DATA_FILE'}"
                                        }
                                    }
                                } catch (ignored) {
                                    echo 'No DATA_FILE uploaded.'
                                }

                                if (!metadataPresent && !dataPresent) {
                                    error('Mindestens METADATA_FILE oder DATA_FILE muss hochgeladen werden.')
                                }

                                sh "./gradlew @@GRADLE_TASK@@ ${gradleArgs.join(' ')}"
                            }
                        }
                    }
                }

            @@POST_BLOCK@@
            }

            String gretlDatenportalEmailBody(String status) {
                return \"\"\"Status: ${status}
            Organisation: ${params.ORGANISATION}
            Datensatz: ${params.DATASET}
            Build: ${env.BUILD_URL}
            Console: ${env.BUILD_URL}console
            \"\"\"
            }
            """;

    @Test
    void rendersUploadAndGradleContract() {
        OrganizationUnit organization = organization("publishToDatenportal", 60, NotificationConfiguration.disabled());

        String script = new PipelineJobRenderer().renderTemplate(TEMPLATE, organization, NotificationConfiguration.disabled());

        assertTrue(script.contains("withFileParameter('METADATA_FILE')"));
        assertTrue(script.contains("withFileParameter('DATA_FILE')"));
        assertTrue(script.contains("./gradlew publishToDatenportal"));
        assertTrue(script.contains("-PmetadataFileName="));
        assertTrue(script.contains("-PdataFileName="));
        assertTrue(script.contains("-PseriesId="));
        assertFalse(script.contains("-Penvironment="));
        assertFalse(script.contains("-PdryRun="));
        assertFalse(script.contains("CONFIRM_PRODUCTION"));
    }

    @Test
    void rendersTemplateDeterministically() {
        OrganizationUnit organization = organization("publishToDatenportal", 60, NotificationConfiguration.disabled());

        String script = new PipelineJobRenderer().renderTemplate(TEMPLATE, organization, NotificationConfiguration.disabled());

        assertEquals(
                """
                pipeline {
                    agent any

                    options {
                        timeout(time: 60, unit: 'MINUTES')
                        disableConcurrentBuilds()
                    }

                    stages {
                        stage('Validate parameters') {
                            steps {
                                script {
                                    if (!(params.ORGANISATION ?: '').trim()) {
                                        error('ORGANISATION muss gesetzt sein.')
                                    }
                                    if (!(params.DATASET ?: '').trim()) {
                                        error('DATASET muss gesetzt sein.')
                                    }
                                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.ORGANISATION} / ${params.DATASET}"
                                    currentBuild.description = "Datenportal: ${params.ORGANISATION} / ${params.DATASET}"
                                }
                            }
                        }

                        stage('Run GRETL Datenportal Job') {
                            steps {
                                script {
                                    def gradleArgs = [
                                        "-Porganisation=${params.ORGANISATION}",
                                        "-Pdataset=${params.DATASET}"
                                    ]
                                    if ((params.SERIES_ID ?: '').trim()) {
                                        gradleArgs << "-PseriesId=${params.SERIES_ID}"
                                    }

                                    def metadataPresent = false
                                    def dataPresent = false

                                    try {
                                        withFileParameter('METADATA_FILE') {
                                            if (env.METADATA_FILE) {
                                                metadataPresent = true
                                                gradleArgs << "-PmetadataFile=${env.METADATA_FILE}"
                                                gradleArgs << "-PmetadataFileName=${env.METADATA_FILE_FILENAME ?: 'METADATA_FILE'}"
                                            }
                                        }
                                    } catch (ignored) {
                                        echo 'No METADATA_FILE uploaded.'
                                    }

                                    try {
                                        withFileParameter('DATA_FILE') {
                                            if (env.DATA_FILE) {
                                                dataPresent = true
                                                gradleArgs << "-PdataFile=${env.DATA_FILE}"
                                                gradleArgs << "-PdataFileName=${env.DATA_FILE_FILENAME ?: 'DATA_FILE'}"
                                            }
                                        }
                                    } catch (ignored) {
                                        echo 'No DATA_FILE uploaded.'
                                    }

                                    if (!metadataPresent && !dataPresent) {
                                        error('Mindestens METADATA_FILE oder DATA_FILE muss hochgeladen werden.')
                                    }

                                    sh "./gradlew publishToDatenportal ${gradleArgs.join(' ')}"
                                }
                            }
                        }
                    }

                    post { always { echo 'GRETL Datenportal job finished.' } }
                }

                String gretlDatenportalEmailBody(String status) {
                    return \"\"\"Status: ${status}
                Organisation: ${params.ORGANISATION}
                Datensatz: ${params.DATASET}
                Build: ${env.BUILD_URL}
                Console: ${env.BUILD_URL}console
                \"\"\"
                }
                """,
                script);
    }

    @Test
    void rendersTemplateWithOrganizationSpecificValues() {
        NotificationConfiguration notifications = new NotificationConfiguration(
                true,
                NotificationConfiguration.Mode.APPEND,
                List.of("data@example.invalid"),
                true,
                false,
                true,
                false,
                false);
        OrganizationUnit organization = organization("publishCustom", 45, notifications);

        String script = new PipelineJobRenderer().renderTemplate(TEMPLATE, organization, notifications);

        assertTrue(script.contains("timeout(time: 45, unit: 'MINUTES')"));
        assertTrue(script.contains("./gradlew publishCustom"));
        assertTrue(script.contains("to: 'data@example.invalid'"));
        assertTrue(script.contains("gretlDatenportalEmailBody('SUCCESS')"));
        assertTrue(script.contains("gretlDatenportalEmailBody('FAILURE')"));
    }

    private OrganizationUnit organization(
            String gradleTask,
            int timeoutMinutes,
            NotificationConfiguration notificationConfiguration) {
        return new OrganizationUnit(
                "afu",
                Path.of("afu"),
                true,
                new JobDefinition("afu", "AFU", "", "gretl-datenportal-afu", gradleTask, "", timeoutMinutes),
                GuiDefinition.empty(),
                notificationConfiguration,
                List.of(new DatasetEntry(
                        "ch.so.dataset",
                        Path.of("afu/ch.so.dataset"),
                        new DatasetDefinition("ch.so.dataset", "Dataset", "", false),
                        true,
                        false,
                        GuiDefinition.empty())));
    }
}
