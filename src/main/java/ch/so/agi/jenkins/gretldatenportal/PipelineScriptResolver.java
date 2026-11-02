package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PipelineScriptResolver {
    private static final String JENKINSFILE_NAME = "Jenkinsfile";

    private final PipelineJobRenderer pipelineJobRenderer;

    public PipelineScriptResolver() {
        this(new PipelineJobRenderer());
    }

    public PipelineScriptResolver(PipelineJobRenderer pipelineJobRenderer) {
        this.pipelineJobRenderer = pipelineJobRenderer;
    }

    public String resolve(OrganizationUnit organization) throws IOException {
        Path explicit = configuredOrganizationJenkinsfile(organization);
        if (explicit != null) {
            return Files.readString(explicit, StandardCharsets.UTF_8);
        }

        Path implicit = organization.getPath().resolve(JENKINSFILE_NAME);
        if (Files.isRegularFile(implicit)) {
            return Files.readString(implicit, StandardCharsets.UTF_8);
        }

        Path sharedExplicit = configuredSharedJenkinsfile(organization.getRepositoryDefaults());
        if (sharedExplicit != null) {
            return Files.readString(sharedExplicit, StandardCharsets.UTF_8);
        }

        Path sharedImplicit = implicitSharedJenkinsfile(organization);
        if (sharedImplicit != null) {
            return pipelineJobRenderer.renderTemplate(
                    Files.readString(sharedImplicit, StandardCharsets.UTF_8),
                    organization,
                    organization.getNotificationConfiguration());
        }

        throw new IOException("Shared default Jenkinsfile not found: " + defaultSharedJenkinsfilePath(organization));
    }

    private Path configuredOrganizationJenkinsfile(OrganizationUnit organization) throws IOException {
        String configured = organization.getJobDefinition().getJenkinsfile();
        return configuredJenkinsfile(organization.getPath(), configured);
    }

    private Path configuredSharedJenkinsfile(RepositoryDefaults repositoryDefaults) throws IOException {
        if (repositoryDefaults == null || !repositoryDefaults.hasSharedPath()) {
            return null;
        }
        return configuredJenkinsfile(repositoryDefaults.getSharedPath(), repositoryDefaults.getJenkinsfile());
    }

    private Path implicitSharedJenkinsfile(OrganizationUnit organization) throws IOException {
        Path implicit = defaultSharedJenkinsfilePath(organization);
        return Files.isRegularFile(implicit) ? implicit : null;
    }

    private Path defaultSharedJenkinsfilePath(OrganizationUnit organization) throws IOException {
        RepositoryDefaults repositoryDefaults = organization.getRepositoryDefaults();
        if (repositoryDefaults != null && repositoryDefaults.hasSharedPath()) {
            return repositoryDefaults.getSharedPath().resolve(JENKINSFILE_NAME);
        }

        Path repositoryPath = organization.getPath().toAbsolutePath().normalize().getParent();
        if (repositoryPath == null) {
            throw new IOException("Could not determine topic repository root for organization: " + organization.getPath());
        }
        return repositoryPath.resolve(TopicRepositoryScanner.SHARED_DIR).resolve(JENKINSFILE_NAME);
    }

    private Path configuredJenkinsfile(Path basePath, String configured) throws IOException {
        if (configured == null || configured.isBlank()) {
            return null;
        }

        Path configuredPath = Path.of(configured);
        if (configuredPath.isAbsolute()) {
            throw new IOException("Configured Jenkinsfile path must be relative: " + configured);
        }

        Path normalizedBasePath = basePath.toAbsolutePath().normalize();
        Path candidate = normalizedBasePath.resolve(configuredPath).normalize();
        if (!candidate.startsWith(normalizedBasePath)) {
            throw new IOException("Configured Jenkinsfile path must stay inside its base folder: " + configured);
        }
        if (!Files.isRegularFile(candidate)) {
            throw new IOException("Configured Jenkinsfile not found: " + candidate);
        }
        return candidate;
    }
}
