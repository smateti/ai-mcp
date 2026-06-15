package com.example.gitlabmigration.service;

import com.example.gitlabmigration.config.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ProcessBuilder wrapper for git commands.
 * Tokens are injected via a one-shot credential helper,
 * never embedded in remote URLs.
 */
@Service
public class GitCommandService {

    private static final Logger log = LoggerFactory.getLogger(GitCommandService.class);
    private static final long GIT_TIMEOUT_SECONDS = 3600; // 1 hour

    private final MigrationProperties props;

    public GitCommandService(MigrationProperties props) {
        this.props = props;
    }

    /**
     * Run a git command with token auth via credential helper.
     *
     * @param gitArgs git arguments, e.g. ["clone", "--mirror", url, dir]
     * @param token   GitLab token used as password (username 'oauth2')
     * @param workDir working directory (null = inherit)
     * @return ProcessResult with exit code, stdout, stderr
     */
    public ProcessResult runGit(List<String> gitArgs, String token, Path workDir) {
        String helper = "!f() { echo username=oauth2; echo password=" + token + "; }; f";

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("credential.helper=" + helper);
        command.add("-c");
        command.add("credential.useHttpPath=true");
        if (props.getSsl().isSkipHostnameVerification()) {
            command.add("-c");
            command.add("http.sslVerify=false");
        }
        command.addAll(gitArgs);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }

            Process process = pb.start();

            // Read stdout and stderr in parallel to avoid pipe buffer deadlock
            StreamReader stdoutReader = new StreamReader(process.getInputStream());
            StreamReader stderrReader = new StreamReader(process.getErrorStream());
            stdoutReader.start();
            stderrReader.start();

            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            stdoutReader.join(5000);
            stderrReader.join(5000);

            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(-1, stdoutReader.getOutput(),
                        "Process timed out after " + GIT_TIMEOUT_SECONDS + "s");
            }

            return new ProcessResult(process.exitValue(),
                    stdoutReader.getOutput(), stderrReader.getOutput());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ProcessResult(-1, "", "Exception running git: " + e.getMessage());
        }
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {}

    private static class StreamReader extends Thread {
        private final InputStream inputStream;
        private String output = "";

        StreamReader(InputStream inputStream) {
            this.inputStream = inputStream;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException ignored) {
                // Best effort
            }
        }

        String getOutput() { return output; }
    }
}
