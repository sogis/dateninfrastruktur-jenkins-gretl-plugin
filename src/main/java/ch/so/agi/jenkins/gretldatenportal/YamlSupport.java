package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

final class YamlSupport {
    private YamlSupport() {
    }

    static Map<String, Object> loadMap(Path yamlFile) throws IOException {
        Object value = new Yaml().load(Files.readString(yamlFile, StandardCharsets.UTF_8));
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IOException("YAML root must be a mapping.");
        }
        return castMap(map);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString().trim();
    }

    static boolean booleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.toString());
    }

    static Integer integerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
