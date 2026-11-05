package ch.so.agi.jenkins.delivery.identity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;

/** Short-lived authentication cache only. No Fachrecht or user token is retained here. */
public final class ServiceTokenClient {
    private static final ServiceTokenClient INSTANCE=new ServiceTokenClient();
    private String token;private Instant until=Instant.EPOCH;
    public static ServiceTokenClient get(){return INSTANCE;}
    public synchronized String acquire(){
        if(token!=null && Instant.now().isBefore(until))return token;
        var config=DeliveryPluginConfiguration.get();String secret=DeliveryCredentials.read(DeliveryPluginConfiguration.SERVICE_CREDENTIAL);
        var response=BoundedHttp.request(config.serviceTokenUri(),"POST",Map.of("Content-Type","application/x-www-form-urlencoded","Accept","application/json"),BoundedHttp.utf8("grant_type=client_credentials&client_id=di-ingestion-executor&client_secret="+URLEncoder.encode(secret,StandardCharsets.UTF_8)));
        if(response.status()!=200)throw DeliveryProblem.unavailable();
        try{var json=Wire.JSON.readTree(response.body());String candidate=json.path("access_token").asString("");long seconds=json.path("expires_in").asLong(0);
            if(candidate.isBlank() || candidate.length()>16384 || seconds<=15 || !"Bearer".equals(json.path("token_type").asString("")))throw DeliveryProblem.unavailable();
            token=candidate;until=Instant.now().plusSeconds(Math.min(180,seconds-10));return token;
        }catch(RuntimeException e){throw DeliveryProblem.unavailable();}
    }
    public synchronized void invalidateOn401(){token=null;until=Instant.EPOCH;}
    public static String pluginSecret(){return DeliveryCredentials.pluginSecret();}
}
