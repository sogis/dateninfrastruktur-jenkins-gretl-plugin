package ch.so.agi.jenkins.gretldatenportal;

enum TopicRepositoryMode {
    MANAGED_GIT("managed-git"),
    WORKING_TREE("working-tree");

    private final String value;

    TopicRepositoryMode(String value) {
        this.value = value;
    }

    String getValue() {
        return value;
    }

    static TopicRepositoryMode parse(String value) {
        if (value == null || value.isBlank()) {
            return MANAGED_GIT;
        }
        for (TopicRepositoryMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "Unknown topic repository mode '" + value + "'. Use managed-git or working-tree.");
    }
}
