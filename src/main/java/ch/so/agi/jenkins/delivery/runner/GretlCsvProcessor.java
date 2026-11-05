package ch.so.agi.jenkins.delivery.runner;

import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.steps.CsvDeliveryProcessor;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;

/** The one fixed production runner. No user-selected executable, repository, SQL or target. */
@Extension public final class GretlCsvProcessor implements CsvDeliveryProcessor {
    @Override public JsonNode process(FilePath directory,JsonNode plan,Callbacks callbacks,TaskListener listener)throws Exception{
        if(directory.isRemote())throw DeliveryProblem.unavailable();
        Path run=Path.of(directory.getRemote()).toRealPath(LinkOption.NOFOLLOW_LINKS);
        String submission=id(plan,"submissionId"),execution=id(plan,"executionId");
        if(!run.getFileName().toString().equals(".di-"+execution)||Files.isSymbolicLink(run))throw DeliveryProblem.invalid();
        var artifacts=new PrivateArtifacts(run);var input=plan.path("input");
        var base=plan.path("baseSnapshot");
        if("append".equals(plan.path("mode").asString(""))){
            artifacts.downloadBase(base.path("csvObjectKey").asString(""),base.path("csvSize").asLong(0),base.path("csvSha256").asString(""));
        }
        callbacks.heartbeat();
        runGretl(run,plan.path("limits").path("timeoutSeconds").asInt(0));
        var local=read(run.resolve("local-evidence.json"),16384);
        var incoming=local.path("incoming");var snapshot=local.path("snapshot");
        callbacks.checkpoint("INCOMING_VALIDATED",incoming);
        callbacks.checkpoint("SNAPSHOT_VALIDATED",snapshot);
        var candidate=new ClickHouseSnapshotWriter().stage(plan,run.resolve("snapshot.ch.jsonl"),snapshot);
        callbacks.checkpoint("CANDIDATE_VERIFIED",candidate);
        var archivedKey="ingestion/"+submission+"/input.csv";var reportKey="ingestion/"+submission+"/attempts/"+execution+"/report.json";
        var original=artifacts.store(archivedKey,run.resolve("input.csv"));
        if(original.size()!=input.path("size").asLong()||!original.sha256().equals(input.path("sha256").asString()))throw new DeliveryProblem(409,"INPUT_MISMATCH");
        var report=run.resolve("report.json");
        Files.write(report,Wire.JSON.writeValueAsBytes(Map.of("schemaVersion",1,"incoming",incoming,"snapshot",snapshot,"candidate",candidate)),StandardOpenOption.CREATE_NEW);
        var archivedReport=artifacts.store(reportKey,report);
        var outputs=new ArrayList<Map<String,Object>>();
        for(var output:plan.path("outputs")){
            var format=output.path("format").asString("");String outputId=id(output,"distributionId"),key=output.path("objectKey").asString("");
            if(!Set.of("csv","xlsx","parquet").contains(format)||!key.equals("downloads/"+outputId+"/data."+format))throw DeliveryProblem.invalid();
            var evidence=artifacts.store(key,run.resolve("data."+format));
            outputs.add(Map.of("distributionId",outputId,"format",format,"objectKey",key,"mediaType",output.path("mediaType").asString(),
                "byteSize",evidence.size(),"sha256",evidence.sha256(),"snapshotDigest",snapshot.path("dataSha256").asString()));
        }
        var runner=plan.path("runner");
        var out=Wire.JSON.valueToTree(Map.of("schemaVersion",1,"artifacts",outputs,"runnerSha256",runner.path("runnerSha256").asString(),
            "repositorySha256",runner.path("repositorySha256").asString(),"imageSha256",runner.path("imageSha256").asString(),
            "archive",Map.of("inputKey",archivedKey,"inputSize",original.size(),"inputSha256",original.sha256(),"reportKey",reportKey,
                "reportSize",archivedReport.size(),"reportSha256",archivedReport.sha256())));
        callbacks.checkpoint("ARTIFACTS_VERIFIED",out);
        return Wire.JSON.valueToTree(Map.of("schemaVersion",1,"submissionId",submission,"executionId",execution,"fence",plan.path("fence").asLong(),
            "planDigest",Wire.digest(plan),"input",Map.of("schemaVersion",1,"size",input.path("size").asLong(),"sha256",input.path("sha256").asString()),
            "incoming",incoming,"snapshot",snapshot,"candidate",candidate,"outputs",out));
    }
    private static void runGretl(Path run,int timeout)throws Exception{
        if(timeout<1||timeout>7200)throw DeliveryProblem.invalid();
        var command=new ProcessBuilder("/opt/dateninfrastruktur/gradle/bin/gradle","-p","/opt/dateninfrastruktur/runner-bundle/runner",
            "--project-cache-dir",run.resolve("gradle-cache").toString(),"--offline","--no-daemon","--console=plain","-PrunDirectory="+run,"ingestCsv").directory(run.toFile());
        var env=command.environment();env.clear();env.putAll(Map.of("JAVA_HOME","/opt/java/openjdk17","PATH","/opt/java/openjdk17/bin:/usr/bin:/bin",
            "TZ","UTC","LANG","C.UTF-8","GRADLE_USER_HOME",run.resolve("gradle-home").toString()));
        command.redirectOutput(ProcessBuilder.Redirect.DISCARD);command.redirectError(ProcessBuilder.Redirect.DISCARD);
        var child=command.start();
        try{
            if(!child.waitFor(timeout,TimeUnit.SECONDS))throw DeliveryProblem.unavailable();
            if(child.exitValue()!=0){
                if(Files.isRegularFile(run.resolve("validation-error.json"),LinkOption.NOFOLLOW_LINKS)){
                    var detail=read(run.resolve("validation-error.json"),2048);
                    String code=detail.path("code").asString("");
                    if(Set.of("INPUT_MISMATCH","DEFINITION_BINDING","DEFINITION_HASH","MODEL_HASH","SCHEMA_BINDING","PLAN_TARGET","PLAN_BINDING","PLAN_SCHEMA","BASE_DATA_HASH").contains(code))
                        throw new DeliveryProblem(409,"INPUT_MISMATCH");
                    throw DeliveryProblem.validation(code,detail.path("row").asLong(0),detail.path("attribute").asString(null));
                }
                for(String validation:List.of("validateIncomingCsv","validateCompleteSnapshot"))
                    if(Files.isRegularFile(run.resolve(validation+".started"))&&!Files.isRegularFile(run.resolve(validation+".json")))throw DeliveryProblem.validation("MODEL_RULE",0,null);
                throw DeliveryProblem.unavailable();
            }
        }finally{
            if(child.isAlive()){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();}
        }
    }
    private static JsonNode read(Path path,int limit)throws Exception{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw DeliveryProblem.unavailable();
        try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] data=in.readNBytes(limit+1);if(data.length>limit)throw DeliveryProblem.invalid();return Wire.JSON.readTree(data);}
    }
    private static String id(JsonNode node,String key){
        String value=node.path(key).asString("");
        if(!value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw DeliveryProblem.invalid();
        return value;
    }
}
