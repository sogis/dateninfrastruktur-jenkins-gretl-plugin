package ch.so.agi.jenkins.delivery.client;

import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import ch.so.agi.jenkins.delivery.identity.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Typed central API. No Jenkins build parameter can choose a backend, token or target. */
public final class IngestionApiClient {
    private final DeliveryPluginConfiguration config;
    public IngestionApiClient(){this(DeliveryPluginConfiguration.get());}
    public IngestionApiClient(DeliveryPluginConfiguration config){this.config=config;}
    public void identify(JenkinsOidcActorResolver.UserTokenContext user,UUID command){user("identity","POST",user,command,Map.of("schemaVersion",1));}
    public List<Wire.Eligible> listEligible(JenkinsOidcActorResolver.UserTokenContext user,String search){return List.of(read(user("datasets?offset=0&limit=100&search="+URLEncoder.encode(search,StandardCharsets.UTF_8),"GET",user,null,null),Wire.Eligible[].class));}
    public Wire.Created create(JenkinsOidcActorResolver.UserTokenContext user,UUID command,Wire.Create input){return read(user("submissions","POST",user,command,input),Wire.Created.class);}
    public Wire.UploadPermit uploadPermit(JenkinsOidcActorResolver.UserTokenContext user,UUID id){return read(user("submissions/"+id+"/upload-permit","GET",user,null,null),Wire.UploadPermit.class);}
    public Wire.Created seal(JenkinsOidcActorResolver.UserTokenContext user,UUID id,UUID command,Wire.Seal input){return read(user("submissions/"+id+"/seal","POST",user,command,input),Wire.Created.class);}
    public Wire.Status getStatus(JenkinsOidcActorResolver.UserTokenContext user,UUID id){return read(user("submissions/"+id,"GET",user,null,null),Wire.Status.class);}
    public List<Wire.Status> list(JenkinsOidcActorResolver.UserTokenContext user){return List.of(read(user("submissions?offset=0&limit=50","GET",user,null,null),Wire.Status[].class));}
    public Wire.Report report(JenkinsOidcActorResolver.UserTokenContext user,UUID id){return read(user("submissions/"+id+"/report","GET",user,null,null),Wire.Report.class);}
    public Wire.Created retry(JenkinsOidcActorResolver.UserTokenContext user,UUID id,UUID command,Wire.Mutation input){return read(user("submissions/"+id+"/retry","POST",user,command,input),Wire.Created.class);}
    public Wire.Created cancel(JenkinsOidcActorResolver.UserTokenContext user,UUID id,UUID command,Wire.Mutation input){return read(user("submissions/"+id+"/cancel","POST",user,command,input),Wire.Created.class);}
    public Wire.Reserved reserve(UUID id,UUID command){var response=service("dispatch/reserve","POST",command,new Wire.Reserve(id));return response.status()==204?null:read(response,Wire.Reserved.class);}
    public Wire.Reserved queued(UUID id,String queueId){return read(service("dispatch/"+id+"/queued","POST",UUID.randomUUID(),new Wire.Queued(queueId)),Wire.Reserved.class);}
    public Wire.SpoolDecision maintainSpool(UUID id){return read(service("dispatch/spool/"+id+"/maintenance","POST",UUID.randomUUID(),null),Wire.SpoolDecision.class);}
    public List<Wire.Reserved> reconcile(){return List.of(read(service("dispatch/reconciliation","GET",null,null),Wire.Reserved[].class));}
    public void interrupted(UUID dispatch,String runId){service("executions/dispatch/"+dispatch+"/interrupted","POST",UUID.randomUUID(),Map.of("runId",runId));}
    public Wire.Claimed claim(Wire.Claim input,UUID command){return read(service("executions/claim","POST",command,input),Wire.Claimed.class);}
    public void heartbeat(UUID id,Wire.Binding binding){service("executions/"+id+"/heartbeat","POST",UUID.randomUUID(),binding);}
    public void checkpoint(UUID id,Wire.Binding binding,String name,JsonNode evidence){service("executions/"+id+"/checkpoint","POST",UUID.randomUUID(),new Wire.Checkpoint(binding,name,evidence));}
    public Wire.Accepted complete(UUID id,Wire.Binding binding,JsonNode manifest,UUID command){return read(service("executions/"+id+"/complete","POST",command,new Wire.Complete(binding,manifest)),Wire.Accepted.class);}
    public void fail(UUID id,Wire.Binding binding,String code,Wire.ValidationIssue issue){service("executions/"+id+"/fail","POST",UUID.randomUUID(),new Wire.Fail(binding,code,issue));}
    private BoundedHttp.Response user(String path,String method,JenkinsOidcActorResolver.UserTokenContext user,UUID command,Object payload){return checked(request(path,method,user.accessToken(),ServiceTokenClient.pluginSecret(),command,payload),true);}
    private BoundedHttp.Response service(String path,String method,UUID command,Object payload){var tokens=ServiceTokenClient.get();var r=request(path,method,tokens.acquire(),null,command,payload);if(r.status()==401){tokens.invalidateOn401();r=request(path,method,tokens.acquire(),null,command,payload);}return checked(r,false);}
    private BoundedHttp.Response request(String path,String method,String token,String plugin,UUID command,Object payload){
        var headers=new HashMap<String,String>();headers.put("Authorization","Bearer "+token);headers.put("Accept","application/json");
        if(plugin!=null)headers.put("X-DI-Plugin-Credential",plugin);if(command!=null)headers.put("Idempotency-Key",command.toString());if(payload!=null)headers.put("Content-Type","application/json");
        return BoundedHttp.request(config.backendBaseUri().resolve(path),method,headers,payload==null?null:Wire.JSON.writeValueAsBytes(payload));
    }
    private static BoundedHttp.Response checked(BoundedHttp.Response r,boolean user){
        if(r.status()>=200 && r.status()<300)return r;if(r.status()==401 && user)throw DeliveryProblem.login();
        if(r.status()>=500 || r.status()<400)throw DeliveryProblem.unavailable();String code="INVALID_INPUT";
        try{String candidate=Wire.JSON.readTree(r.body()).path("code").asString("");if(candidate.matches("[A-Z_]{1,64}"))code=candidate;}catch(RuntimeException ignored){/* No remote text is emitted. */}
        throw new DeliveryProblem(r.status(),code);
    }
    private static <T>T read(BoundedHttp.Response r,Class<T> type){try{T value=Wire.JSON.readValue(r.body(),type);if(value==null)throw DeliveryProblem.unavailable();return value;}catch(RuntimeException e){throw DeliveryProblem.unavailable();}}
}
