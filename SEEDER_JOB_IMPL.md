# Implementation Brief: Plugin-verwalteter Seeder-Job mit Cron

Diese Datei ist eine Umsetzungsanweisung fuer einen LLM-Coding-Agenten. Ziel ist
eine konkrete Implementierung, keine weitere Architekturfindung.

## Zielbild

Das Jenkins-Plugin `jenkins-gretl-datenportal-plugin` soll den Seed-Job selbst
bereitstellen. In einer frischen Jenkins-Installation soll nach der
Plugin-Konfiguration automatisch ein Job `gretl-datenportal-seed` existieren,
der das Themenrepo periodisch scannt und daraus die Datenportal-Workflow-Jobs
materialisiert.

Entscheidungen fuer die Implementierung:

- Der Seed-Job wird im Plugin erzeugt und verwaltet, nicht mehr per JCasC/Job-DSL
  im Repo `datenportal-jenkins-dev`.
- Der Seed-Job ist ein `FreeStyleProject` mit genau einem
  `GretlDatenportalSeedBuilder`.
- Der Builder im automatisch erstellten Job wird mit leeren Repo-Feldern
  konfiguriert, damit er immer die globale Plugin-Konfiguration verwendet.
- Der feste Jobname ist `gretl-datenportal-seed`.
- Der Standard-Cron ist `H/15 * * * *`.
- Der Seed-Job wird nur dann aktiviert und mit Timer-Trigger versehen, wenn ein
  Themenrepo per URL oder Pfad konfiguriert ist.
- Die generierten Datenportal-Jobs sollen weiterhin nicht selbst das Themenrepo
  klonen. Repo-Aenderungen werden ueber den Seed-Lauf materialisiert.
- Bestehende, nicht vom Plugin verwaltete Jenkins-Jobs duerfen nicht
  ueberschrieben oder geloescht werden.

## Aktueller Stand

Im Plugin-Repo existiert bereits:

- `GretlDatenportalSeedBuilder`: Build-Step, der das Themenrepo aufloest,
  scannt, validiert und `GretlDatenportalJobGenerator` aufruft.
- `GretlDatenportalJobGenerator`: erzeugt oder aktualisiert
  `WorkflowJob`s pro Organisation.
- `GretlDatenportalGlobalConfiguration`: globale Konfiguration fuer
  Display-Name, URL-Name, Themenrepo-URL, Themenrepo-Branch und Themenrepo-Pfad.
- Doku unter `docs/biblios/entwicklung/seed-und-job-generierung.adoc`, die
  den Seed-Builder beschreibt.

Im Repo `../datenportal-jenkins-dev` wird der lokale Seed-Job aktuell per
JCasC/Job-DSL erzeugt:

- `casc/jenkins.yaml` enthaelt unter `jobs:` einen Job
  `gretl-datenportal-plugin-generator-local`.
- Dieser Job benutzt direkt
  `ch.so.agi.jenkins.gretldatenportal.GretlDatenportalSeedBuilder`.
- `plugins.txt` enthaelt `job-dsl` hauptsaechlich fuer diesen JCasC-Job.

Im Repo `../datenportal-themenrepo` ist der fachliche Vertrag dokumentiert:

- `docs/biblios/entwicklung/seed-und-bootstrap.adoc` spricht aktuell vom per
  JCasC erzeugten lokalen Seed-Job.
- Das Themenrepo selbst braucht fuer dieses Vorhaben keine fachliche
  Struktur-Aenderung.

## Plugin-Repo: Implementierung

### 1. Globale Konfiguration erweitern

Datei:

- `src/main/java/ch/so/agi/jenkins/gretldatenportal/GretlDatenportalGlobalConfiguration.java`

Neue Felder:

```java
private boolean seedJobAutoCreate = true;
private String seedJobCron = GretlDatenportalSeedJobProvisioner.DEFAULT_SEED_JOB_CRON;
private int seedJobBuildsToKeep = 20;
```

