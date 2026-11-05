package ch.so.agi.jenkins.delivery.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;

/** Strict v1 wire DTOs. Arrays are ordered and nulls explicit in the request/plan hash. */
public final class Wire {
    private Wire(){}
    public static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).rebuild()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    public record Eligible(UUID datasetId,String identifier,String title,String office,UUID issueId,String issueKey,List<String> allowedModes,String configurationDigest,String definitionKey,String definitionSha256){}
    public record Create(UUID datasetId,UUID issueId,String mode,String expectedConfigurationDigest,String comment){}
    public record Created(UUID submissionId){}
    public record UploadPermit(UUID submissionId,long maxBytes){}
    public record Seal(long inputSize,String inputSha256,String originalName,String privateSpoolReference,String spoolProof){@Override public String toString(){return "Seal[private]";}}
    public record Mutation(long expectedStatusVersion,String reason){}
    public record Status(UUID submissionId,UUID datasetId,UUID issueId,String state,long statusVersion,String originalName,Long inputSize,String mode,UUID publishedVersionId,String errorCode,String message,String datasetTitle,String createdAt){}
    public record Step(String checkpoint){}
    public record Report(UUID submissionId,String state,String errorCode,String message,List<Step> steps,List<ValidationIssue> issues){}
    public record Reserve(UUID submissionId){}
    public record Reserved(UUID dispatchId,UUID submissionId,String jobName,String state,String queueId,String runId){}
    public record SpoolDecision(UUID submissionId,boolean remove){}
    public record Queued(String queueId){}
    public record Claim(UUID dispatchId,String jenkinsInstance,String jobName,String runId,Runner runner){}
    public record Runner(String runnerSha256,String repositorySha256,String imageSha256){}
    public record Claimed(UUID executionId,long fence,String leaseSecret,String planDigest,JsonNode plan){@Override public String toString(){return "Claimed[private]";}}
    public record Binding(long fence,String leaseSecret){@Override public String toString(){return "Binding[private]";}}
    public record Checkpoint(Binding binding,String checkpoint,JsonNode evidence){}
    public record Complete(Binding binding,JsonNode manifest){}
    public record ValidationIssue(String code,long row,String attribute){}
    public record Fail(Binding binding,String code,ValidationIssue issue){}
    public record Accepted(UUID submissionId,UUID versionId,long publicationRevision,String state){}
    public record Problem(int status,String code,String detail){}
    public static boolean hash(String value){return value!=null && value.matches("[0-9a-f]{64}");}
    public static String digest(JsonNode value){return sha(("di-json-v1\n"+canonical(value)).getBytes(StandardCharsets.UTF_8));}
    public static String canonical(JsonNode value){
        if(value.isNull())return "null";
        if(value.isObject()){
            var names=new ArrayList<String>();value.propertyNames().forEach(names::add);names.forEach(Wire::unicode);
            names.sort((a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)));
            return names.stream().map(k->JSON.writeValueAsString(k)+":"+canonical(value.get(k))).collect(java.util.stream.Collectors.joining(",","{","}"));
        }
        if(value.isArray()){var fields=new ArrayList<String>();value.forEach(v->fields.add(canonical(v)));return "["+String.join(",",fields)+"]";}
        if(value.isString()){unicode(value.asString());return JSON.writeValueAsString(value.asString());}
        if(value.isBoolean() || value.isIntegralNumber())return value.toString();
        if(value.isBigDecimal()){var n=value.decimalValue();if(Math.abs((long)n.scale())>1024 || n.precision()>1024)throw DeliveryProblem.invalid();return n.stripTrailingZeros().toPlainString();}
        throw DeliveryProblem.invalid();
    }
    public static void unicode(String text){for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(Character.isHighSurrogate(ch)){if(++i>=text.length() || !Character.isLowSurrogate(text.charAt(i)))throw DeliveryProblem.invalid();}else if(Character.isLowSurrogate(ch))throw DeliveryProblem.invalid();}}
    public static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static String spoolProof(String secret,UUID id,long size,String sha,String name,String ref){
        try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            var body=JSON.valueToTree(Map.of("schemaVersion",1,"submissionId",id.toString(),"inputSize",size,"inputSha256",sha,"originalName",name,"privateSpoolReference",ref));
            return HexFormat.of().formatHex(mac.doFinal(("di-spool-v1\n"+canonical(body)).getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}
    }
}
