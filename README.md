# CSV-Anlieferung für die Dateninfrastruktur

Das Jenkins-Plugin stellt `/jenkins/data-delivery/` bereit. Im gemeinsamen
Compose-Stack führt `/anlieferung` dorthin. Jenkins meldet Personen über
Keycloak mit PKCE an; die feste Benutzerkennung ist der OIDC-Subject.
Datensatzauswahl, Lieferrecht, Status und Validierungsberichte kommen aus der
Admin-API.

Ein vorbereiteter Auftrag kann genau eine CSV aufnehmen. Das Plugin schreibt
die Datei begrenzt und privat unter `$JENKINS_HOME/data-infrastructure/uploads`,
versiegelt sie mit Grösse und SHA256 und prüft dieselben Bytes beim Materialisieren
im Run. Das Standardlimit beträgt 100 MiB; das Hardlimit beträgt 256 MiB.

Die einzige technische Pipeline heisst `di-csv-ingest`. Sie nutzt
`diClaimDelivery`, `diRunCsv`, `diCompleteDelivery` und `diCleanDelivery`.
Der Dispatcher prüft ausstehende Aufträge alle fünf Sekunden. Die Admin-API
besitzt den Datensatzslot und entscheidet über Annahme und Publikation. Der Job
läuft unter `di-csv-executor`; Lieferanten sehen ihre eigenen Berichte in der
Anlieferung. Bis zur Inbetriebnahme des CSV-Prozessors ist die Auftragserstellung
deaktiviert.

Build und JenkinsRule-Tests mit Java 21:

```sh
mvn -ntp clean package
```

Das HPI liegt unter `target/jenkins-gretl-dateninfrastruktur-plugin.hpi`.
Die JCasC-Konfiguration `diDelivery`, der isolierte Browsertest und die
Betriebsanleitung gehören zum Schwesterrepository `dateninfrastruktur-dev-stack`.
