package ch.so.agi.jenkins.delivery.client;

import ch.so.agi.jenkins.delivery.config.DeliveryPluginConfiguration;
import ch.so.agi.jenkins.delivery.identity.JenkinsOidcActorResolver.UserTokenContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

public final class DeliveryApi {
    public static final JsonMapper JSON = new JsonMapper();

    public record Eligible(UUID datasetId, String title, UUID issueId, String issueKey, String allowedModes) {
        public List<String> modes() {
            return switch (allowedModes) {
                case "replace_only" -> List.of("replace");
                case "append_only" -> List.of("append");
                case "replace_and_append" -> List.of("replace", "append");
                default -> List.of();
            };
        }
    }

    public record Create(UUID datasetId, UUID issueId, String mode, String originalFileName,
                         long inputSize, String inputSha256) {}
    public record State(UUID id, String status, UUID acceptedVersionId, Long inputSize) {}

    public List<Eligible> eligible(UserTokenContext user, String search) {
        return Arrays.asList(JSON.treeToValue(call("GET", "eligible?search="
            + URLEncoder.encode(search, StandardCharsets.UTF_8), user.accessToken(), null), Eligible[].class));
    }

    public void create(UserTokenContext user, UUID id, Create request) {
        call("PUT", id.toString(), user.accessToken(), request);
    }

    public State state(UserTokenContext user, UUID id) {
        var state = call("GET", id.toString(), user.accessToken(), null);
        return new State(id, state.path("status").asString(),
            state.path("acceptedVersionId").isString() ? UUID.fromString(state.path("acceptedVersionId").asString()) : null,
            state.path("inputSize").isNumber() ? state.path("inputSize").asLong() : null);
    }

    public void cancel(UserTokenContext user, UUID id) {
        call("POST", id + "/cancel", user.accessToken(), null);
    }

    public void fail(String token, UUID id) {
        call("POST", id + "/fail", token, Map.of("message", "Technischer Fehler im GRETL-Lauf."));
    }

    private static tools.jackson.databind.JsonNode call(String method, String path, String token, Object body) {
        var uri = DeliveryPluginConfiguration.get().backendBaseUri().resolve(path);
        var result = BoundedHttp.request(uri, method,
            Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json", "Accept", "application/json"),
            body == null ? null : JSON.writeValueAsBytes(body));
        return switch (result.status()) {
            case 200 -> JSON.readTree(result.body());
            case 400 -> throw DeliveryProblem.invalid();
            case 401 -> throw DeliveryProblem.login();
            case 403 -> throw new DeliveryProblem(403, "NOT_AUTHORIZED");
            case 404 -> throw new DeliveryProblem(404, "NOT_FOUND");
            case 409 -> throw new DeliveryProblem(409, "INVALID_STATE");
            default -> throw DeliveryProblem.unavailable();
        };
    }
}
