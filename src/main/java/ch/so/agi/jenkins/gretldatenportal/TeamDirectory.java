package ch.so.agi.jenkins.gretldatenportal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TeamDirectory {
    private final Map<String, Set<String>> usersByTeam;

    public TeamDirectory(Map<String, Set<String>> usersByTeam) {
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        if (usersByTeam != null) {
            usersByTeam.forEach((team, users) -> {
                if (team != null && !team.isBlank()) {
                    LinkedHashSet<String> normalizedUsers = new LinkedHashSet<>();
                    if (users != null) {
                        users.stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(user -> !user.isBlank())
                                .forEach(normalizedUsers::add);
                    }
                    normalized.put(team.trim(), Collections.unmodifiableSet(normalizedUsers));
                }
            });
        }
        this.usersByTeam = Collections.unmodifiableMap(normalized);
    }

    public static TeamDirectory empty() {
        return new TeamDirectory(Map.of());
    }

    public boolean hasTeam(String team) {
        return team != null && usersByTeam.containsKey(team.trim());
    }

    public Set<String> getUsers(String team) {
        if (team == null) {
            return Set.of();
        }
        return usersByTeam.getOrDefault(team.trim(), Set.of());
    }

    public Map<String, Set<String>> getUsersByTeam() {
        return usersByTeam;
    }
}
