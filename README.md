# dateninfrastruktur-jenkins-gretl-plugin

Jenkins-Plugin für GRETL-Datenportal-Katalog, Startformular, Seed-Job und
generierte Pipeline-Jobs in der lokalen Dateninfrastruktur-Grundinstallation.

Das Git-Repository heisst jetzt `dateninfrastruktur-jenkins-gretl-plugin`. Die
technische Jenkins-Plugin-ID bleibt `jenkins-gretl-datenportal-plugin`; deshalb
bleiben HPI-Dateiname und Jenkins-Asset-Pfade unverändert.

## Rolle im Gesamtsystem

Dieses Repo enthaelt die eigentliche Plugin-Implementierung.

Hierher gehoeren:

- Scanner und Validierungslogik fuer das Themenrepo
- RootAction unter `/gretl-datenportal`
- fixes Startformular und serverseitige Validierung
- Rechtepruefung ueber `permissions.read` und `permissions.build`
- Seed-Builder, verwalteter Themenrepo-Checkout und Job-Generierung
- Renderlogik fuer `shared/Jenkinsfile` und Placeholder-Ersatz

Nicht hierher gehoeren:

- der fachliche Vertrag des Themenrepos:
  [dateninfrastruktur-themenrepo](https://github.com/sogis/dateninfrastruktur-themenrepo)
- die lokale Jenkins-Betriebsumgebung:
  [dateninfrastruktur-jenkins-dev](https://github.com/sogis/dateninfrastruktur-jenkins-dev)

## Zentrale Workflows

### Plugin bauen

```bash
cd ../dateninfrastruktur-jenkins-gretl-plugin
export JAVA_HOME="${JAVA21_HOME:-$HOME/.sdkman/candidates/java/current}"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -ntp package
```

Das lokale HPI landet unter
`target/jenkins-gretl-datenportal-plugin.hpi`.

### Plugin in die lokale Jenkins-Umgebung installieren

```bash
cd ../dateninfrastruktur-jenkins-dev
./bin/install-gretl-datenportal-plugin.sh
```

Wenn Jenkins bereits laeuft, ist anschliessend ein voller Restart noetig.

### Lokalen End-to-End-Loop fahren

```bash
cd ../dateninfrastruktur-jenkins-gretl-plugin
export JAVA_HOME="${JAVA21_HOME:-$HOME/.sdkman/candidates/java/current}"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -ntp package

cd ../dateninfrastruktur-jenkins-dev
./bin/install-gretl-datenportal-plugin.sh
./bin/start.sh
```

Bei einem frischen Jenkins-Home wird der automatisch angelegte Job
`gretl-datenportal-seed` bei konfiguriertem Themenrepo einmalig automatisch
gestartet. Bei einem bestehenden Home oder nach einem fehlgeschlagenen Erstlauf
kann der Job weiterhin manuell gestartet werden; alternativ wartet man auf den
Cron-Lauf. Danach das Datenportal unter `/gretl-datenportal` pruefen.

Für lokale Änderungen am Themenrepo kann die Dev-Umgebung den Modus
`THEMEN_REPO_MODE=working-tree` verwenden. Der Seed-Lauf kopiert dabei den
externen Arbeitsbaum inklusive uncommitteter und untracked Dateien in einen
Jenkins-internen Snapshot. Der Snapshot wird nur beim Seed-Lauf aktualisiert;
Jobs sind weiterhin keine Live-Sicht auf das externe Arbeitsverzeichnis. Der
Repository-Modus `managed-git` verarbeitet dagegen den committed Stand des
konfigurierten Branches. Er ist auch im lokalen Dev-Betrieb verwendbar und
aktiviert allein kein Git-Rückschreiben. Laufzeit-ENV und Defaults stehen in der
[zentralen Jenkins-Konfigurationsreferenz](https://github.com/sogis/dateninfrastruktur-jenkins-dev/src/branch/main/docs/biblios/entwicklung/referenz-konfiguration.adoc).

## Seed-Job

Neben dem Seed-Builder provisioniert das Plugin auch den Seed-Job
`gretl-datenportal-seed` selbst. Der Job:

- wird automatisch erstellt und aktualisiert, wenn `seedJobAutoCreate=true`;
- bleibt deaktiviert, solange kein Themenrepo konfiguriert ist;
- wird per `seedJobCron` (Default `H/15 * * * *`) getriggert;
- kann jederzeit manuell gestartet werden;
- verwendet die globale Themenrepo-Konfiguration, nicht duplizierte Repo-Werte
  im Job.

Ein leerer `seedJobCron` deaktiviert nur den Timer, nicht den Job. Bestehende,
nicht vom Plugin verwaltete Jobs mit demselben Namen werden nicht ueberschrieben.

## Teams und Jenkins-Autorisierung

Die Benutzer werden im Themenrepo Teams zugeordnet. Die Datei
`shared/gretl-datenportal-teams.yaml` ist die einzige Quelle fuer diese
Zuordnung:

```yaml
teams:
  gretl-datenportal-seed-operators:
    users:
      - sziegler
      - mmuster

  datenportal-read:
    users:
      - bbeispiel

  statistikdienst-build:
    users:
      - sziegler
```

Eine Organisationsdatei referenziert nur noch Team-IDs:

```yaml
permissions:
  read:
    - team: datenportal-read
  build:
    - team: statistikdienst-build
```

Unter `Manage Jenkins -> System -> GRETL Datenportal Jobs` wird bei
`Seed-Job Operators Team` die Team-ID des Seeder-Teams eingetragen. Das Feld
ist keine Organisationsberechtigung. Das Seeder-Team darf den verwalteten Job
sehen und starten, aber nicht konfigurieren, loeschen, abbrechen oder seinen
Builder ersetzen. Der erste Seed-Lauf muss deshalb durch einen Jenkins-
Administrator erfolgen; danach synchronisiert ein erfolgreicher Seed-Lauf auch
die Seeder-ACL.

Fuer die Jenkins-Sicherheit muss das Plugin `matrix-auth` installiert sein und
Jenkins muss die **Project-based Matrix Authorization Strategy** verwenden.
LDAP oder Entra ID authentisiert die Benutzer; die Teamdatei liefert die
projektbezogenen Berechtigungen. Global sollten normale Benutzer nur
`Overall/Read` erhalten. Pauschale globale `Item/Read`- oder `Item/Build`-
Rechte fuer `authenticated` duerfen nicht gesetzt werden, weil globale Rechte
additiv zu den Projekt-ACLs wirken. Jenkins-Administratoren werden separat mit
`Overall/Administer` abgesichert. Die Themenrepo-YAML vergibt niemals
globale Administrationsrechte und insbesondere kein `Item/Configure`.

Pluginverwaltete Organisationsjobs erhalten eine nicht-erbende Matrix-ACL. Bei
einem erfolgreichen Seed-Lauf werden Team-Aenderungen und entfernte
Organisationen synchronisiert. Eine ungueltige Teams- oder
Berechtigungskonfiguration bricht den Lauf ab; die letzte gueltige Seeder-ACL
bleibt dabei erhalten.

Jobdefinitionen, Berechtigungen und die Themenauswahl werden beim Seed
materialisiert. Für den Build kopiert `working-tree` diesen Seeder-Snapshot;
`managed-git` erstellt dagegen einen frischen Checkout des konfigurierten
Remote-Branches. Änderungen am Repository-Kontext der Jobs benötigen einen
neuen Seed.

## Maven-Artefakt veröffentlichen

Jeder Push auf `main` veröffentlicht die bestehende Snapshot-Version
`0.1.0-SNAPSHOT` über GitHub Actions nach
`https://jars.interlis.guru/snapshots/`. Eine Tag-Strategie gibt es derzeit
nicht. Pull Requests bauen und testen nur.

Benötigte Repository-Secrets:

- `INTERLIS_MAVEN_USERNAME`
- `INTERLIS_MAVEN_TOKEN`

Der Workflow
`.github/workflows/maven-publish.yml` baut mit Java 21 und führt
`mvn clean deploy` gegen `interlis-guru` aus. Das HPI heisst weiterhin
`target/jenkins-gretl-datenportal-plugin.hpi`; die technische Plugin-ID
`jenkins-gretl-datenportal-plugin` bleibt unverändert.

Lokale Prüfung des veröffentlichten Artefakts:

```bash
mvn -ntp dependency:get \
  -Dartifact=ch.so.agi.jenkins:jenkins-gretl-datenportal-plugin:0.1.0-SNAPSHOT:hpi \
  -DremoteRepositories=interlis-guru::default::https://jars.interlis.guru/snapshots/ -U
```

Danach den SHA256 des heruntergeladenen HPI mit dem lokal gebauten HPI
vergleichen. Ein Release-Tag ist für diese Etappe nicht vorgesehen.

## Langform-Doku

Die technische Langform-Doku liegt unter
[docs/biblios/entwicklung/index.adoc](docs/biblios/entwicklung/index.adoc).

## Schwester-Repositories

- [dateninfrastruktur-themenrepo](https://github.com/sogis/dateninfrastruktur-themenrepo)
  ist die kanonische Doku fuer Organisationsstruktur, Datensaetze,
  `gretl-datenportal-job.yaml`, `shared/Jenkinsfile` und Shared Defaults.
- [dateninfrastruktur-jenkins-dev](https://github.com/sogis/dateninfrastruktur-jenkins-dev)
  ist die kanonische Doku fuer lokalen Jenkins, Offline-Bundle, JCasC und
  Docker-/Airgap-Tests.

Die Standardpipeline kann über `prepareDatenportalWorkspace` je Build einen
isolierten Checkout oder eine Kopie des Seeder-Snapshots beziehen. Git-Rückschreiben
ist standardmässig deaktiviert; HTTPS-Credential-ID und Commit-Identität werden
administrativ konfiguriert. Details stehen unter
[Themenrepo-Checkout und Konfiguration](docs/biblios/entwicklung/checkout-und-konfiguration.adoc).

Die Startvariante `PUBLICATION_MODE=repository-metadata` gleicht ein einzelnes
Repository-Datenblatt ohne Upload ab. `delivery` bleibt der Default und verlangt
mindestens eine Datei. `RELOAD_PORTAL` fordert optional einen Reload nach der
S3-Übernahme an. Die Freigabe zum Publizieren wird administrativ konfiguriert;
`working-tree` verbietet ausschliesslich Git-Schreiben, nicht eine ausdrücklich
aktivierte lokale Garage-Publikation.
