package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TeamDirectoryParser {
    public TeamDirectory parse(Path yamlFile) throws IOException {
        Map<String, Object> root = YamlSupport.loadMap(yamlFile);
        Object teamsValue = root.get("teams");
        if (!(teamsValue instanceof Map<?, ?> teamsMap)) {
            throw new IOException("teams must be a mapping.");
        }

        Map<String, Set<String>> usersByTeam = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : teamsMap.entrySet()) {
            String teamId = stringKey(entry.getKey(), "team id");
            if (usersByTeam.containsKey(teamId)) {
                throw new IOException("Duplicate team: " + teamId);
            }
            if (!(entry.getValue() instanceof Map<?, ?> teamMap)) {
                throw new IOException("Team '" + teamId + "' must be a mapping.");
            }

            Object usersValue = teamMap.get("users");
            if (!(usersValue instanceof List<?> usersList)) {
                throw new IOException("Team '" + teamId + "' must contain a users list.");
            }
            Set<String> users = parseUsers(teamId, usersList);
            if (users.isEmpty()) {
                throw new IOException("Team '" + teamId + "' must contain at least one user.");
            }
            usersByTeam.put(teamId, users);
        }
        return new TeamDirectory(usersByTeam);
    }

    private Set<String> parseUsers(String teamId, List<?> values) throws IOException {
        LinkedHashSet<String> users = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null || value.toString().isBlank()) {
                throw new IOException("Team '" + teamId + "' contains an empty user id.");
            }
            users.add(value.toString().trim());
        }
        return users;
    }

    private String stringKey(Object value, String description) throws IOException {
        if (value == null || value.toString().isBlank()) {
            throw new IOException("A " + description + " must not be blank.");
        }
        return value.toString().trim();
    }
}
