package ch.so.agi.jenkins.gretldatenportal;

import java.util.List;
import java.util.Objects;

public final class GuiFieldDefinition {
    private final String id;
    private final String label;
    private final String description;
    private final ParameterType type;
    private final boolean typeSpecified;
    private final boolean required;
    private final boolean requiredSpecified;
    private final String defaultValue;
    private final boolean readOnly;
    private final List<String> values;
    private final ChoiceSource source;
    private final Condition visibleIf;
    private final Condition requiredIf;
    private final String uploadMode;
    private final List<String> allowedExtensions;
    private final Integer maxSizeMb;

    public GuiFieldDefinition(
            String id,
            String label,
            String description,
            ParameterType type,
            boolean required,
            String defaultValue,
            boolean readOnly,
            List<String> values,
            ChoiceSource source,
            Condition visibleIf,
            Condition requiredIf,
            String uploadMode,
            List<String> allowedExtensions,
            Integer maxSizeMb) {
        this(
                id,
                label,
                description,
                type,
                true,
                required,
                true,
                defaultValue,
                readOnly,
                values,
                source,
                visibleIf,
                requiredIf,
                uploadMode,
                allowedExtensions,
                maxSizeMb);
    }

    public GuiFieldDefinition(
            String id,
            String label,
            String description,
            ParameterType type,
            boolean typeSpecified,
            boolean required,
            boolean requiredSpecified,
            String defaultValue,
            boolean readOnly,
            List<String> values,
            ChoiceSource source,
            Condition visibleIf,
            Condition requiredIf,
            String uploadMode,
            List<String> allowedExtensions,
            Integer maxSizeMb) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = label == null ? id : label;
        this.description = description == null ? "" : description;
        this.type = Objects.requireNonNull(type, "type");
        this.typeSpecified = typeSpecified;
        this.required = required;
        this.requiredSpecified = requiredSpecified;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
        this.readOnly = readOnly;
        this.values = values == null ? List.of() : List.copyOf(values);
        this.source = source;
        this.visibleIf = visibleIf;
        this.requiredIf = requiredIf;
        this.uploadMode = uploadMode == null ? "" : uploadMode;
        this.allowedExtensions = allowedExtensions == null ? List.of() : List.copyOf(allowedExtensions);
        this.maxSizeMb = maxSizeMb;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public ParameterType getType() {
        return type;
    }

    public boolean isTypeSpecified() {
        return typeSpecified;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isRequiredSpecified() {
        return requiredSpecified;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public List<String> getValues() {
        return values;
    }

    public ChoiceSource getSource() {
        return source;
    }

    public Condition getVisibleIf() {
        return visibleIf;
    }

    public Condition getRequiredIf() {
        return requiredIf;
    }

    public String getUploadMode() {
        return uploadMode;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public Integer getMaxSizeMb() {
        return maxSizeMb;
    }
}
