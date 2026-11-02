package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class RepositoryDefaultsParser {

    public RepositoryDefaults parse(Path yamlFile, Path sharedPath) throws IOException {
        Map<String, Object> root = YamlSupport.loadMap(yamlFile);
        rejectGuiConfiguration(root);
        Map<String, Object> execution = YamlSupport.asMap(root.get("execution"));
        return new RepositoryDefaults(
                sharedPath,
                true,
                YamlSupport.stringValue(execution, "gradleTask"),
                YamlSupport.integerValue(execution, "timeoutMinutes"),
                YamlSupport.stringValue(execution, "jenkinsfile"),
                GuiDefinition.empty(),
                NotificationConfiguration.fromYaml(YamlSupport.asMap(root.get("notifications"))));
    }

    private void rejectGuiConfiguration(Map<String, Object> root) throws IOException {
        if (!YamlSupport.asMap(root.get("gui")).isEmpty()) {
            throw new IOException("gretl-datenportal-defaults.yaml contains unsupported gui configuration.");
        }
    }
}
