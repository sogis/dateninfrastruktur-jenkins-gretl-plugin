package ch.so.agi.jenkins.gretldatenportal;

import java.util.List;
import java.util.Map;

public final class WebhookConfiguration {
    private final boolean enabled;
    private final String credentialId;
    private final List<String> events;

    public WebhookConfiguration(boolean enabled, String credentialId, List<String> events) {
        this.enabled = enabled;
        this.credentialId = credentialId == null ? "" : credentialId;
        this.events = events == null ? List.of() : List.copyOf(events);
    }

    public static WebhookConfiguration disabled() {
        return new WebhookConfiguration(false, "", List.of());
    }

    public static WebhookConfiguration fromYaml(Map<String, Object> notifications) {
        Map<String, Object> webhook = YamlSupport.asMap(notifications.get("webhook"));
        if (webhook.isEmpty()) {
            return disabled();
        }
        return new WebhookConfiguration(
                YamlSupport.booleanValue(webhook, "enabled", false),
                YamlSupport.stringValue(webhook, "credentialId"),
                YamlSupport.asList(webhook.get("events")).stream().map(Object::toString).toList());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public List<String> getEvents() {
        return events;
    }

    public boolean isConfigured() {
        return enabled || !credentialId.isBlank() || !events.isEmpty();
    }
}
