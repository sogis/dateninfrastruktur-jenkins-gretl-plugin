package ch.so.agi.jenkins.gretldatenportal;

import java.util.Locale;

public enum ParameterType {
    STRING,
    TEXT,
    CHOICE,
    BOOLEAN,
    FILE,
    DATE,
    URL;

    public static ParameterType fromId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Parameter type must not be blank.");
        }
        return ParameterType.valueOf(id.trim().toUpperCase(Locale.ROOT));
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
