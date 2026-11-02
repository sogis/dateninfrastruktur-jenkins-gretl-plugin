package ch.so.agi.jenkins.gretldatenportal;

public final class EmailNotificationService {
    public String renderPostBlock(NotificationConfiguration notificationConfiguration) {
        if (notificationConfiguration == null || !notificationConfiguration.isEmailEnabled()
                || notificationConfiguration.getEmailRecipients().isEmpty()) {
            return "";
        }

        String recipients = escapeGroovy(notificationConfiguration.getEmailRecipientsCsv());
        return """
            post {
                success {
                    script {
                        if (%s) {
                            emailext(
                                to: '%s',
                                subject: "[GRETL Datenportal] OK: ${params.ORGANISATION} / ${params.DATASET} (#${env.BUILD_NUMBER})",
                                body: gretlDatenportalEmailBody('SUCCESS')
                            )
                        }
                    }
                }
                unstable {
                    script {
                        if (%s) {
                            emailext(
                                to: '%s',
                                subject: "[GRETL Datenportal] WARNUNG: ${params.ORGANISATION} / ${params.DATASET} (#${env.BUILD_NUMBER})",
                                body: gretlDatenportalEmailBody('UNSTABLE')
                            )
                        }
                    }
                }
                failure {
                    script {
                        if (%s) {
                            emailext(
                                to: '%s',
                                subject: "[GRETL Datenportal] FEHLER: ${params.ORGANISATION} / ${params.DATASET} (#${env.BUILD_NUMBER})",
                                body: gretlDatenportalEmailBody('FAILURE')
                            )
                        }
                    }
                }
                aborted {
                    script {
                        if (%s) {
                            emailext(
                                to: '%s',
                                subject: "[GRETL Datenportal] ABGEBROCHEN: ${params.ORGANISATION} / ${params.DATASET} (#${env.BUILD_NUMBER})",
                                body: gretlDatenportalEmailBody('ABORTED')
                            )
                        }
                    }
                }
            }
            """.formatted(
                notificationConfiguration.isOnSuccess(),
                recipients,
                notificationConfiguration.isOnUnstable(),
                recipients,
                notificationConfiguration.isOnFailure(),
                recipients,
                notificationConfiguration.isOnAborted(),
                recipients);
    }

    private static String escapeGroovy(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
