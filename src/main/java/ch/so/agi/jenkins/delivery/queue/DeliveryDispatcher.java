package ch.so.agi.jenkins.delivery.queue;

import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.steps.CsvDeliveryProcessor;
import hudson.Extension;
import hudson.model.*;
import hudson.security.ACL;
import java.util.UUID;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

/** Bounded at-least-once scheduling. HTTP stays outside Jenkins queue/system-ACL blocks. */
@Extension public final class DeliveryDispatcher extends AsyncPeriodicWork {
    private String spoolAfter="";
    private long nextSpoolSweep;
    public DeliveryDispatcher(){super("CSV delivery dispatch");}
    @Override public long getRecurrencePeriod(){return 5000L;}
    @Override protected void execute(TaskListener listener){
        if(!ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration.get().configured() || !CsvDeliveryProcessor.ready())return;
        try{reconcilePending();maintainSpool();}catch(DeliveryProblem unavailable){/* Persistent reservations will be retried. No tokens/errors logged. */}
    }
    private void maintainSpool(){
        long now=System.nanoTime();if(nextSpoolSweep!=0 && now-nextSpoolSweep<0)return;
        nextSpoolSweep=now+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
        var store=new ch.so.agi.jenkins.delivery.upload.IncomingUploadStore(Jenkins.get().getRootDir().toPath());
        var api=new IngestionApiClient();
        try{
            var candidates=store.maintenanceBatch(spoolAfter);
            for(var id:candidates){
                try{store.maintain(id,()->{var decision=api.maintainSpool(id);return id.equals(decision.submissionId())&&decision.remove();});}
                catch(DeliveryProblem failure){if(failure.status()>=500)throw failure;}
            }
            spoolAfter=candidates.size()<8?"":candidates.getLast().toString();
        }catch(java.io.IOException ignored){/* Keep data when a private file cannot be checked. */}
    }
    public void reconcilePending(){
        var job=CsvJobProvisioner.current();if(job==null)return;var api=new IngestionApiClient();
        for(var dispatch:api.reconcile())dispatch(api,job,dispatch);
        for(int i=0;i<8;i++){var dispatch=api.reserve(null,UUID.randomUUID());if(dispatch==null)break;dispatch(api,job,dispatch);}
    }
    private static void dispatch(IngestionApiClient api,WorkflowJob job,Wire.Reserved reserved){
        if(!CsvJobProvisioner.JOB.equals(reserved.jobName()) || reserved.dispatchId()==null || reserved.submissionId()==null)throw DeliveryProblem.unavailable();
        if("CLAIMED".equals(reserved.state())){
            if(reserved.runId()==null || !reserved.runId().matches("[1-9][0-9]{0,9}"))throw DeliveryProblem.unavailable();
            boolean ended;
            try(var elevated=ACL.as2(ACL.SYSTEM2)){
                var run=job.getBuildByNumber(Integer.parseInt(reserved.runId()));
                var marker=run==null?null:run.getAction(DeliveryQueueAction.class);
                ended=marker!=null && reserved.dispatchId().equals(marker.dispatchId()) && reserved.submissionId().equals(marker.submissionId()) && !run.isBuilding() && run.getResult()!=null;
            }
            if(ended)api.interrupted(reserved.dispatchId(),reserved.runId());
            return;
        }
        Long queueId;
        try(var elevated=ACL.as2(ACL.SYSTEM2)){
            var queue=Jenkins.get().getQueue();try{queueId=Queue.withLock(()->{
            Long previous=findQueuedDispatch(queue,job,reserved.dispatchId());
            if(previous!=null)return previous;
                var result=DeliveryQueueDecision.schedule(queue,job,new DeliveryQueueAction(reserved.submissionId(),reserved.dispatchId()));
                if(!result.isAccepted() || result.getItem()==null)throw DeliveryProblem.unavailable();return result.getItem().getId();
            });}catch(DeliveryProblem problem){throw problem;}catch(Exception error){throw DeliveryProblem.unavailable();}
        }
        if(reserved.queueId()==null)api.queued(reserved.dispatchId(),Long.toString(queueId));
    }
    private static Long findQueuedDispatch(Queue queue,WorkflowJob job,UUID id){
        for(var item:queue.getItems())if(item.task==job){var marker=item.getAction(DeliveryQueueAction.class);if(marker!=null && id.equals(marker.dispatchId()))return item.getId();}
        int count=0;for(var run=job.getLastBuild();run!=null && count++<256;run=run.getPreviousBuild()){
            var marker=run.getAction(DeliveryQueueAction.class);if(marker!=null && id.equals(marker.dispatchId()) && (run.isBuilding() || run.getAction(ch.so.agi.jenkins.delivery.steps.DeliveryRunBindingAction.class)!=null) && run.getQueueId()>0)return run.getQueueId();
        }return null;
    }
    /** Only after Admin has committed cancellation and fenced this exact submission. */
    public static void cancelLocal(UUID id){
        var job=CsvJobProvisioner.current();if(job==null)return;
        try(var elevated=ACL.as2(ACL.SYSTEM2)){
            var queue=Jenkins.get().getQueue();for(var item:queue.getItems())if(item.task==job){var marker=item.getAction(DeliveryQueueAction.class);if(marker!=null && id.equals(marker.submissionId()))queue.cancel(item);}
            int count=0;for(var run=job.getLastBuild();run!=null && count++<256;run=run.getPreviousBuild()){
                var marker=run.getAction(DeliveryQueueAction.class);if(marker!=null && id.equals(marker.submissionId()) && run.isBuilding() && run.getExecution()!=null){try{run.getExecution().interrupt(Result.ABORTED);}catch(Exception ignored){/* Central fence already prevents acceptance. */}}
            }
        }
    }
}