Neue Getter:

```java
public boolean isSeedJobAutoCreate()
public String getSeedJobCron()
public int getSeedJobBuildsToKeep()
public boolean isTopicRepositoryConfigured()
```

Getter-Verhalten:

- `isSeedJobAutoCreate()` gibt den gespeicherten Wert zurueck; Default ist
  `true`.
- `getSeedJobCron()` gibt den Default `H/15 * * * *` zurueck, wenn der
  gespeicherte Wert `null` ist. Ein bewusst leerer String bleibt leer und
  deaktiviert nur den Timer-Trigger.
- `getSeedJobBuildsToKeep()` gibt `20` zurueck, wenn der gespeicherte Wert
  kleiner oder gleich `0` ist.
- `isTopicRepositoryConfigured()` ist `true`, wenn `getTopicRepositoryUrl()`
  oder `getTopicRepositoryPath()` nicht blank ist.

Neue Setter mit `@DataBoundSetter`:

```java
public void setSeedJobAutoCreate(boolean seedJobAutoCreate)
public void setSeedJobCron(String seedJobCron)
public void setSeedJobBuildsToKeep(int seedJobBuildsToKeep)
```

Setter-Verhalten:

- Werte normalisieren, dann speichern.
- Nach dem Speichern versuchen, den Seed-Job neu zu provisionieren.
- Fehler beim Provisionieren nicht in der UI verschlucken, wenn der Setter aus
  einer normalen Jenkins-Konfigurationsspeicherung kommt. Bei JCasC/Startup
  aber robust loggen, damit Jenkins nicht wegen eines bereits existierenden
  Fremd-Jobs unbenutzbar startet.

Empfohlene Hilfsmethode:

```java
private void saveAndProvisionSeedJob()
```

Sie soll:

1. `save()` aufrufen.
2. Wenn `Jenkins.getInstanceOrNull()` nicht `null` ist,
   `GretlDatenportalSeedJobProvisioner.provisionFromConfiguration(this)`
   aufrufen.
3. `IOException` als `FormException` oder `RuntimeException` nur dort
   eskalieren, wo Jenkins-Konfigurationsspeicherung das sauber anzeigen kann.
   Fuer reine Setter-Aufrufe ist Logging akzeptabel.

Falls die Jenkins-API-Signaturen eine saubere `configure(...)`-Ueberschreibung
ermoeglichen, ist diese Variante vorzuziehen:

```java
@Override
public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException
```

Dann sollen die einzelnen Setter nur normalisieren, waehrend `configure(...)`
nach `super.configure(req, json)` genau einmal provisioniert. Wichtig ist, dass
JCasC weiterhin funktioniert.

Neue Validierungsmethoden:

```java
public FormValidation doCheckSeedJobCron(@QueryParameter String value)
public FormValidation doCheckSeedJobBuildsToKeep(@QueryParameter String value)
```

Validierung:

- Blank Cron ist erlaubt und bedeutet: Seed-Job bleibt manuell startbar, aber
  ohne Timer.
- Nicht-blank Cron mit Jenkins `TimerTrigger.DescriptorImpl#doCheckSpec(...)`
  validieren.
- `seedJobBuildsToKeep` muss eine positive Ganzzahl sein.

### 2. Globales Konfigurationsformular erweitern

Datei:

- `src/main/resources/ch/so/agi/jenkins/gretldatenportal/GretlDatenportalGlobalConfiguration/config.jelly`

Neue Felder im bestehenden Abschnitt `GRETL Datenportal Jobs`:

```xml
<f:entry title="Seed-Job automatisch erstellen" field="seedJobAutoCreate">
    <f:checkbox />
</f:entry>
<f:entry title="Seed-Job Cron" field="seedJobCron">
    <f:textbox />
</f:entry>
<f:entry title="Seed-Job Builds behalten" field="seedJobBuildsToKeep">
    <f:number />
</f:entry>
```

