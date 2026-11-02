package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationConfigurationTest {
    @Test
    void normalizesAndDedupeRecipients() {
        NotificationConfiguration configuration = new NotificationConfiguration(
                true,
                NotificationConfiguration.Mode.APPEND,
                List.of("DATA@EXAMPLE.INVALID", "data@example.invalid", " fach@example.invalid "),
                true,
                true,
                false,
                false,
                true);

        assertEquals(List.of("data@example.invalid", "fach@example.invalid"), configuration.getEmailRecipients());
        assertEquals("data@example.invalid,fach@example.invalid", configuration.getEmailRecipientsCsv());
    }

    @Test
    void rendersEmailExtPostBlockWhenEnabled() {
        NotificationConfiguration configuration = new NotificationConfiguration(
                true,
                NotificationConfiguration.Mode.APPEND,
                List.of("data@example.invalid"),
                true,
                true,
                false,
                false,
                false);

        String block = new EmailNotificationService().renderPostBlock(configuration);

        assertTrue(block.contains("emailext"));
        assertTrue(block.contains("data@example.invalid"));
        assertFalse(block.isBlank());
    }

    @Test
    void absentNotificationsDoNotDisableSharedDefaults() {
        NotificationConfiguration shared = new NotificationConfiguration(
                true,
                NotificationConfiguration.Mode.APPEND,
                List.of("shared@example.invalid"),
                true,
                true,
                false,
                false,
                false);

        NotificationConfiguration merged = shared.merge(NotificationConfiguration.fromYaml(java.util.Map.of()));

        assertTrue(merged.isEmailEnabled());
        assertEquals(List.of("shared@example.invalid"), merged.getEmailRecipients());
    }

    @Test
    void explicitDisabledNotificationsOverrideSharedDefaults() {
        NotificationConfiguration shared = new NotificationConfiguration(
                true,
                NotificationConfiguration.Mode.APPEND,
                List.of("shared@example.invalid"),
                true,
                true,
                false,
                false,
                false);

        NotificationConfiguration override = NotificationConfiguration.fromYaml(java.util.Map.of(
                "email", java.util.Map.of("mode", "disabled")));
        NotificationConfiguration merged = shared.merge(override);

        assertFalse(merged.isEmailEnabled());
        assertTrue(merged.getEmailRecipients().isEmpty());
    }
}
