package ch.so.agi.jenkins.gretldatenportal;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ScanResult {
    private final Path repositoryPath;
    private final List<OrganizationUnit> organizations;
    private final List<ValidationMessage> messages;
    private final TeamDirectory teamDirectory;

    public ScanResult(Path repositoryPath, List<OrganizationUnit> organizations, List<ValidationMessage> messages) {
        this(repositoryPath, organizations, messages, TeamDirectory.empty());
    }

    public ScanResult(
            Path repositoryPath,
            List<OrganizationUnit> organizations,
            List<ValidationMessage> messages,
            TeamDirectory teamDirectory) {
        this.repositoryPath = repositoryPath;
        this.organizations = List.copyOf(Objects.requireNonNull(organizations, "organizations"));
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        this.teamDirectory = teamDirectory == null ? TeamDirectory.empty() : teamDirectory;
    }

    public static ScanResult empty(Path repositoryPath, ValidationMessage message) {
        return new ScanResult(repositoryPath, List.of(), List.of(message));
    }

    public Path getRepositoryPath() {
        return repositoryPath;
    }

    public String getRepositoryPathString() {
        return repositoryPath == null ? "" : repositoryPath.toString();
    }

    public List<OrganizationUnit> getOrganizations() {
        return organizations;
    }

    public int getOrganizationCount() {
        return organizations.size();
    }

    public List<ValidationMessage> getMessages() {
        return messages;
    }

    public TeamDirectory getTeamDirectory() {
        return teamDirectory;
    }

    public boolean hasMessages() {
        return !messages.isEmpty();
    }

    public boolean hasErrors() {
        return messages.stream().anyMatch(ValidationMessage::isError);
    }
}