Hilfetexte sind optional, aber empfohlen. Wenn Hilfetexte ergaenzt werden,
dann als Jelly `help-*.html` in demselben Ressourcenordner:

- `help-seedJobAutoCreate.html`
- `help-seedJobCron.html`
- `help-seedJobBuildsToKeep.html`

Inhalt kurz halten:

- Auto-Create: erstellt/aktualisiert `gretl-datenportal-seed`.
- Cron: Jenkins-Cron, leer fuer keinen Timer.
- Builds behalten: Log-Retention fuer den Seed-Job.

### 3. Marker-Property fuer pluginverwaltete Jobs einfuehren

Neue Datei:

- `src/main/java/ch/so/agi/jenkins/gretldatenportal/GretlDatenportalManagedSeedJobProperty.java`

Zweck:

- Persistenter Marker, damit das Plugin erkennt, ob ein vorhandener
  `FreeStyleProject` vom Plugin verwaltet wird.
- Verhindert, dass ein zufaellig gleich benannter Benutzerjob ueberschrieben
  wird.

Klassenstruktur:

```java
public final class GretlDatenportalManagedSeedJobProperty
        extends JobProperty<FreeStyleProject> {

    @DataBoundConstructor
    public GretlDatenportalManagedSeedJobProperty() {
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) { ... }

        @Override
        public String getDisplayName() { ... }
    }
}
```

Details:

- `isApplicable(...)` nur fuer `FreeStyleProject`.
- `getDisplayName()` darf z.B.
  `GRETL Datenportal managed seed job` zurueckgeben.
- Keine UI-Konfigurationsfelder notwendig.

### 4. Provisioner-Klasse einfuehren

Neue Datei:

- `src/main/java/ch/so/agi/jenkins/gretldatenportal/GretlDatenportalSeedJobProvisioner.java`

Imports voraussichtlich:

```java
import hudson.BulkChange;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.tasks.LogRotator;
import hudson.triggers.TimerTrigger;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
```

Konstanten:

```java
static final String DEFAULT_SEED_JOB_NAME = "gretl-datenportal-seed";
static final String DEFAULT_SEED_JOB_CRON = "H/15 * * * *";
private static final Logger LOGGER = Logger.getLogger(GretlDatenportalSeedJobProvisioner.class.getName());
```

Oeffentliche/static API:

```java
@Initializer(after = InitMilestone.JOB_LOADED)
public static void provisionAfterJobsLoaded()

public static void provisionFromConfiguration(GretlDatenportalGlobalConfiguration configuration)
```

Instanzmethoden:

```java
void ensureSeedJob(Jenkins jenkins, GretlDatenportalGlobalConfiguration configuration) throws IOException
private FreeStyleProject getOrCreateManagedSeedJob(Jenkins jenkins) throws IOException
private boolean isManagedSeedJob(FreeStyleProject project)
private void markManaged(FreeStyleProject project) throws IOException
private void configureProject(FreeStyleProject project, GretlDatenportalGlobalConfiguration configuration) throws IOException
private void configureDescription(FreeStyleProject project)
private void configureBuilders(FreeStyleProject project)
private void configureBuildDiscarder(FreeStyleProject project, int buildsToKeep) throws IOException
private void configureDisabledState(FreeStyleProject project, boolean active) throws IOException
private void configureTimerTrigger(FreeStyleProject project, String cron, boolean active) throws IOException
private boolean hasConfiguredTopicRepository(GretlDatenportalGlobalConfiguration configuration)
```

Verhalten `provisionAfterJobsLoaded()`:

1. `Jenkins.getInstanceOrNull()` holen.
2. Wenn `null`, nur loggen und zurueck.
3. `GretlDatenportalGlobalConfiguration.get()` holen.
4. Wenn Konfiguration `null`, zurueck.
5. `new GretlDatenportalSeedJobProvisioner().ensureSeedJob(jenkins, config)`.
6. Fehler mit `LOGGER.log(Level.WARNING, ...)` protokollieren, Jenkins-Start
   aber nicht abbrechen.

