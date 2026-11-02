package ch.so.agi.jenkins.gretldatenportal;

import java.util.List;
import java.util.Objects;

public final class GuiDefinition {
    private final List<GuiFieldDefinition> fields;

    public static GuiDefinition empty() {
        return new GuiDefinition(List.of());
    }

    public GuiDefinition(List<GuiFieldDefinition> fields) {
        this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    }

    public List<GuiFieldDefinition> getFields() {
        return fields;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }
}
