package ch.so.agi.jenkins.delivery.runner;

import ch.so.agi.jenkins.delivery.client.DeliveryProblem;
import com.cloudbees.plugins.credentials.*;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import java.util.List;

/** Fixed SYSTEM-scoped credentials are read only by the trusted pipeline code. */
final class RunnerCredentials {
    enum Key {
        STORAGE_ID("di-csv-storage-id"),STORAGE_SECRET("di-csv-storage-secret"),CLICKHOUSE("di-csv-clickhouse-loader");
        final String id;Key(String id){this.id=id;}
    }
    private RunnerCredentials(){}
    static String read(Key key){
        var credentials=CredentialsProvider.lookupCredentialsInItemGroup(StringCredentials.class,Jenkins.get(),ACL.SYSTEM2,List.of()).stream()
            .filter(c->key.id.equals(c.getId())&&c.getScope()==CredentialsScope.SYSTEM).toList();
        if(credentials.size()!=1)throw DeliveryProblem.unavailable();
        var value=credentials.getFirst().getSecret().getPlainText();
        if(value.isEmpty()||value.length()>256||value.chars().anyMatch(Character::isISOControl))throw DeliveryProblem.unavailable();
        return value;
    }
}
