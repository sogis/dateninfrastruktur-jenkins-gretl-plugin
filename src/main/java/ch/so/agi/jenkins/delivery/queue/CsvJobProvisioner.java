package ch.so.agi.jenkins.delivery.queue;

import hudson.BulkChange;
import hudson.init.*;
import hudson.model.*;
import hudson.security.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.matrixauth.*;
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;

public final class CsvJobProvisioner {
    public static final String JOB="di-csv-ingest",EXECUTOR="di-csv-executor";
    private static final String SCRIPT=script();
    private CsvJobProvisioner(){}
    private static String script(){try(var in=CsvJobProvisioner.class.getResourceAsStream("/delivery/Jenkinsfile")){if(in==null)throw new IllegalStateException("Missing fixed delivery pipeline");return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException e){throw new IllegalStateException("Missing fixed delivery pipeline");}}
    @Initializer(after=InitMilestone.JOB_LOADED) public static void startup()throws Exception{
        var config=DeliveryPluginConfiguration.get();if(config.configured()){config.validate();ensureJob();}
    }
    public static synchronized WorkflowJob ensureJob()throws IOException,Descriptor.FormException{
        try(var elevated=ACL.as2(ACL.SYSTEM2)){
            var jenkins=Jenkins.get();var item=jenkins.getItem(JOB);
            if(item!=null && !(item instanceof WorkflowJob job && owned(job)))throw new IOException("Unowned di-csv-ingest name collision; job untouched");
            WorkflowJob job=item==null?jenkins.createProject(WorkflowJob.class,JOB):(WorkflowJob)item;
            try(var update=new BulkChange(job)){
                if(item==null)job.addProperty(new DeliveryJobProperty());
                if(!fixedDefinition(job))job.setDefinition(new CpsFlowDefinition(SCRIPT,true));
                job.setDescription("Technische CSV-Ausführung. Start ausschliesslich über einen zentral reservierten Lieferauftrag.");
                job.removeProperty(ParametersDefinitionProperty.class);
                var permissions=new HashMap<Permission,Set<PermissionEntry>>();
                var executor=Set.of(new PermissionEntry(AuthorizationType.USER,EXECUTOR));
                for(var p:List.of(Item.READ,Item.BUILD,Item.WORKSPACE,Item.CANCEL))permissions.put(p,executor);
                job.removeProperty(AuthorizationMatrixProperty.class);job.addProperty(new AuthorizationMatrixProperty(permissions,new NonInheritingStrategy()));
                update.commit();
            }
            return job;
        }
    }
    public static boolean owned(WorkflowJob job){var marker=job.getProperty(DeliveryJobProperty.class);return JOB.equals(job.getFullName()) && marker!=null && marker.valid();}
    public static boolean fixedDefinition(WorkflowJob job){return job.getDefinition() instanceof CpsFlowDefinition c && c.isSandbox() && SCRIPT.equals(c.getScript());}
    public static boolean usable(WorkflowJob job){return owned(job)&&fixedDefinition(job)&&!job.isDisabled()&&job.getProperty(ParametersDefinitionProperty.class)==null;}
    public static WorkflowJob current(){try(var elevated=ACL.as2(ACL.SYSTEM2)){var item=Jenkins.get().getItem(JOB);return item instanceof WorkflowJob job && usable(job)?job:null;}}
}
