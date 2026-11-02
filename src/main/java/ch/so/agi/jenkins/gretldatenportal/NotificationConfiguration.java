package ch.so.agi.jenkins.gretldatenportal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NotificationConfiguration {
    public enum Mode {
        APPEND,
        OVERRIDE,
        DISABLED
    }

    private final boolean emailEnabled;
    private final Mode emailMode;
    private final List<String> emailRecipients;
    private final boolean onFailure;
    private final boolean onUnstable;
    private final boolean onSuccess;
    private final boolean onAborted;
    private final boolean includeBuildStarter;
    private final WebhookConfiguration webhookConfiguration;
    private final boolean configured;

    public NotificationConfiguration(
            boolean emailEnabled,
            Mode emailMode,
            List<String> emailRecipients,
            boolean onFailure,
            boolean onUnstable,
            boolean onSuccess,
            boolean onAborted,
            boolean includeBuildStarter) {
        this(
                emailEnabled,
                emailMode,
                emailRecipients,
                onFailure,
                onUnstable,
                onSuccess,
                onAborted,
                includeBuildStarter,
                WebhookConfiguration.disabled());
    }

    public NotificationConfiguration(
            boolean emailEnabled,
            Mode emailMode,
            List<String> emailRecipients,
            boolean onFailure,
            boolean onUnstable,
            boolean onSuccess,
            boolean onAborted,
            boolean includeBuildStarter,
            WebhookConfiguration webhookConfiguration) {
        this(
                emailEnabled,
                emailMode,
                emailRecipients,
                onFailure,
                onUnstable,
                onSuccess,
                onAborted,
                includeBuildStarter,
                webhookConfiguration,
                true);
    }

    private NotificationConfiguration(
            boolean emailEnabled,
            Mode emailMode,
            List<String> emailRecipients,
            boolean onFailure,
            boolean onUnstable,
            boolean onSuccess,
            boolean onAborted,
            boolean includeBuildStarter,
            WebhookConfiguration webhookConfiguration,
            boolean configured) {
        this.emailEnabled = emailEnabled;
        this.emailMode = emailMode == null ? Mode.APPEND : emailMode;
        this.emailRecipients = normalizeRecipients(emailRecipients);
        this.onFailure = onFailure;
        this.onUnstable = onUnstable;
        this.onSuccess = onSuccess;
        this.onAborted = onAborted;
        this.includeBuildStarter = includeBuildStarter;
        this.webhookConfiguration = webhookConfiguration == null
                ? WebhookConfiguration.disabled()
                : webhookConfiguration;
        this.configured = configured;
    }

    public static NotificationConfiguration empty() {
        return new NotificationConfiguration(
                false,
                Mode.APPEND,
                List.of(),
                true,
                true,
                false,
                false,
                false,
                WebhookConfiguration.disabled(),
                false);
    }

    public static NotificationConfiguration disabled() {
        return new NotificationConfiguration(false, Mode.DISABLED, List.of(), true, true, false, false, false);
    }

    public static NotificationConfiguration fromYaml(Map<String, Object> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return empty();
        }
        Map<String, Object> email = YamlSupport.asMap(notifications.get("email"));
        WebhookConfiguration webhookConfiguration = WebhookConfiguration.fromYaml(notifications);
        if (email.isEmpty()) {
            return new NotificationConfiguration(
                    false,
                    Mode.APPEND,
                    List.of(),
                    true,
                    true,
                    false,
                    false,
                    false,
                    webhookConfiguration,
                    true);
        }

        Mode mode = parseMode(YamlSupport.stringValue(email, "mode"));
        boolean enabled = mode != Mode.DISABLED && YamlSupport.booleanValue(email, "enabled", true);
        return new NotificationConfiguration(
                enabled,
                mode,
                YamlSupport.asList(email.get("recipients")).stream().map(Object::toString).toList(),
                YamlSupport.booleanValue(email, "onFailure", true),
                YamlSupport.booleanValue(email, "onUnstable", true),
                YamlSupport.booleanValue(email, "onSuccess", false),
                YamlSupport.booleanValue(email, "onAborted", false),
                YamlSupport.booleanValue(email, "includeBuildStarter", false),
                webhookConfiguration,
                true);
    }

    public NotificationConfiguration merge(NotificationConfiguration override) {
        if (override == null || !override.isConfigured()) {
            return this;
        }
        if (override.emailMode == Mode.DISABLED) {
            return new NotificationConfiguration(false, Mode.DISABLED, List.of(),
                    override.onFailure, override.onUnstable, override.onSuccess, override.onAborted,
                    override.includeBuildStarter,
                    chooseWebhook(override),
                    true);
        }
        List<String> recipients = new ArrayList<>();
        if (override.emailMode == Mode.OVERRIDE) {
            recipients.addAll(override.emailRecipients);
        } else {
            recipients.addAll(emailRecipients);
            recipients.addAll(override.emailRecipients);
        }
        return new NotificationConfiguration(
                emailEnabled || override.emailEnabled,
                override.emailMode,
                recipients,
                override.onFailure,
                override.onUnstable,
                override.onSuccess,
                override.onAborted,
                includeBuildStarter || override.includeBuildStarter,
                chooseWebhook(override),
                true);
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public Mode getEmailMode() {
        return emailMode;
    }

    public List<String> getEmailRecipients() {
        return emailRecipients;
    }

    public String getEmailRecipientsCsv() {
        return String.join(",", emailRecipients);
    }

    public boolean isOnFailure() {
        return onFailure;
    }

    public boolean isOnUnstable() {
        return onUnstable;
    }

    public boolean isOnSuccess() {
        return onSuccess;
    }

    public boolean isOnAborted() {
        return onAborted;
    }

    public boolean isIncludeBuildStarter() {
        return includeBuildStarter;
    }

    public WebhookConfiguration getWebhookConfiguration() {
        return webhookConfiguration;
    }

    public boolean isConfigured() {
        return configured;
    }

    private WebhookConfiguration chooseWebhook(NotificationConfiguration override) {
        if (override.webhookConfiguration != null && override.webhookConfiguration.isConfigured()) {
            return override.webhookConfiguration;
        }
        return webhookConfiguration;
    }

    private static Mode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return Mode.APPEND;
        }
        return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static List<String> normalizeRecipients(List<String> recipients) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String recipient : recipients == null ? List.<String>of() : recipients) {
            String value = recipient == null ? "" : recipient.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }
}
