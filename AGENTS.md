# AGENTS.md – dateninfrastruktur-jenkins-gretl-plugin

## Vor Beginn lesen

Lies ausdrücklich `../AGENTS.md` und `../dateninfrastruktur-dev-stack/docs/implementation/grundinstallation/AUFTRAG_GRUNDINSTALLATION.md` sowie den zur aktuellen Rolle gehörenden Startprompt im selben Dokumentationsordner. Fehlt `../AGENTS.md`, lies ersatzweise `../dateninfrastruktur-dev-stack/docs/implementation/grundinstallation/templates/AGENTS.workspace.md`. Verlasse dich nicht auf automatische Vererbung über Git-Grenzen. Weitere betroffene Schwesterrepos haben eigene AGENTS-Dateien.

Diese Regeln gelten für Implementierung **und** Review. Ein Reviewauftrag erlaubt keine Änderungen an Produktdateien, Tests, Anforderungen oder AGENTS-Dateien; Befunde melden, nicht selbst reparieren. Builds/Tests nur mit eigenen Laufzeitdaten. Quellrepos, fremde Änderungen und laufende fremde Instanzen erhalten; keine Secrets in Git oder Berichten.

## Verantwortung

Eigenständige Kopie des einen projektspezifischen Jenkins-Plugins: Themenrepo-Scanner, Startformular, Seedjob und Jobgenerierung. Keine neuen Datenzugangs- oder BI-Funktionen einbauen.

## Besondere Regeln

- Repositoryname und Herkunft ändern; technische Plugin-ID `jenkins-gretl-datenportal-plugin` zunächst beibehalten.
- Ebenso `gretlDatenportalJobs`, `prepareDatenportalWorkspace`, Seedjobname und Java-/Jelly-Verträge gemäss Auftrag erhalten. Keine globale Namespace-Ersetzung.
- Bestehende Tests und Lizenzhinweise erhalten. Datenportal-Quellrepo unverändert lassen.
- Jenkins-Teamrechte für Jobausführung sind keine ClickHouse-Datenrechte. Keine entsprechende Rollenautomatik in dieses Plugin verlagern.
- In einer Jenkins-Instanz wird nur das neue lokale Artefakt installiert; alte und neue Kopie mit derselben technischen ID nicht parallel installieren.

## Nachweise

Den vorhandenen Maven-Test-/Paketweg mit der vorgesehenen Java-Version nutzen, beispielsweise `mvn -ntp package` im Repo-Root, sofern der übernommene Build dies bestätigt. HPI-Build und Hash dokumentieren; Installation, Formular, Assetpfade und Seedjob im neuen Dev-Stack prüfen. Kein Pluginrelease und kein Remote-Push verlangt.
