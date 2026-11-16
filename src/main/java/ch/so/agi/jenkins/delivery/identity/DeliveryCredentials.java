package ch.so.agi.jenkins.delivery.identity;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.security.ACL;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import ch.so.agi.jenkins.delivery.client.DeliveryProblem;

public final class DeliveryCredentials {
    public static final String OAUTH = "di-gretl-delivery";
    public static final String STORAGE_ID = "di-csv-storage-id";
    public static final String STORAGE_SECRET = "di-csv-storage-secret";
    public static final String CLICKHOUSE = "di-csv-clickhouse-loader";

    private DeliveryCredentials() {}

    public static String read(String id) {
        var credentials = CredentialsProvider.lookupCredentialsInItemGroup(
            StringCredentials.class, Jenkins.get(), ACL.SYSTEM2, List.of()).stream()
            .filter(credential -> id.equals(credential.getId()) && credential.getScope() == CredentialsScope.SYSTEM)
            .toList();
        if (credentials.size() != 1) throw DeliveryProblem.unavailable();
        String value = credentials.getFirst().getSecret().getPlainText();
        if (value.isBlank()) throw DeliveryProblem.unavailable();
        return value;
    }
}
