package ch.so.agi.jenkins.delivery.web;

import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import ch.so.agi.jenkins.delivery.identity.*;
import ch.so.agi.jenkins.delivery.queue.*;
import ch.so.agi.jenkins.delivery.steps.CsvDeliveryProcessor;
import ch.so.agi.jenkins.delivery.upload.*;
import hudson.Extension;
import hudson.model.RootAction;
import java.io.IOException;
import java.util.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension public final class DeliveryRootAction implements RootAction {
    @Override public String getUrlName(){return "data-delivery";}
    @Override public String getDisplayName(){return "CSV-Anlieferung";}
    @Override public String getIconFileName(){return "symbol-upload";}
    public String getCrumbField(){var issuer=Jenkins.get().getCrumbIssuer();return issuer==null?"":issuer.getCrumbRequestField();}
    public String getCrumb(){var issuer=Jenkins.get().getCrumbIssuer();return issuer==null?"":issuer.getCrumb();}
    public String freshCommand(){return UUID.randomUUID().toString();}
    public boolean isProcessingReady(){return CsvDeliveryProcessor.ready()&&CsvJobProvisioner.current()!=null;}
    private static final JenkinsOidcActorResolver IDENTITIES=new JenkinsOidcActorResolver();
    private JenkinsOidcActorResolver.UserTokenContext actor(StaplerRequest2 req){Jenkins.get().checkPermission(Jenkins.READ);return IDENTITIES.current(req);}
    private void requireReady(){if(!isProcessingReady())throw new DeliveryProblem(503,"PROCESSOR_NOT_READY");}
    private String base(StaplerRequest2 req){return req.getContextPath()+"/data-delivery/";}
    private void view(StaplerRequest2 req,StaplerResponse2 res,String name)throws Exception{res.setHeader("Cache-Control","no-store");req.getView(this,name+".jelly").forward(req,res);}
    private void fail(StaplerRequest2 req,StaplerResponse2 res,DeliveryProblem problem)throws Exception{res.setStatus(problem.status());req.setAttribute("problem",problem.getMessage());req.setAttribute("loginRequired",problem.status()==401);req.setAttribute("identityUnknown","PRINCIPAL_UNKNOWN".equals(problem.code()));view(req,res,"problem");}
    private static void get(StaplerRequest2 req){if(!"GET".equals(req.getMethod()))throw new DeliveryProblem(405,"INVALID_INPUT");}
    private static UUID uuid(String value){if(value==null || !value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw DeliveryProblem.invalid();return UUID.fromString(value);}
    private static String parameter(StaplerRequest2 req,String key,int max){var values=req.getParameterValues(key);if(values==null || values.length!=1 || values[0].length()>max)throw DeliveryProblem.invalid();Wire.unicode(values[0]);return values[0];}
    private static void post(StaplerRequest2 req,boolean multipart){
        var config=DeliveryPluginConfiguration.get();config.validate();var origins=Collections.list(req.getHeaders("Origin"));
        if(origins.size()!=1 || !config.getPublicOrigin().equals(origins.getFirst()))throw new DeliveryProblem(403,"NOT_AUTHORIZED");
        if(!multipart){long length=req.getContentLengthLong();if(length<0 || length>16384 || req.getContentType()==null || !req.getContentType().split(";",2)[0].equalsIgnoreCase("application/x-www-form-urlencoded"))throw DeliveryProblem.invalid();}
        var crumbIssuer=Jenkins.get().getCrumbIssuer();if(crumbIssuer==null)throw new DeliveryProblem(403,"NOT_AUTHORIZED");
        String field=crumbIssuer.getCrumbRequestField();var headers=Collections.list(req.getHeaders(field));String crumb=headers.size()==1?headers.getFirst():null;
        if(!multipart && headers.isEmpty())crumb=parameter(req,field,256);
        if(crumb==null || crumb.length()>256 || !crumbIssuer.validateCrumb(req,crumbIssuer.getDescriptor().getCrumbSalt(),crumb))throw new DeliveryProblem(403,"NOT_AUTHORIZED");
    }
    public void doIndex(StaplerRequest2 req,StaplerResponse2 res)throws Exception{
        try{get(req);var user=actor(req);String search=req.getParameter("search");if(search==null)search="";if(search.length()>100)throw DeliveryProblem.invalid();
            var api=new IngestionApiClient();req.setAttribute("eligible",api.listEligible(user,search));req.setAttribute("submissions",api.list(user));req.setAttribute("search",search);view(req,res,"index");
        }catch(DeliveryProblem p){fail(req,res,p);}
    }
    @RequirePOST public void doIdentify(StaplerRequest2 req,StaplerResponse2 res)throws Exception{
        try{post(req,false);var user=actor(req);new IngestionApiClient().identify(user,uuid(parameter(req,"commandId",36)));redirect(res,base(req));}catch(DeliveryProblem p){fail(req,res,p);}
    }
    @RequirePOST public void doPrepare(StaplerRequest2 req,StaplerResponse2 res)throws Exception{
        try{post(req,false);var user=actor(req);requireReady();String mode=parameter(req,"mode",12),digest=parameter(req,"configurationDigest",64);
            if(!Set.of("replace","append").contains(mode)||!Wire.hash(digest))throw DeliveryProblem.invalid();
            var input=new Wire.Create(uuid(parameter(req,"datasetId",36)),uuid(parameter(req,"issueId",36)),mode,digest,parameter(req,"comment",4000));
            var created=new IngestionApiClient().create(user,uuid(parameter(req,"commandId",36)),input);redirect(res,base(req)+"uploadForm?id="+created.submissionId());
        }catch(DeliveryProblem p){fail(req,res,p);}
    }
    public void doUploadForm(StaplerRequest2 req,StaplerResponse2 res)throws Exception{
        try{get(req);var user=actor(req);requireReady();UUID id=uuid(parameter(req,"id",36));var permit=new IngestionApiClient().uploadPermit(user,id);
            if(!id.equals(permit.submissionId()))throw DeliveryProblem.unavailable();req.setAttribute("submission",id.toString());req.setAttribute("uploadKey",UUID.randomUUID().toString());req.setAttribute("uploadLimit",Math.min(permit.maxBytes(),DeliveryPluginConfiguration.get().getMaxUploadBytes()));view(req,res,"upload");
        }catch(DeliveryProblem p){fail(req,res,p);}
    }
    @RequirePOST public void doUpload(StaplerRequest2 req,StaplerResponse2 res)throws IOException{
        try{
            post(req,true);var user=actor(req);requireReady();String query=req.getQueryString();if(query==null || !query.startsWith("id=") || query.length()!=39)throw new DeliveryProblem(400,"INVALID_UPLOAD_REFERENCE");
            UUID id=uuid(query.substring(3));var keys=Collections.list(req.getHeaders("Idempotency-Key"));if(keys.size()!=1 || !keys.getFirst().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new DeliveryProblem(400,"INVALID_UPLOAD_COMMAND");UUID key=uuid(keys.getFirst());
            var api=new IngestionApiClient();var permit=api.uploadPermit(user,id);if(!id.equals(permit.submissionId()))throw DeliveryProblem.unavailable();
            long limit=Math.min(permit.maxBytes(),DeliveryPluginConfiguration.get().getMaxUploadBytes());var store=new IncomingUploadStore(Jenkins.get().getRootDir().toPath());
            var file=store.receive(id,limit,(path,cap)->MultipartCsv.read(req,path,cap));
            String proof=Wire.spoolProof(ServiceTokenClient.pluginSecret(),id,file.size(),file.sha256(),file.originalName(),file.privateSpoolReference());
            var receipt=api.seal(user,id,key,new Wire.Seal(file.size(),file.sha256(),file.originalName(),file.privateSpoolReference(),proof));if(!id.equals(receipt.submissionId()))throw DeliveryProblem.unavailable();
            json(res,201,Map.of("statusUrl",base(req)+"status?id="+id));
        }catch(DeliveryProblem p){json(res,p.status(),Map.of("code",p.code(),"message",p.getMessage()));}
        catch(IOException e){var p=DeliveryProblem.unavailable();json(res,p.status(),Map.of("code",p.code(),"message",p.getMessage()));}
    }
    public void doStatus(StaplerRequest2 req,StaplerResponse2 res)throws Exception{
        try{get(req);var user=actor(req);UUID id=uuid(parameter(req,"id",36));var status=new IngestionApiClient().getStatus(user,id);req.setAttribute("delivery",status);view(req,res,"status");}catch(DeliveryProblem p){fail(req,res,p);}
    }
    public void doReport(StaplerRequest2 req,StaplerResponse2 res)throws Exception{
        try{get(req);var user=actor(req);UUID id=uuid(parameter(req,"id",36));req.setAttribute("report",new IngestionApiClient().report(user,id));view(req,res,"report");}catch(DeliveryProblem p){fail(req,res,p);}
    }
    @RequirePOST public void doCancel(StaplerRequest2 req,StaplerResponse2 res)throws Exception{mutate(req,res,false);}
    @RequirePOST public void doRetry(StaplerRequest2 req,StaplerResponse2 res)throws Exception{mutate(req,res,true);}
    private void mutate(StaplerRequest2 req,StaplerResponse2 res,boolean retry)throws Exception{
        try{post(req,false);var user=actor(req);if(retry)requireReady();UUID id=uuid(parameter(req,"id",36)),key=uuid(parameter(req,"commandId",36));long revision;
            try{revision=Long.parseLong(parameter(req,"statusVersion",19));}catch(NumberFormatException e){throw DeliveryProblem.invalid();}
            var input=new Wire.Mutation(revision,parameter(req,"reason",4000));var api=new IngestionApiClient();if(retry)api.retry(user,id,key,input);else{api.cancel(user,id,key,input);DeliveryDispatcher.cancelLocal(id);}redirect(res,base(req)+"status?id="+id);
        }catch(DeliveryProblem p){fail(req,res,p);}
    }
    private static void redirect(StaplerResponse2 res,String target){res.setStatus(303);res.setHeader("Location",target);res.setHeader("Cache-Control","no-store");}
    private static void json(StaplerResponse2 res,int status,Object value)throws IOException{res.setStatus(status);res.setHeader("Cache-Control","no-store");res.setContentType("application/json;charset=UTF-8");res.getOutputStream().write(Wire.JSON.writeValueAsBytes(value));}
    public String stateLabel(String state){return switch(state){case "UPLOADING"->"Upload vorbereitet";case "READY"->"Versiegelt; wartet auf Verarbeitung";case "QUEUED"->"In Warteschlange";case "VALIDATING"->"Eingang wird geprüft";case "STAGING","READY_TO_COMMIT"->"Verarbeitung läuft";case "COMMITTED"->"Angenommen; SQL-Aktualisierung ausstehend";case "PUBLISHED"->"Veröffentlicht";case "FAILED_RETRYABLE"->"Technischer Fehler; neuer Versuch möglich";case "CANCELLED"->"Abgebrochen";default->"Nicht angenommen";};}
    public boolean canCancel(String state){return Set.of("UPLOADING","READY","QUEUED","VALIDATING","STAGING","READY_TO_COMMIT","FAILED_RETRYABLE").contains(state);}
}
