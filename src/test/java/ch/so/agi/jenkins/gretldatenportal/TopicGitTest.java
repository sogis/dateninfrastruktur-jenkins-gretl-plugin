package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class TopicGitTest {
    @TempDir Path directory;

    @Test @WithJenkins
    void credentialsReachAskpassOnlyThroughEnvironmentAndHelperIsRemoved(JenkinsRule j) throws Exception {
        var provider = SystemCredentialsProvider.getInstance();
        provider.getCredentials().add(new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, "delivery-git", "test", "delivery-bot", "dummy-test-token"));
        provider.save();
        GretlDatenportalGlobalConfiguration.get().setTopicRepositoryCredentialsId("delivery-git");
        String probe = "!f() { printf '%s\\n' \"$GIT_ASKPASS\"; \"$GIT_ASKPASS\" Username; \"$GIT_ASKPASS\" Password; }; f";
        String output = TopicGit.run(directory, "-c", "alias.probe=" + probe, "probe");
        var lines = output.lines().toList();
        assertEquals("delivery-bot", lines.get(1));
        assertEquals("****", lines.get(2));
        assertFalse(output.contains("dummy-test-token"));
        assertFalse(Files.exists(Path.of(lines.getFirst())));
    }

    @Test @WithJenkins
    void missingConfiguredCredentialFailsBeforeInvokingGit(JenkinsRule j) {
        GretlDatenportalGlobalConfiguration.get().setTopicRepositoryCredentialsId("missing");
        var failure = assertThrows(IOException.class, () -> TopicGit.run(directory, "--version"));
        assertTrue(failure.getMessage().contains("credential not found"));
    }
}