Verhalten `ensureSeedJob(...)`:

1. Wenn `configuration.isSeedJobAutoCreate()` `false` ist:
   - Existiert ein pluginverwalteter Seed-Job, Timer entfernen und Job
     deaktivieren.
   - Keinen neuen Job erzeugen.
   - Zurueck.
2. `getOrCreateManagedSeedJob(jenkins)` aufrufen.
3. Projekt in einem `BulkChange` konfigurieren.
4. `configureProject(...)`.
5. `project.save()` und `bulkChange.commit()`.

Verhalten `getOrCreateManagedSeedJob(...)`:

- `jenkins.getItem(DEFAULT_SEED_JOB_NAME)` pruefen.
- Wenn kein Item existiert:
  - `jenkins.createProject(FreeStyleProject.class, DEFAULT_SEED_JOB_NAME)`.
  - Marker-Property hinzufuegen.
  - Projekt zurueckgeben.
- Wenn Item ein `FreeStyleProject` ist und Marker-Property hat:
  - Projekt zurueckgeben.
- Wenn Item ein `FreeStyleProject` ist, aber keinen Marker hat:
  - Nicht ueberschreiben.
  - `IOException` mit klarer Meldung werfen:
    `Cannot manage GRETL Datenportal seed job 'gretl-datenportal-seed': item already exists and is not plugin-managed.`
- Wenn Item ein anderer Typ ist:
  - Nicht ueberschreiben.
  - `IOException` mit klarer Meldung werfen:
    `Cannot create GRETL Datenportal seed job 'gretl-datenportal-seed': item already exists with another type.`

Verhalten `configureProject(...)`:

- Beschreibung setzen, z.B.
  `Plugin-managed seed job for GRETL Datenportal generated jobs. Do not edit manually; configure it under Manage Jenkins -> System -> GRETL Datenportal Jobs.`
- Marker-Property sicherstellen.
- `configureBuilders(...)`.
- `configureBuildDiscarder(...)`.
- `active = configuration.isSeedJobAutoCreate() && hasConfiguredTopicRepository(configuration)`.
- `configureDisabledState(project, active)`.
- `configureTimerTrigger(project, configuration.getSeedJobCron(), active)`.

Verhalten `configureBuilders(...)`:

- Alle vorhandenen `GretlDatenportalSeedBuilder` aus der Builder-Liste
  entfernen, damit keine Duplikate entstehen.
- Genau einen Builder hinzufuegen:

```java
new GretlDatenportalSeedBuilder("", "", "")
```

Wichtig: Nicht die globale Repo-URL in den Job kopieren. Die globale
Konfiguration bleibt Source of Truth.

Verhalten `configureTimerTrigger(...)`:

- Bestehenden `TimerTrigger` auf dem Seed-Job entfernen.
- Wenn `active == false`, keinen neuen Trigger setzen.
- Wenn `cron` blank ist, keinen neuen Trigger setzen; Job bleibt manuell
  startbar, sofern nicht deaktiviert.
- Wenn `cron` nicht blank ist:
  - `TimerTrigger trigger = new TimerTrigger(cron.trim())`.
  - Trigger am Projekt registrieren.
  - Falls die Jenkins-API den Trigger nicht automatisch startet, explizit
    `trigger.start(project, true)` ausfuehren.

Verhalten `configureDisabledState(...)`:

- Wenn `active == true`, Projekt aktivieren.
- Wenn kein Themenrepo konfiguriert ist oder Auto-Create deaktiviert wurde,
  Projekt deaktivieren.
- Deaktivierter Zustand verhindert Cron-Fehler in frischen Installationen ohne
  Repo-Konfiguration.

Verhalten `configureBuildDiscarder(...)`:

- `project.setBuildDiscarder(new LogRotator(-1, buildsToKeep, -1, -1))`.

### 5. Permissions und Sicherheit

