package ch.so.agi.jenkins.gretldatenportal;

import java.util.Objects;

public final class Condition {
    private final String parameter;
    private final String equals;

    public Condition(String parameter, String equals) {
        this.parameter = Objects.requireNonNull(parameter, "parameter");
        this.equals = Objects.requireNonNull(equals, "equals");
    }

    public String getParameter() {
        return parameter;
    }

    public String getEquals() {
        return equals;
    }
}
