package ch.so.agi.jenkins.gretldatenportal;

import hudson.model.Job;
import hudson.model.Item;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.Permission;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.matrixauth.AuthorizationType;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy;

public final class GretlDatenportalAuthorizationSynchronizer {

    public void synchronize(Job<?, ?> job, PermissionConfiguration permissions) throws IOException {
        if (job == null || permissions == null) {
            return;
        }
        replaceAuthorization(job, permissions.getReadUsers(), permissions.getBuildUsers());
    }

    public void synchronizeSeedJob(Job<?, ?> seedJob, TeamDirectory directory, String operatorsTeam)
            throws IOException {
        if (seedJob == null) {
            return;
        }
        if (directory == null || operatorsTeam == null || operatorsTeam.isBlank()) {
            throw new IOException("Seed-Job Operators Team is not configured.");
        }
        String normalizedTeam = operatorsTeam.trim();
        if (!directory.hasTeam(normalizedTeam)) {
            throw new IOException("Seed-Job Operators Team '" + normalizedTeam + "' is not defined in the teams file.");
        }
        Set<String> operators = directory.getUsers(normalizedTeam);
        replaceAuthorization(seedJob, List.copyOf(operators), List.copyOf(operators));
    }

    public void lock(Job<?, ?> job) throws IOException {
        if (job == null) {
            return;
        }
        replaceAuthorization(job, List.of(), List.of());
    }

    private void replaceAuthorization(Job<?, ?> job, List<String> readUsers, List<String> buildUsers)
            throws IOException {
        Map<Permission, Set<PermissionEntry>> permissions = new HashMap<>();
        permissions.put(Item.READ, entries(readUsers));
        permissions.put(Item.BUILD, entries(buildUsers));

        job.removeProperty(AuthorizationMatrixProperty.class);
        job.addProperty(new AuthorizationMatrixProperty(permissions, new NonInheritingStrategy()));
        if (job.getProperty(GretlDatenportalManagedJobProperty.class) == null) {
            job.addProperty(new GretlDatenportalManagedJobProperty());
        }
        job.save();
    }

    private Set<PermissionEntry> entries(List<String> users) {
        Set<PermissionEntry> entries = new HashSet<>();
        if (users != null) {
            for (String user : users) {
                if (user != null && !user.isBlank()) {
                    entries.add(new PermissionEntry(AuthorizationType.USER, user.trim()));
                }
            }
        }
        return entries;
    }
}