- Der Provisioner laeuft beim Jenkins-Start als System-Code und soll keine
  Benutzerpermission pruefen.
- `GretlDatenportalSeedBuilder.perform(...)` bleibt unveraendert und prueft
  ueber `GretlDatenportalJobGenerator.generate(...)` weiterhin
  `Item.CONFIGURE`.
- Keine Credentials-Logik einfuehren. Das bestehende Themenrepo-Handling
  unterstuetzt aktuell URL/Pfad ohne Jenkins-Credentials. Das Vorhaben soll
  daran nichts aendern.

### 6. Migration bestehender lokaler Jobs

- Der neue pluginverwaltete Job heisst `gretl-datenportal-seed`.
- Der alte lokale JCasC-Job
  `gretl-datenportal-plugin-generator-local` wird nicht automatisch geloescht.
- In `datenportal-jenkins-dev` muss JCasC so angepasst werden, dass der alte
  Job nicht mehr neu erzeugt wird.
- In der Doku klar sagen:
  - Bei vorhandenen lokalen Jenkins-Homes kann der alte Job manuell geloescht
    werden.
  - Fuer frische lokale Umgebungen existiert nur noch
    `gretl-datenportal-seed`.

## Plugin-Repo: Tests

Neue Testdatei:

- `src/test/java/ch/so/agi/jenkins/gretldatenportal/GretlDatenportalSeedJobProvisionerTest.java`

Teststil:

- JUnit 5 mit `@WithJenkins`.
- `JenkinsRule` verwenden.
- Temp-Git-Repos mit vorhandenem `GitTestSupport` aufbauen.
- Keine echten externen Netzwerke.

Testfaelle:

1. `createsManagedSeedJobWhenRepositoryIsConfigured`
   - GlobalConfig mit `topicRepositoryUrl=file://...` und Branch `main`
     konfigurieren.
   - `ensureSeedJob(...)` aufrufen.
   - Jenkins enthaelt `FreeStyleProject` `gretl-datenportal-seed`.
   - Job hat `GretlDatenportalManagedSeedJobProperty`.
   - Job ist nicht disabled.
   - Job hat genau einen `GretlDatenportalSeedBuilder`.
   - Builder-Felder `topicRepositoryUrl`, `topicRepositoryBranch`,
     `topicRepositoryPath` sind leer.

2. `configuresTimerTriggerWithDefaultCron`
   - Seed-Job provisionieren.
   - `TimerTrigger` vorhanden.
   - Cron-Spec ist `H/15 * * * *`.

3. `blankCronLeavesJobManualOnly`
   - `seedJobCron=""`.
   - Seed-Job provisionieren.
   - Kein `TimerTrigger`.
   - Job bleibt aktiviert, wenn Themenrepo konfiguriert ist.

4. `disablesSeedJobWhenRepositoryIsMissing`
   - Keine Themenrepo-URL und kein Pfad.
   - Seed-Job provisionieren.
   - Job existiert, ist disabled, hat keinen Timer.
   - Manuelles Aktivieren durch Nutzer ist nicht Ziel dieses Tests.

5. `autoCreateFalseDoesNotCreateJob`
   - `seedJobAutoCreate=false`.
   - Kein bestehender Job.
   - Provisioner ausfuehren.
   - `jenkins.getItem("gretl-datenportal-seed") == null`.

6. `autoCreateFalseDisablesExistingManagedJob`
   - Erst mit Auto-Create true erzeugen.
   - Dann Auto-Create false setzen und provisionieren.
   - Job bleibt vorhanden, ist disabled, Timer entfernt.

7. `isIdempotentAndDoesNotDuplicateBuilderOrTrigger`
   - Provisioner zweimal ausfuehren.
   - Genau ein `GretlDatenportalSeedBuilder`.
   - Genau ein `TimerTrigger`.
   - Marker-Property genau einmal.

