# CSV-Anlieferung für die Dateninfrastruktur

Im Compose-Stack führt `/anlieferung` nach `/jenkins/anlieferung/`.
Jenkins meldet Personen mit Keycloak und PKCE an. Die Benutzerkennung ist
der OIDC-Subject. Admin liefert die berechtigten Ausgaben und prüft die
Rechte erneut beim Erstellen, Starten und fachlichen Abschluss der Submission.

Das Plugin schreibt genau eine CSV privat unter
`$JENKINS_HOME/data-infrastructure/uploads`, ermittelt Grösse und SHA-256
und registriert die Submission mit dem Benutzer-JWT. Das Standardlimit
beträgt 100 MiB und das Hardlimit 256 MiB.

Der feste Job `di-csv-ingest` startet GRETL unter `di-csv-executor`.
Der Run ist über die Submission-ID zugeordnet. Der Status in der Anlieferung
stammt von Admin. Die Anlieferung zeigt die Taskzustände und Fehlercodes
aus dem GRETL-Runbericht. Jenkins bewahrt den originalen Validierungsbericht
bis 64 MiB unverändert und privat beim zugehörigen Build auf. Der
ursprüngliche Einreicher kann ihn nach der Admin-Statusprüfung über die
Statusseite herunterladen. Die Datei kann konkrete CSV-Werte enthalten.
Jenkins wertet die Meldungen nicht aus.

Der eingebaute Jenkins LogRotator bewahrt die Builds von `di-csv-ingest`
grundsätzlich 30 Tage auf. Nach Ablauf der Frist löscht Jenkins den Build
samt vollständigem Validatorlog bei der nächsten regulären Logrotation.
Laufende Builds und als «Keep forever» markierte Builds bleiben von der
Löschung ausgenommen.

Der Prozess hat ein Zeitlimit. Jenkins entfernt die privaten Laufzeitdateien
nach dem Run und protokolliert einen allfälligen Cleanup-Fehler mit der
Lieferkennung.

Build und JenkinsRule-Tests mit Java 21:

```sh
mvn -ntp clean package
```

Das HPI liegt unter `target/jenkins-gretl-dateninfrastruktur-plugin.hpi`.
JCasC und die isolierten Lieferprüfungen liegen im Dev-Stack.
