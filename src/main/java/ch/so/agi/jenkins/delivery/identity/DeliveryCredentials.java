package ch.so.agi.jenkins.delivery.identity;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.security.ACL;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import ch.so.agi.jenkins.delivery.client.DeliveryProblem;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;

final class DeliveryCredentials {
    private DeliveryCredentials(){}
    static String read(String id){
        if(!List.of(DeliveryPluginConfiguration.PLUGIN_CREDENTIAL,DeliveryPluginConfiguration.SERVICE_CREDENTIAL).contains(id))throw DeliveryProblem.unavailable();
        var found=CredentialsProvider.lookupCredentialsInItemGroup(StringCredentials.class,Jenkins.get(),ACL.SYSTEM2,List.of()).stream().filter(c->id.equals(c.getId()) && c.getScope()==CredentialsScope.SYSTEM).toList();
        if(found.size()!=1)throw DeliveryProblem.unavailable();String value=found.getFirst().getSecret().getPlainText();if(value.length()<32)throw DeliveryProblem.unavailable();return value;
    }
    public static String pluginSecret(){return read(DeliveryPluginConfiguration.PLUGIN_CREDENTIAL);}
}
