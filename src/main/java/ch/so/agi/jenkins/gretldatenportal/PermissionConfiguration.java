package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;

public final class PermissionConfiguration {
    private final List<String> readUsers;
    private final List<String> buildUsers;

    public PermissionConfiguration(List<String> readUsers, List<String> buildUsers) {
        this.readUsers = normalize(readUsers);
        this.buildUsers = normalize(buildUsers);
    }

    public static PermissionConfiguration empty() {
        return new PermissionConfiguration(List.of(), List.of());
    }

    public static PermissionConfiguration fromYaml(Map<String, Object> root, TeamDirectory teamDirectory)
            throws IOException {
        if (root == null || root.isEmpty()) {
            return empty();
        }
        TeamDirectory directory = teamDirectory == null ? TeamDirectory.empty() : teamDirectory;
        return new PermissionConfiguration(
                resolveTeams(root.get("read"), "permissions.read", directory),
                resolveTeams(root.get("build"), "permissions.build", directory));
    }

    public boolean hasReadRestrictions() {
        return !readUsers.isEmpty();
    }

    public boolean hasBuildRestrictions() {
        return !buildUsers.isEmpty();
    }

    public boolean canRead(Authentication authentication) {
        return !hasReadRestrictions() || hasAny(authentication, readUsers) || hasAny(authentication, buildUsers);
    }

    public boolean canBuild(Authentication authentication) {
        return !hasBuildRestrictions() || hasAny(authentication, buildUsers);
    }

    public List<String> getReadUsers() {
        LinkedHashSet<String> users = new LinkedHashSet<>(readUsers);
        users.addAll(buildUsers);
        return List.copyOf(users);
    }

    public List<String> getBuildUsers() {
        return buildUsers;
    }

    private static List<String> resolveTeams(Object value, String path, TeamDirectory teamDirectory) throws IOException {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> entries)) {
            throw new IOException(path + " must be a list of team mappings.");
        }

        LinkedHashSet<String> users = new LinkedHashSet<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IOException(path + " entries must contain exactly one team mapping.");
            }
            if (map.size() != 1 || !map.containsKey("team")) {
                throw new IOException(path + " entries must contain exactly one team key.");
            }
            Object teamValue = map.get("team");
            if (teamValue == null || teamValue.toString().isBlank()) {
                throw new IOException(path + " contains an empty team id.");
            }
            String team = teamValue.toString().trim();
            if (!teamDirectory.hasTeam(team)) {
                throw new IOException(path + " references unknown team '" + team + "'.");
            }
            users.addAll(teamDirectory.getUsers(team));
        }
        return List.copyOf(users);
    }

    private static boolean hasAny(Authentication authentication, List<String> users) {
        return authentication != null
                && users.contains(authentication.getName());
    }

    private static List<String> normalize(List<String> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String user : users) {
            if (user != null && !user.isBlank()) {
                normalized.add(user.trim());
            }
        }
        return List.copyOf(normalized);
    }
}