8. `doesNotOverwriteExistingFreestyleJobWithoutMarker`
   - Vorher manuell `FreeStyleProject` mit Name `gretl-datenportal-seed`
     erstellen, ohne Marker.
   - Provisioner wirft `IOException`.
   - Bestehende Builder/Description bleiben unveraendert.

9. `doesNotOverwriteExistingItemWithDifferentType`
   - Vorher z.B. `WorkflowJob` mit Name `gretl-datenportal-seed` erstellen.
   - Provisioner wirft `IOException`.

10. `managedSeedJobCanGenerateOrganizationJobs`
    - Temp-Git-Repo mit `GitTestSupport.initRepository`,
      `writeSharedJenkinsfile`, `addOrganization`.
    - GlobalConfig auf dieses Repo setzen.
    - Provisioner ausfuehren.
    - `jenkinsRule.buildAndAssertSuccess(seedJob)`.
    - Danach existiert `WorkflowJob` `gretl-datenportal-afu`.

11. `updatesManagedSeedJobWhenCronChanges`
    - Erst mit Default-Cron provisionieren.
    - Dann `seedJobCron="H H * * *"` setzen.
    - Provisioner erneut ausfuehren.
    - Nur ein Timer, Spec ist `H H * * *`.

Erweiterung bestehender Tests:

- `GretlDatenportalGlobalConfiguration` braucht Tests fuer neue Getter,
  Normalisierung und FormValidation.
- Falls kein eigener Test fuer GlobalConfiguration existiert, neue Datei
  `GretlDatenportalGlobalConfigurationTest.java` anlegen.

Maven-Testkommando:

```bash
mvn -ntp test
```

Falls einzelne JenkinsRule-Tests langsam sind, zunaechst gezielt:

```bash
mvn -ntp -Dtest=GretlDatenportalSeedJobProvisionerTest test
```

## Repo `datenportal-jenkins-dev`: notwendige Aenderungen

### 1. JCasC anpassen

Datei:

- `../datenportal-jenkins-dev/casc/jenkins.yaml`

Unter `unclassified.gretlDatenportalJobs` neue Felder setzen:

```yaml
    seedJobAutoCreate: true
    seedJobCron: "H/15 * * * *"
    seedJobBuildsToKeep: 20
```

Den ganzen bisherigen `jobs:`-Block entfernen, der
`gretl-datenportal-plugin-generator-local` erzeugt.

Zielzustand:

- Kein Job-DSL-basierter Seed-Job mehr in JCasC.
- Das Plugin erzeugt `gretl-datenportal-seed` nach dem Jenkins-Start selbst.

### 2. `plugins.txt` pruefen

Datei:

- `../datenportal-jenkins-dev/plugins.txt`

Wenn nach Entfernen des JCasC-`jobs:`-Blocks keine andere lokale Konfiguration
mehr `job-dsl` braucht, `job-dsl` aus `plugins.txt` entfernen.

Begruendung:

- Der neue Seed-Job wird durch Java-Code im Plugin erzeugt.
- Das Plugin selbst hat keine Runtime-Abhaengigkeit zu `job-dsl`.

`configuration-as-code` bleibt notwendig, weil die globale Plugin-Konfiguration
weiterhin ueber JCasC gesetzt wird.

### 3. Persistierte lokale GlobalConfig aktualisieren

Datei:

- `../datenportal-jenkins-dev/jenkins-home/ch.so.agi.jenkins.gretldatenportal.GretlDatenportalGlobalConfiguration.xml`

Wenn diese Datei versioniert bleiben soll, neue XML-Elemente ergaenzen:

```xml
  <seedJobAutoCreate>true</seedJobAutoCreate>
  <seedJobCron>H/15 * * * *</seedJobCron>
  <seedJobBuildsToKeep>20</seedJobBuildsToKeep>
```

Wenn `jenkins-home` nur lokaler Zustand ist, in der Doku stattdessen erklaeren,
dass ein bestehendes lokales Jenkins-Home beim naechsten Start durch JCasC und
Plugin-Provisioning aktualisiert wird.

