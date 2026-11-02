package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import jenkins.model.Jenkins;

final class TopicRepositoryManager {
    static final Object REPOSITORY_LOCK = new Object();
    static final String MANAGED_REPOSITORY_RELATIVE_PATH = "gretl-datenportal/topic-repo";

    Path resolveRepositoryPath(GretlDatenportalGlobalConfiguration configuration, boolean update)
            throws IOException, InterruptedException {
        return resolveRepositoryPath(
                ConfiguredTopicRepository.fromGlobalConfiguration(configuration),
                update,
                null);
    }

    Path resolveRepositoryPath(
            ConfiguredTopicRepository repository,
            boolean update,
            Consumer<String> logger) throws IOException, InterruptedException {
        Objects.requireNonNull(repository, "repository");

        if (repository.hasGitRepository()) {
            if (repository.usesWorkingTree()) {
                return ensureWorkingTreeSnapshot(
                        repository.legacyPathAsPath(), managedRepositoryPath(), update, logger);
            }
            return ensureManagedCheckout(repository.getUrl(), repository.getBranch(), managedRepositoryPath(), update, logger);
        }
        if (repository.hasLegacyPath()) {
            return ensureWorkingTreeSnapshot(
                    repository.legacyPathAsPath(), managedRepositoryPath(), update, logger);
        }
        return null;
    }

    Path ensureWorkingTreeSnapshot(
            Path sourcePath,
            Path checkoutPath,
            boolean update,
            Consumer<String> logger) throws IOException {
        synchronized (REPOSITORY_LOCK) {
            if (sourcePath == null) {
                return null;
            }

            Path normalizedSource = sourcePath.toAbsolutePath().normalize();
            Path normalizedCheckout = checkoutPath.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalizedSource)) {
                throw new IOException("Working-tree topic repository directory not found: " + normalizedSource);
            }

            if (Files.isDirectory(normalizedCheckout) && !update) {
                return normalizedCheckout;
            }

            Files.createDirectories(normalizedCheckout.getParent());
            deleteRecursively(normalizedCheckout);
            Files.createDirectories(normalizedCheckout);
            log(logger, "Creating working-tree topic repository snapshot from: " + normalizedSource);
            log(logger, "Working-tree snapshot destination: " + normalizedCheckout);
            copyWorkingTree(normalizedSource, normalizedCheckout);
            return normalizedCheckout;
        }
    }

    Path ensureManagedCheckout(
            String repositoryUrl,
            String branch,
            Path checkoutPath,
            boolean update,
            Consumer<String> logger) throws IOException, InterruptedException {
        synchronized (REPOSITORY_LOCK) {
            if (repositoryUrl == null || repositoryUrl.isBlank()) {
                return null;
            }

            java.net.URI remote;
            try { remote = java.net.URI.create(repositoryUrl); }
            catch (IllegalArgumentException ex) { throw new IOException("Invalid topic Git URL."); }
            if (!("https".equals(remote.getScheme()) || "file".equals(remote.getScheme())) || remote.getUserInfo() != null)
                throw new IOException("Topic Git URL must use HTTPS or file, without embedded credentials.");
            if (normalizeBranch(branch).startsWith("-")) throw new IOException("Invalid topic Git branch.");
            Path normalizedCheckoutPath = checkoutPath.toAbsolutePath().normalize();
            Files.createDirectories(normalizedCheckoutPath.getParent());

            if (!isGitCheckout(normalizedCheckoutPath)) {
                recreateCheckout(normalizedCheckoutPath, repositoryUrl, branch, logger);
                return normalizedCheckoutPath;
            }

            if (!update) {
                return normalizedCheckoutPath;
            }

            log(logger, "Updating managed topic repository checkout: " + normalizedCheckoutPath);
            runGit(normalizedCheckoutPath, logger, "remote", "set-url", "origin", repositoryUrl);
            runGit(normalizedCheckoutPath, logger, "fetch", "--prune", "origin");
            String normalizedBranch = normalizeBranch(branch);
            runGit(normalizedCheckoutPath, logger, "checkout", "-B", normalizedBranch, "origin/" + normalizedBranch);
            runGit(normalizedCheckoutPath, logger, "reset", "--hard", "origin/" + normalizedBranch);
            runGit(normalizedCheckoutPath, logger, "clean", "-fd");
            return normalizedCheckoutPath;
        }
    }

    private Path managedRepositoryPath() throws IOException {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new IOException("Jenkins is not running; managed topic repository path is unavailable.");
        }
        return jenkins.getRootDir().toPath().resolve(MANAGED_REPOSITORY_RELATIVE_PATH);
    }

    private void recreateCheckout(Path checkoutPath, String repositoryUrl, String branch, Consumer<String> logger)
            throws IOException, InterruptedException {
        deleteRecursively(checkoutPath);
        Files.createDirectories(checkoutPath.getParent());
        log(logger, "Cloning managed topic repository checkout: " + repositoryUrl);
        runGit(
                checkoutPath.getParent(),
                logger,
                "clone",
                "--branch",
                normalizeBranch(branch),
                "--single-branch",
                repositoryUrl,
                checkoutPath.toString());
    }

    private boolean isGitCheckout(Path checkoutPath) {
        if (!Files.isDirectory(checkoutPath)) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("git", "-C", checkoutPath.toString(), "rev-parse", "--is-inside-work-tree")
                    .redirectErrorStream(true)
                    .start();
            try (InputStream inputStream = process.getInputStream()) {
                String output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
                return process.waitFor() == 0 && "true".equalsIgnoreCase(output);
            }
        } catch (Exception ex) {
            return false;
        }
    }

    void copyWorkingTree(Path sourcePath, Path checkoutPath) throws IOException {
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Path relative = sourcePath.relativize(directory);
                if (!relative.toString().isEmpty() && isExcluded(relative)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(checkoutPath.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path relative = sourcePath.relativize(file);
                if (!isExcluded(relative)) {
                    Path target = checkoutPath.resolve(relative);
                    Files.createDirectories(target.getParent());
                    if (Files.isSymbolicLink(file)) {
                        Files.deleteIfExists(target);
                        Files.createSymbolicLink(target, Files.readSymbolicLink(file));
                    } else {
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isExcluded(Path relativePath) {
        for (Path part : relativePath) {
            String name = part.toString();
            if (name.equals(".git")
                    || name.equals(".gradle")
                    || name.equals("build")
                    || name.equals(".DS_Store")) {
                return true;
            }
        }
        return false;
    }

    private void runGit(Path workdir, Consumer<String> logger, String... args) throws IOException, InterruptedException {
        String output = TopicGit.run(workdir, args);
        if (!output.isBlank()) log(logger, output);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    private String normalizeBranch(String branch) {
        return branch == null || branch.isBlank() ? "main" : branch.trim();
    }

    private void log(Consumer<String> logger, String message) {
        if (logger != null && message != null && !message.isBlank()) {
            logger.accept(message);
        }
    }
}
