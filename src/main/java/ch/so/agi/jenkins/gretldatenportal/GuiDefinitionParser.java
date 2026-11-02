package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GuiDefinitionParser {
    public GuiDefinition parse(Path yamlFile) throws IOException {
        return parse(YamlSupport.loadMap(yamlFile));
    }

    public GuiDefinition parse(Map<String, Object> root) {
        Map<String, Object> gui = YamlSupport.asMap(root.get("gui"));
        List<GuiFieldDefinition> fields = new ArrayList<>();
        for (Object fieldValue : YamlSupport.asList(gui.get("fields"))) {
            Map<String, Object> field = YamlSupport.asMap(fieldValue);
            if (!field.isEmpty()) {
                fields.add(parseField(field));
            }
        }
        return new GuiDefinition(fields);
    }

    private GuiFieldDefinition parseField(Map<String, Object> field) {
        String id = YamlSupport.stringValue(field, "id");
        ParameterType type = parseType(field);
        return new GuiFieldDefinition(
                id,
                YamlSupport.stringValue(field, "label"),
                YamlSupport.stringValue(field, "description"),
                type,
                field.containsKey("type"),
                YamlSupport.booleanValue(field, "required", false),
                field.containsKey("required"),
                YamlSupport.stringValue(field, "defaultValue"),
                YamlSupport.booleanValue(field, "readOnly", false),
                stringList(field.get("values")),
                parseChoiceSource(field.get("source")),
                parseCondition(field.get("visibleIf")),
                parseCondition(field.get("requiredIf")),
                YamlSupport.stringValue(field, "uploadMode"),
                stringList(field.get("allowedExtensions")),
                YamlSupport.integerValue(field, "maxSizeMb"));
    }

    private ParameterType parseType(Map<String, Object> field) {
        String type = YamlSupport.stringValue(field, "type");
        if (type.isBlank()) {
            return ParameterType.STRING;
        }
        return ParameterType.fromId(type);
    }

    private ChoiceSource parseChoiceSource(Object value) {
        Map<String, Object> source = YamlSupport.asMap(value);
        if (source.isEmpty()) {
            return null;
        }
        return new ChoiceSource(
                YamlSupport.stringValue(source, "type"),
                YamlSupport.stringValue(source, "path"));
    }

    private Condition parseCondition(Object value) {
        Map<String, Object> condition = YamlSupport.asMap(value);
        if (condition.isEmpty()) {
            return null;
        }
        return new Condition(
                YamlSupport.stringValue(condition, "parameter"),
                YamlSupport.stringValue(condition, "equals"));
    }

    private List<String> stringList(Object value) {
        return YamlSupport.asList(value).stream()
                .map(Object::toString)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