### 4. README und Dev-Doku aktualisieren

Datei:

- `../datenportal-jenkins-dev/README.md`

Aenderungen:

- Im lokalen End-to-End-Workflow nicht mehr
  `gretl-datenportal-plugin-generator-local` nennen.
- Neuer Text:
  - Nach Jenkins-Start legt das Plugin den Job `gretl-datenportal-seed` an und
    startet bei einem frischen Home mit konfiguriertem Themenrepo einmalig den
    initialen Seed-Lauf.
  - Der Job ist aktiviert, sobald `THEMEN_REPO_URL` oder ein Pfad konfiguriert
    ist.
  - Der Job laeuft per Cron `H/15 * * * *`.
  - Fuer sofortige lokale Tests kann man den Job manuell starten.
- Hinweis fuer bestehende lokale Jenkins-Homes:
  - Alter Job `gretl-datenportal-plugin-generator-local` kann geloescht werden.
  - Relevant ist neu `gretl-datenportal-seed`.

Falls im Repo doch eine Langform-Doku unter `docs/` existiert oder spaeter
hinzukommt, dieselben Aussagen dort einpflegen.

### 5. Lokaler Smoke-Test

Nach Plugin-Implementierung:

```bash
cd ../datenportal-jenkins-gretl-plugin
export JAVA_HOME="${JAVA21_HOME:-$HOME/.sdkman/candidates/java/current}"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -ntp package

cd ../datenportal-jenkins-dev
./bin/install-gretl-datenportal-plugin.sh
./bin/start.sh
```

Manuell in Jenkins pruefen:

- `gretl-datenportal-seed` existiert.
- `gretl-datenportal-plugin-generator-local` wird in einer frischen Umgebung
  nicht mehr angelegt.
- `gretl-datenportal-seed` hat einen Timer-Trigger.
- Manueller Lauf von `gretl-datenportal-seed` ist erfolgreich.
- Danach existieren die erwarteten Organisation-Jobs, z.B.
  `gretl-datenportal-afu`.
- `/gretl-datenportal` zeigt die Jobs korrekt an.

## Repo `datenportal-themenrepo`: notwendige Aenderungen

### 1. Seed-Doku aktualisieren

Datei:

- `../datenportal-themenrepo/docs/biblios/seed-und-bootstrap.adoc`

Aenderungen:

- Abschnitt `Lokaler Bootstrap` aktualisieren.
- Nicht mehr schreiben, dass der Seed-Job per JCasC erzeugt wird.
- Neu schreiben:
  - Das Plugin erzeugt `gretl-datenportal-seed` automatisch.
  - Der Job wird per globaler Plugin-Konfiguration gesteuert.
  - In der lokalen Dev-Umgebung kommt die Repo-URL aus
    `datenportal-jenkins-dev/casc/jenkins.yaml` bzw. den Env-Variablen
    `THEMEN_REPO_URL` und `THEMEN_REPO_BRANCH`.
  - Der Seed-Job laeuft periodisch per Cron und kann fuer Tests manuell
    gestartet werden.

### 2. README aktualisieren

Datei:

- `../datenportal-themenrepo/README.md`

Kleine Anpassung im Workflow-/Schwesterrepo-Abschnitt:

- Der lokale Jenkins erzeugt den Seed-Job nicht mehr ueber JCasC, sondern das
  Plugin provisioniert `gretl-datenportal-seed`.
- Themenrepo-Aenderungen werden nach dem naechsten Seed-Lauf in Jenkins-Jobs
  materialisiert.

### 3. Keine fachlichen Struktur-Aenderungen

Nicht aendern:

- `shared/Jenkinsfile`
- `shared/gretl-datenportal-defaults.yaml`
- Organisations-`gretl-datenportal-job.yaml`
- Dataset-Struktur

Diese Dateien sind fuer den Seeder-Job selbst nicht anzupassen.

## Plugin-Doku aktualisieren

Dateien:

