package ch.so.agi.jenkins.delivery.runner;

import ch.so.agi.jenkins.delivery.client.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** The loader can create and verify only private candidates. It never writes a current view. */
final class ClickHouseSnapshotWriter {
    private static final String ENDPOINT="http://clickhouse:8123/";
    private static final Pattern TYPE=Pattern.compile("(?:Nullable\\()?(?:Int32|String|Bool|Date32|DateTime64\\(3,'UTC'\\)|Decimal\\([0-9]{1,2},[0-9]\\)|Enum8\\('[A-Za-z][A-Za-z0-9_]*'=[0-9]+(?:,'[A-Za-z][A-Za-z0-9_]*'=[0-9]+)*\\))\\)?");
    private static final Pattern SCALE=Pattern.compile("Decimal\\([0-9]+,([0-9]+)\\)");
    private static final DateTimeFormatter UTC=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    JsonNode stage(JsonNode plan,Path rows,JsonNode snapshot)throws Exception{
        var target=plan.path("clickhouse");String name=target.path("candidateTable").asString("");
        if(!"di_data".equals(target.path("candidateDatabase").asString(""))||!name.matches("v_[0-9a-f]{32}_[0-9a-f]{32}"))throw DeliveryProblem.invalid();
        String table="di_data."+name;
        var names=new ArrayList<String>();var types=new ArrayList<String>();var logical=new ArrayList<String>();
        for(var column:plan.path("orderedColumns")){
            String attr=column.path("attribute").asString(""),type=column.path("clickhouseType").asString("");
            if(!attr.matches("[A-Za-z][A-Za-z0-9_]{0,127}")||!TYPE.matcher(type).matches()||names.contains(attr))throw DeliveryProblem.invalid();
            names.add(attr);types.add(type);logical.add(column.path("type").asString(""));
        }
        String ddl="_di_ordinal UInt64";for(int i=0;i<names.size();i++)ddl+=",`"+names.get(i)+"` "+types.get(i);
        var exists=small("EXISTS TABLE "+table);
        if("0".equals(exists)){
            // Unique version/execution name and synchronous write. Never refill an existing partial attempt.
            small("CREATE TABLE "+table+" ("+ddl+") ENGINE=MergeTree ORDER BY _di_ordinal");
            try(var reply=request("INSERT INTO "+table+" SETTINGS async_insert=0 FORMAT JSONCompactEachRow",HttpRequest.BodyPublishers.ofFile(rows))){
                if(reply.readNBytes(4097).length>4096)throw DeliveryProblem.unavailable();
            }
        }else if(!"1".equals(exists))throw DeliveryProblem.unavailable();
        try(var response=request("DESCRIBE TABLE "+table+" FORMAT JSONCompactEachRow",HttpRequest.BodyPublishers.noBody());
            var reader=new BufferedReader(new InputStreamReader(response,StandardCharsets.UTF_8))){
            checkColumn(reader.readLine(),"_di_ordinal","UInt64");
            for(int i=0;i<names.size();i++)checkColumn(reader.readLine(),names.get(i),types.get(i));
            if(reader.readLine()!=null)throw PrivateArtifacts.conflict();
        }
        String select="_di_ordinal";for(String n:names)select+=",toString(`"+n+"`)";
        var digest=PrivateArtifacts.sha();long expected=snapshot.path("rowCount").asLong(),count=0;
        try(var response=request("SELECT "+select+" FROM "+table+" ORDER BY _di_ordinal FORMAT JSONCompactEachRow",HttpRequest.BodyPublishers.noBody());
            var reader=new BufferedReader(new InputStreamReader(response,StandardCharsets.UTF_8))){
            for(String line;(line=reader.readLine())!=null;){
                if(line.length()>paddedRowLimit(plan))throw PrivateArtifacts.conflict();
                var data=Wire.JSON.readTree(line);
                if(++count>expected||data.size()!=names.size()+1||data.get(0).asLong()!=count)throw PrivateArtifacts.conflict();
                var normalized=new ArrayList<String>();
                for(int i=0;i<names.size();i++){var value=data.get(i+1);normalized.add(value.isNull()?null:canonical(value.asString(),logical.get(i),types.get(i)));}
                digest.update(Wire.JSON.writeValueAsBytes(normalized));digest.update((byte)'\n');
            }
        }
        String hash=HexFormat.of().formatHex(digest.digest());
        if(count!=expected||!hash.equals(snapshot.path("dataSha256").asString("")))throw PrivateArtifacts.conflict();
        return Wire.JSON.valueToTree(Map.of("schemaVersion",1,"database","di_data","table",name,"schemaDigest",plan.path("fullSchemaDigest").asString(),"rowCount",count,"dataSha256",hash));
    }
    private static int paddedRowLimit(JsonNode plan){return Math.min(52000000,plan.path("orderedColumns").size()*(plan.path("limits").path("maxCellBytes").asInt()*6+16)+32);}
    private static String canonical(String value,String type,String storage){
        return switch(type){
            case "decimal" -> {var scale=SCALE.matcher(storage);if(!scale.find())throw PrivateArtifacts.conflict();yield new BigDecimal(value).setScale(Integer.parseInt(scale.group(1))).toPlainString();}
            case "timestamp" -> UTC.format(Instant.parse(value.replace(' ','T')+"Z"));
            case "boolean" -> switch(value){case "1","true"->"true";case "0","false"->"false";default->throw PrivateArtifacts.conflict();};
            default -> value;
        };
    }
    private static void checkColumn(String line,String name,String type){
        if(line==null||line.length()>4096)throw PrivateArtifacts.conflict();
        var field=Wire.JSON.readTree(line);
        if(!name.equals(field.path(0).asString(""))||!type.equals(field.path(1).asString("").replace(" ","")))throw PrivateArtifacts.conflict();
    }
    private String small(String query)throws Exception{
        try(var body=request(query,HttpRequest.BodyPublishers.noBody())){
            byte[] bytes=body.readNBytes(4097);if(bytes.length>4096)throw DeliveryProblem.unavailable();
            return new String(bytes,StandardCharsets.UTF_8).strip();
        }
    }
    private InputStream request(String query,HttpRequest.BodyPublisher body)throws IOException,InterruptedException{
        URI uri=URI.create(ENDPOINT+"?wait_end_of_query=1&max_execution_time=120&query="+URLEncoder.encode(query,StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(135)).header("X-ClickHouse-User","di_csv_loader")
            .header("X-ClickHouse-Key",RunnerCredentials.read(RunnerCredentials.Key.CLICKHOUSE)).POST(body).build();
        var result=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        if(result.statusCode()!=200){result.body().close();throw DeliveryProblem.unavailable();}
        return result.body();
    }
}
