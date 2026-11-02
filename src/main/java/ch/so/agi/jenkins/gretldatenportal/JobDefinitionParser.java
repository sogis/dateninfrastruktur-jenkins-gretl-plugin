package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class JobDefinitionParser {

    public OrganizationJobConfiguration parse(Path yamlFile, String fallbackId) throws IOException {
        return parse(yamlFile, fallbackId, TeamDirectory.empty());
    }

    public OrganizationJobConfiguration parse(
            Path yamlFile,
            String fallbackId,
            TeamDirectory teamDirectory) throws IOException {
        Map<String, Object> root = YamlSupport.loadMap(yamlFile);
        rejectGuiConfiguration(root, "gretl-datenportal-job.yaml");
        Map<String, Object> execution = YamlSupport.asMap(root.get("execution"));
        Integer configuredTimeout = YamlSupport.integerValue(execution, "timeoutMinutes");
        int timeout = valueOrDefault(configuredTimeout, 60);

        JobDefinition jobDefinition = new JobDefinition(
                fallbackId,
                YamlSupport.stringValue(root, "title"),
                YamlSupport.stringValue(root, "description"),
                YamlSupport.stringValue(execution, "jobName"),
                YamlSupport.stringValue(execution, "gradleTask"),
                YamlSupport.stringValue(execution, "jenkinsfile"),
                timeout,
                hasNonBlankValue(execution, "jobName"),
                hasNonBlankValue(execution, "gradleTask"),
                hasNonBlankValue(execution, "jenkinsfile"),
                configuredTimeout != null);

        NotificationConfiguration notificationConfiguration =
                NotificationConfiguration.fromYaml(YamlSupport.asMap(root.get("notifications")));
        PermissionConfiguration permissionConfiguration =
                PermissionConfiguration.fromYaml(YamlSupport.asMap(root.get("permissions")), teamDirectory);

        return new OrganizationJobConfiguration(
                jobDefinition,
                GuiDefinition.empty(),
                notificationConfiguration,
                permissionConfiguration);
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private boolean hasNonBlankValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null && !value.toString().isBlank();
    }

    private void rejectGuiConfiguration(Map<String, Object> root, String sourceName) throws IOException {
        if (!YamlSupport.asMap(root.get("gui")).isEmpty()) {
            throw new IOException(sourceName + " contains unsupported gui configuration.");
        }
    }
}