- `README.md`
- `docs/biblios/entwicklung/seed-und-job-generierung.adoc`
- optional `docs/biblios/entwicklung/lokale-entwicklung.adoc`

Aenderungen:

- Beschreiben, dass das Plugin neben dem Seed-Builder auch den Seed-Job
  `gretl-datenportal-seed` provisioniert.
- Erklaeren, dass der Job:
  - automatisch erstellt wird, wenn `seedJobAutoCreate=true`;
  - disabled bleibt, solange kein Themenrepo konfiguriert ist;
  - per `seedJobCron` getriggert wird;
  - manuell gestartet werden kann;
  - die globale Themenrepo-Konfiguration verwendet.
- Den alten Satz `Danach in Jenkins den Seed-Job ausfuehren` praezisieren: Bei
  einem frischen Home den automatischen initialen Seed-Lauf abwarten; bei einem
  bestehenden Home oder nach einem fehlgeschlagenen Erstlauf den automatisch
  angelegten Job `gretl-datenportal-seed` manuell starten oder auf den Cron-Lauf
  warten.
- Hinweis aufnehmen, dass generierte Datenportal-Jobs weiterhin keine Live-Sicht
  auf das Repo sind.

## Akzeptanzkriterien

Die Implementierung ist fertig, wenn alle Punkte erfuellt sind:

- Frisches Jenkins mit installiertem Plugin und gesetzter globaler
  Themenrepo-Konfiguration erzeugt automatisch `gretl-datenportal-seed`.
- Der Seed-Job enthaelt genau einen `GretlDatenportalSeedBuilder`.
- Der Seed-Job nutzt die globale Themenrepo-Konfiguration, nicht duplizierte
  URL-/Branch-Werte im Job.
- Der Seed-Job hat per Default den Cron `H/15 * * * *`.
- Blank Cron deaktiviert nur den Timer, nicht den Job.
- Ohne Themenrepo-Konfiguration ist der Seed-Job disabled und hat keinen Timer.
- `seedJobAutoCreate=false` verhindert Neuanlage und deaktiviert einen bereits
  pluginverwalteten Seed-Job.
- Bestehende fremde Jobs mit Name `gretl-datenportal-seed` werden nicht
  ueberschrieben.
- Wiederholtes Provisionieren erzeugt keine doppelten Builder oder Trigger.
- Ein Build des Seed-Jobs erzeugt weiterhin die Organisation-`WorkflowJob`s.
- `datenportal-jenkins-dev` erzeugt keinen zweiten Seed-Job per JCasC.
- `datenportal-themenrepo`-Doku beschreibt den neuen Bootstrap korrekt.
- Alle relevanten Tests laufen:

```bash
cd ../datenportal-jenkins-gretl-plugin
mvn -ntp test
```

- Lokaler E2E-Loop funktioniert:

```bash
cd ../datenportal-jenkins-gretl-plugin
mvn -ntp package

cd ../datenportal-jenkins-dev
./bin/install-gretl-datenportal-plugin.sh
./bin/start.sh
```

Danach:

- Jenkins zeigt `gretl-datenportal-seed`.
- Manueller Seed-Lauf ist erfolgreich.
- `/gretl-datenportal` listet die generierten Datenportal-Jobs.

## Hinweise fuer den Implementierungsagenten

- Keine unrelated Refactorings.
- Keine Umstellung der generierten Datenportal-Jobs auf runtime Git checkout.
- Keine Loeschlogik fuer alte generierte Organisation-Jobs einfuehren.
- Keine Credentials-Funktionalitaet in diesem Vorhaben einfuehren.
- Bestehende Tests nicht abschwaechen.
- Wenn Jenkins-API-Signaturen leicht abweichen, compile-getrieben anpassen,
  aber das oben beschriebene Verhalten beibehalten.
- Nach Aenderungen in allen drei Repos jeweils `git status --short` pruefen und
  im Abschlussbericht getrennt nach Repo zusammenfassen.
