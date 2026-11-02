package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.matrixauth.AuthorizationType;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
import org.jenkinsci.plugins.matrixauth.inheritance.NonInheritingStrategy;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class GretlDatenportalAuthorizationSynchronizerTest {
    @Test
    @WithJenkins
    void synchronizesWorkflowJobPermissionsAndRemovesStaleEntries(JenkinsRule jenkinsRule) throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "datenportal-afu");
        GretlDatenportalAuthorizationSynchronizer synchronizer =
                new GretlDatenportalAuthorizationSynchronizer();

        synchronizer.synchronize(
                job,
                new PermissionConfiguration(List.of("read-user"), List.of("build-user")));
        AuthorizationMatrixProperty property = job.getProperty(AuthorizationMatrixProperty.class);

        assertNotNull(property);
        assertInstanceOf(NonInheritingStrategy.class, property.getInheritanceStrategy());
        assertEquals(Set.of("read-user", "build-user"), userSids(property, Item.READ));
        assertEquals(Set.of("build-user"), userSids(property, Item.BUILD));

        synchronizer.synchronize(job, new PermissionConfiguration(List.of("new-reader"), List.of("new-builder")));

        assertEquals(Set.of("new-reader", "new-builder"), userSids(
                job.getProperty(AuthorizationMatrixProperty.class), Item.READ));
        assertEquals(Set.of("new-builder"), userSids(
                job.getProperty(AuthorizationMatrixProperty.class), Item.BUILD));
    }

    @Test
    @WithJenkins
    void synchronizesSeedJobFromConfiguredTeam(JenkinsRule jenkinsRule) throws Exception {
        jenkinsRule.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
        FreeStyleProject seedJob = jenkinsRule.createFreeStyleProject("seed-for-test");
        TeamDirectory directory = new TeamDirectory(Map.of(
                "gretl-datenportal-seed-operators", Set.of("operator")));

        new GretlDatenportalAuthorizationSynchronizer().synchronizeSeedJob(
                seedJob,
                directory,
                "gretl-datenportal-seed-operators");

        AuthorizationMatrixProperty property = seedJob.getProperty(AuthorizationMatrixProperty.class);
        assertNotNull(property);
        assertEquals(Set.of("operator"), userSids(property, Item.READ));
        assertEquals(Set.of("operator"), userSids(property, Item.BUILD));
        assertFalse(seedJob.getACL().hasPermission2(authentication("operator"), Item.CONFIGURE));
        assertFalse(seedJob.getACL().hasPermission2(authentication("operator"), Item.DELETE));
        assertFalse(seedJob.getACL().hasPermission2(authentication("operator"), Item.CANCEL));
    }

    @Test
    @WithJenkins
    void refusesToReplaceSeedAclForUnknownOperatorsTeam(JenkinsRule jenkinsRule) throws Exception {
        jenkinsRule.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
        FreeStyleProject seedJob = jenkinsRule.createFreeStyleProject("seed-invalid-team");
        GretlDatenportalAuthorizationSynchronizer synchronizer =
                new GretlDatenportalAuthorizationSynchronizer();
        synchronizer.synchronizeSeedJob(
                seedJob,
                new TeamDirectory(Map.of("known", Set.of("operator"))),
                "known");

        assertThrows(java.io.IOException.class, () -> synchronizer.synchronizeSeedJob(
                seedJob,
                new TeamDirectory(Map.of("known", Set.of("operator"))),
                "missing"));
        assertNotNull(seedJob.getProperty(AuthorizationMatrixProperty.class));
        assertEquals(Set.of("operator"), userSids(
                seedJob.getProperty(AuthorizationMatrixProperty.class), Item.BUILD));
    }

    @Test
    @WithJenkins
    void projectAclRejectsNonMembersAndSeparatesReadAndBuild(JenkinsRule jenkinsRule) throws Exception {
        jenkinsRule.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "datenportal-acl");

        new GretlDatenportalAuthorizationSynchronizer().synchronize(
                job,
                new PermissionConfiguration(List.of("reader"), List.of("builder")));

        assertTrue(job.getACL().hasPermission2(authentication("reader"), Item.READ));
        assertFalse(job.getACL().hasPermission2(authentication("reader"), Item.BUILD));
        assertTrue(job.getACL().hasPermission2(authentication("builder"), Item.READ));
        assertTrue(job.getACL().hasPermission2(authentication("builder"), Item.BUILD));
        assertFalse(job.getACL().hasPermission2(authentication("outsider"), Item.READ));
        assertFalse(job.getACL().hasPermission2(authentication("outsider"), Item.BUILD));
    }

    private Authentication authentication(String username) {
        return new UsernamePasswordAuthenticationToken(username, "n/a", List.of());
    }

    private Set<String> userSids(AuthorizationMatrixProperty property, hudson.security.Permission permission) {
        return property.getGrantedPermissionEntries().getOrDefault(permission, Set.of()).stream()
                .filter(entry -> entry.getType() == AuthorizationType.USER)
                .map(PermissionEntry::getSid)
                .collect(java.util.stream.Collectors.toSet());
    }
}
