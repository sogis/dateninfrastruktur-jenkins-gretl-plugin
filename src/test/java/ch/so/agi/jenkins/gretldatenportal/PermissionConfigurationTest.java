package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class PermissionConfigurationTest {
    @Test
    void fallsBackToAllowWhenNoGroupsAreConfigured() {
        PermissionConfiguration configuration = PermissionConfiguration.empty();

        assertTrue(configuration.canRead(authentication("some-user")));
        assertTrue(configuration.canBuild(authentication("some-user")));
    }

    @Test
    void checksReadAndBuildUsers() {
        PermissionConfiguration configuration = new PermissionConfiguration(
                List.of("read-user"),
                List.of("build-user"));

        assertTrue(configuration.canRead(authentication("read-user")));
        assertFalse(configuration.canBuild(authentication("read-user")));
        assertTrue(configuration.canRead(authentication("build-user")));
        assertTrue(configuration.canBuild(authentication("build-user")));
    }

    @Test
    void parsesYamlTeams() throws IOException {
        TeamDirectory directory = new TeamDirectory(Map.of(
                "datenportal-read", Set.of("read-user", "duplicate-user"),
                "datenportal-build", Set.of("build-user", "duplicate-user")));
        PermissionConfiguration configuration = PermissionConfiguration.fromYaml(Map.of(
                "read", List.of(Map.of("team", "datenportal-read"), Map.of("team", "datenportal-read")),
                "build", List.of(Map.of("team", "datenportal-build"))), directory);

        assertTrue(configuration.hasReadRestrictions());
        assertTrue(configuration.hasBuildRestrictions());
        assertTrue(configuration.getReadUsers().contains("read-user"));
        assertTrue(configuration.getReadUsers().contains("build-user"));
        assertTrue(configuration.getReadUsers().contains("duplicate-user"));
        assertEquals(3, configuration.getReadUsers().size());
        assertTrue(configuration.getBuildUsers().contains("build-user"));
        assertTrue(configuration.getBuildUsers().contains("duplicate-user"));
    }

    @Test
    void rejectsUnknownTeams() {
        assertThrows(IOException.class, () -> PermissionConfiguration.fromYaml(Map.of(
                "read", List.of(Map.of("team", "missing-team"))),
                TeamDirectory.empty()));
    }

    private Authentication authentication(String authority) {
        return new UsernamePasswordAuthenticationToken(
                authority,
                "n/a",
                List.of(new SimpleGrantedAuthority("irrelevant-authority")));
    }
}
