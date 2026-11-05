package ch.so.agi.jenkins.delivery.steps;

import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import ch.so.agi.jenkins.delivery.queue.*;
import ch.so.agi.jenkins.delivery.upload.IncomingUploadStore;
import hudson.*;
import hudson.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import tools.jackson.databind.JsonNode;

/** Four fixed, argument-free steps. Neither plan nor token is returned to CPS. */
public final class DeliverySteps {
    private DeliverySteps(){}
    private static UUID command(String action,UUID id){return UUID.nameUUIDFromBytes(("di-v1:"+action+":"+id).getBytes(StandardCharsets.UTF_8));}
    private static WorkflowRun run(StepContext context)throws Exception{
        if(!(context.get(Run.class) instanceof WorkflowRun run) || !CsvJobProvisioner.usable(run.getParent()) || run.getActions(DeliveryQueueAction.class).size()!=1 || !(run.getExecution() instanceof CpsFlowExecution flow) || !CsvJobProvisioner.EXECUTOR.equals(flow.getAuthentication2().getName()))throw new AbortException("Kein gebundener technischer CSV-Lauf.");
        return run;
    }
    private static DeliveryRunBindingAction binding(WorkflowRun run)throws AbortException{var b=run.getAction(DeliveryRunBindingAction.class);if(b==null)throw new AbortException("Keine gültige Ausführungsbindung.");return b;}
    private static Wire.Runner runner()throws IOException{
        try(var input=Files.newInputStream(Path.of("/opt/dateninfrastruktur/runner-provenance.json"))){
            byte[] bytes=input.readNBytes(2049);if(bytes.length>2048)throw DeliveryProblem.unavailable();
            var provenance=Wire.JSON.readTree(bytes);String bundle=provenance.path("runnerSha256").asString(""),repo=provenance.path("repositorySha256").asString(""),image=System.getenv("DI_JENKINS_IMAGE_SHA256");
            if(!Wire.hash(bundle)||!Wire.hash(repo)||!Wire.hash(image))throw DeliveryProblem.unavailable();
            return new Wire.Runner(bundle,repo,image);
        }
    }
    private static FilePath directory(StepContext context,DeliveryRunBindingAction binding)throws Exception{return context.get(FilePath.class).child(".di-"+binding.executionId());}
    private static JsonNode read(FilePath file,int limit)throws Exception{try(var in=file.read()){byte[] bytes=in.readNBytes(limit+1);if(bytes.length>limit)throw DeliveryProblem.invalid();return Wire.JSON.readTree(bytes);}}
    private static void write(FilePath file,byte[] bytes)throws Exception{try(var out=file.write()){out.write(bytes);}file.chmod(0600);}
    private static JsonNode checkedPlan(FilePath dir,DeliveryRunBindingAction b)throws Exception{
        var plan=read(dir.child("plan.json"),131072);if(!b.planDigest().equals(Wire.digest(plan)))throw new DeliveryProblem(409,"INPUT_MISMATCH");return plan;
    }
    private static void safeFail(DeliveryRunBindingAction b,String code){if(b!=null && !b.accepted())try{new IngestionApiClient().fail(b.executionId(),b.binding(),code);}catch(DeliveryProblem ignored){/* A committed/stale state cannot be downgraded. */}}
    private static String failureCode(Exception e){if(e instanceof DeliveryProblem p && Set.of("INPUT_MISMATCH","ARTIFACT_CONFLICT","VALIDATION_FAILED").contains(p.code()))return p.code();return "DEPENDENCY_UNAVAILABLE";}
    private static AbortException safe(Exception e){return new AbortException(e instanceof DeliveryProblem p?p.getMessage():"Technischer CSV-Lauf beendet; Status im Lieferportal prüfen.");}
    abstract static class FixedDescriptor extends StepDescriptor {
        @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,FilePath.class,TaskListener.class);}
        @Override public String getDisplayName(){return getFunctionName();}
    }
    public static final class Claim extends Step {
        @DataBoundConstructor public Claim(){}
        @Override public StepExecution start(StepContext context){return new ClaimExecution(context);}
        @Extension public static final class Descriptor extends FixedDescriptor {@Override public String getFunctionName(){return "diClaimDelivery";}}
    }
    static final class ClaimExecution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID=1L;
        ClaimExecution(StepContext context){super(context);}
        @Override protected Void run()throws Exception{
            var run=DeliverySteps.run(getContext());var marker=run.getAction(DeliveryQueueAction.class);var api=new IngestionApiClient();
            var request=new Wire.Claim(marker.dispatchId(),DeliveryPluginConfiguration.get().getInstance(),CsvJobProvisioner.JOB,Integer.toString(run.getNumber()),runner());
            DeliveryRunBindingAction b=null;FilePath dir=null;boolean created=false;
            try{
                CsvDeliveryProcessor.require();Wire.Claimed claim;
                try{claim=api.claim(request,command("claim",marker.dispatchId()));}catch(DeliveryProblem error){if(error.status()!=503)throw error;claim=api.claim(request,command("claim",marker.dispatchId()));}
                if(claim.executionId()==null || claim.fence()<=0 || !Wire.hash(claim.leaseSecret()) || !Wire.hash(claim.planDigest()) || claim.plan()==null)throw DeliveryProblem.unavailable();
                var plan=claim.plan();
                if(plan.path("schemaVersion").asInt(0)!=1 || !marker.submissionId().toString().equals(plan.path("submissionId").asString("")) || !claim.executionId().toString().equals(plan.path("executionId").asString("")) || claim.fence()!=plan.path("fence").asLong(0) || !claim.planDigest().equals(Wire.digest(plan)) || !Wire.JSON.valueToTree(request.runner()).equals(plan.path("runner")))throw new DeliveryProblem(409,"INPUT_MISMATCH");
                b=new DeliveryRunBindingAction(marker.submissionId(),claim);run.addAction(b);run.save();
                dir=directory(getContext(),b);if(dir.exists())throw new DeliveryProblem(409,"INPUT_MISMATCH");dir.mkdirs();created=true;dir.chmod(0700);
                var input=plan.path("input");long size=input.path("size").asLong(0);String sha=input.path("sha256").asString(""),ref=input.path("privateSpoolReference").asString("");
                new IncomingUploadStore(Jenkins.get().getRootDir().toPath()).copyToRun(marker.submissionId(),size,sha,ref,dir.child("input.csv"));
                write(dir.child("plan.json"),Wire.canonical(plan).getBytes(StandardCharsets.UTF_8));
                api.checkpoint(b.executionId(),b.binding(),"INPUT_VERIFIED",Wire.JSON.valueToTree(Map.of("schemaVersion",1,"size",size,"sha256",sha)));return null;
            }catch(InterruptedException error){safeFail(b,"DEPENDENCY_UNAVAILABLE");if(created && dir!=null)dir.deleteRecursive();throw error;}
            catch(Exception error){safeFail(b,failureCode(error));if(created && dir!=null)dir.deleteRecursive();throw safe(error);}
        }
    }
    public static final class Process extends Step {
        @DataBoundConstructor public Process(){}
        @Override public StepExecution start(StepContext context){return new ProcessExecution(context);}
        @Extension public static final class Descriptor extends FixedDescriptor {@Override public String getFunctionName(){return "diRunCsv";}}
    }
    static final class ProcessExecution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID=1L;
        ProcessExecution(StepContext context){super(context);}
        @Override protected Void run()throws Exception{
            var run=DeliverySteps.run(getContext());var b=binding(run);var api=new IngestionApiClient();var dir=directory(getContext(),b);
            var callbacks=new CsvDeliveryProcessor.Callbacks(){
                @Override public void checkpoint(String step,JsonNode evidence){api.checkpoint(b.executionId(),b.binding(),step,evidence);}
                @Override public void heartbeat(){api.heartbeat(b.executionId(),b.binding());}
            };
            var heartbeat=Timer.get().scheduleWithFixedDelay(()->{try{callbacks.heartbeat();}catch(DeliveryProblem ignored){/* Final check remains authoritative. */}},60,60,TimeUnit.SECONDS);
            try(var executor=hudson.security.ACL.as2(((CpsFlowExecution)run.getExecution()).getAuthentication2())){
                var result=CsvDeliveryProcessor.require().process(dir,checkedPlan(dir,b),callbacks,getContext().get(TaskListener.class));
                callbacks.heartbeat();if(result==null || !result.isObject())throw DeliveryProblem.unavailable();byte[] bytes=Wire.JSON.writeValueAsBytes(result);if(bytes.length>49152)throw DeliveryProblem.invalid();write(dir.child("result.json"),bytes);return null;
            }catch(InterruptedException error){safeFail(b,"DEPENDENCY_UNAVAILABLE");throw error;}
            catch(Exception error){safeFail(b,failureCode(error));throw safe(error);}finally{heartbeat.cancel(false);}
        }
    }
    public static final class Complete extends Step {
        @DataBoundConstructor public Complete(){}
        @Override public StepExecution start(StepContext context){return new CompleteExecution(context);}
        @Extension public static final class Descriptor extends FixedDescriptor {@Override public String getFunctionName(){return "diCompleteDelivery";}}
    }
    static final class CompleteExecution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID=1L;
        CompleteExecution(StepContext context){super(context);}
        @Override protected Void run()throws Exception{
            var run=DeliverySteps.run(getContext());var b=binding(run);var api=new IngestionApiClient();
            try{
                var manifest=read(directory(getContext(),b).child("result.json"),49152);Wire.Accepted result;
                try{result=api.complete(b.executionId(),b.binding(),manifest,command("complete",b.executionId()));}catch(DeliveryProblem error){if(error.status()!=503)throw error;result=api.complete(b.executionId(),b.binding(),manifest,command("complete",b.executionId()));}
                if(!b.submissionId().equals(result.submissionId()) || result.versionId()==null || !Set.of("COMMITTED","PUBLISHED").contains(result.state()))throw DeliveryProblem.unavailable();
                b.markAccepted();run.save();getContext().get(TaskListener.class).getLogger().println("PUBLISHED".equals(result.state())?"Fachlich veröffentlicht.":"Fachlich angenommen; SQL-Aktualisierung ausstehend.");return null;
            }catch(InterruptedException error){throw error;}catch(Exception error){safeFail(b,failureCode(error));throw safe(error);}
        }
    }
    public static final class Clean extends Step {
        @DataBoundConstructor public Clean(){}
        @Override public StepExecution start(StepContext context){return new CleanExecution(context);}
        @Extension public static final class Descriptor extends FixedDescriptor {@Override public String getFunctionName(){return "diCleanDelivery";}}
    }
    static final class CleanExecution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID=1L;
        CleanExecution(StepContext context){super(context);}
        @Override protected Void run()throws Exception{var run=DeliverySteps.run(getContext());var b=run.getAction(DeliveryRunBindingAction.class);if(b!=null){var dir=directory(getContext(),b);if(dir.exists())dir.deleteRecursive();}return null;}
    }
    @Extension public static final class DeliveryRunListener extends hudson.model.listeners.RunListener<WorkflowRun> {
        @Override public void onCompleted(WorkflowRun run,TaskListener listener){
            if(!CsvJobProvisioner.owned(run.getParent()) || run.getAction(DeliveryQueueAction.class)==null)return;
            var b=run.getAction(DeliveryRunBindingAction.class);
            if(b!=null && !b.accepted())Timer.get().execute(()->safeFail(b,"DEPENDENCY_UNAVAILABLE"));
        }
    }
}
