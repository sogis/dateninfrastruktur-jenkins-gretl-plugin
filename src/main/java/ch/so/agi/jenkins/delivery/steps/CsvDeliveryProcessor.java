package ch.so.agi.jenkins.delivery.steps;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.TaskListener;
import tools.jackson.databind.JsonNode;
import ch.so.agi.jenkins.delivery.client.DeliveryProblem;

/** The single fixed CSV processor. G3 ships no production processor; G4 supplies the GRETL implementation. */
public interface CsvDeliveryProcessor extends ExtensionPoint {
    JsonNode process(FilePath directory,JsonNode immutablePlan,Callbacks callbacks,TaskListener listener)throws Exception;
    interface Callbacks {void checkpoint(String step,JsonNode evidence);void heartbeat();}
    static boolean ready(){return ExtensionList.lookup(CsvDeliveryProcessor.class).size()==1;}
    static CsvDeliveryProcessor require(){var list=ExtensionList.lookup(CsvDeliveryProcessor.class);if(list.size()!=1)throw new DeliveryProblem(503,"PROCESSOR_NOT_READY");return list.get(0);}
}
