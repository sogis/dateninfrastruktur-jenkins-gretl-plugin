package ch.so.agi.jenkins.gretldatenportal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuiDefinitionMerger {
    public GuiDefinition merge(GuiDefinition base, GuiDefinition... overrides) {
        Map<String, GuiFieldDefinition> fieldsById = new LinkedHashMap<>();
        for (GuiFieldDefinition field : safeFields(base)) {
            fieldsById.put(field.getId(), field);
        }
        if (overrides != null) {
            for (GuiDefinition override : overrides) {
                for (GuiFieldDefinition field : safeFields(override)) {
                    fieldsById.put(field.getId(), mergeField(fieldsById.get(field.getId()), field));
                }
            }
        }
        return new GuiDefinition(List.copyOf(fieldsById.values()));
    }

    private List<GuiFieldDefinition> safeFields(GuiDefinition definition) {
        return definition == null ? List.of() : definition.getFields();
    }

    private GuiFieldDefinition mergeField(GuiFieldDefinition base, GuiFieldDefinition override) {
        if (base == null) {
            return override;
        }
        return new GuiFieldDefinition(
                override.getId(),
                choose(override.getLabel(), base.getLabel(), override.getId()),
                choose(override.getDescription(), base.getDescription(), ""),
                override.isTypeSpecified() ? override.getType() : base.getType(),
                override.isRequiredSpecified() ? override.isRequired() : base.isRequired(),
                choose(override.getDefaultValue(), base.getDefaultValue(), ""),
                override.isReadOnly() || base.isReadOnly(),
                override.getValues().isEmpty() ? base.getValues() : override.getValues(),
                override.getSource() == null ? base.getSource() : override.getSource(),
                override.getVisibleIf() == null ? base.getVisibleIf() : override.getVisibleIf(),
                override.getRequiredIf() == null ? base.getRequiredIf() : override.getRequiredIf(),
                choose(override.getUploadMode(), base.getUploadMode(), ""),
                override.getAllowedExtensions().isEmpty() ? base.getAllowedExtensions() : override.getAllowedExtensions(),
                override.getMaxSizeMb() == null ? base.getMaxSizeMb() : override.getMaxSizeMb());
    }

    private String choose(String override, String base, String emptyValue) {
        if (override != null && !override.isBlank() && !override.equals(emptyValue)) {
            return override;
        }
        return base == null ? emptyValue : base;
    }
}
