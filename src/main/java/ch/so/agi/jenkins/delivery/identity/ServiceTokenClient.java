package ch.so.agi.jenkins.delivery.identity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import ch.so.agi.jenkins.delivery.client.*;
import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;

public final class ServiceTokenClient {
    private ServiceTokenClient() {}

    public static String acquire() {
        var secret = DeliveryCredentials.read(DeliveryCredentials.OAUTH);
        var response = BoundedHttp.request(DeliveryPluginConfiguration.get().serviceTokenUri(), "POST",
            Map.of("Content-Type", "application/x-www-form-urlencoded", "Accept", "application/json"),
            BoundedHttp.utf8("grant_type=client_credentials&client_id=gretl-delivery&client_secret="
                + URLEncoder.encode(secret, StandardCharsets.UTF_8)));
        if (response.status() != 200) throw DeliveryProblem.unavailable();
        var json = DeliveryApi.JSON.readTree(response.body());
        String token = json.path("access_token").asString("");
        if (token.isBlank() || token.length() > 16384 || !"Bearer".equals(json.path("token_type").asString(""))) {
            throw DeliveryProblem.unavailable();
        }
        return token;
    }
}
