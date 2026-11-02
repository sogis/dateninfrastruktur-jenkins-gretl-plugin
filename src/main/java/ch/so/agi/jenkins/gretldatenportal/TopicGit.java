package ch.so.agi.jenkins.gretldatenportal;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.security.ACL;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

/** CLI Git with short-lived askpass binding, never credentials in URLs or arguments. */
final class TopicGit {
    static String run(Path directory, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git"); command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        process.environment().put("GIT_TERMINAL_PROMPT", "0");
        Path helper = null;
        String password = "";
        try {
            var jenkins = Jenkins.getInstanceOrNull();
            var config = jenkins == null ? null : GretlDatenportalGlobalConfiguration.get();
            String credentialId = config == null ? "" : config.getTopicRepositoryCredentialsId();
            if (!credentialId.isBlank()) {
                var credential = CredentialsProvider.lookupCredentialsInItemGroup(
                        StandardUsernamePasswordCredentials.class, jenkins, ACL.SYSTEM2, List.of()).stream()
                        .filter(c -> c.getId().equals(credentialId)).findFirst()
                        .orElseThrow(() -> new IOException("Git HTTPS credential not found: " + credentialId));
                password = credential.getPassword().getPlainText();
                helper = Files.createTempFile("datenportal-git-askpass-", ".sh");
                Files.writeString(helper, "#!/bin/sh\ncase \"$1\" in *Username*) printf '%s\\n' \"$DP_GIT_USERNAME\";; *) printf '%s\\n' \"$DP_GIT_PASSWORD\";; esac\n");
                Files.setPosixFilePermissions(helper, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                process.environment().put("GIT_ASKPASS", helper.toString());
                process.environment().put("DP_GIT_USERNAME", credential.getUsername());
                process.environment().put("DP_GIT_PASSWORD", password);
            }
            Process child = process.start();
            String output;
            try (var input = child.getInputStream()) { output = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            int code = child.waitFor();
            if (!password.isEmpty()) output = output.replace(password, "****");
            if (code != 0) throw new IOException("Git " + args[0] + " failed (" + code + "): " + output);
            return output.trim();
        } finally {
            if (helper != null) Files.deleteIfExists(helper);
        }
    }
    private TopicGit() { }
}
