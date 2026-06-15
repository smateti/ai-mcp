package com.example.gitlabmigration.command;

import com.example.gitlabmigration.service.VerifyService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Phase 2 CLI subcommand: drift detection via ref comparison.
 */
@Component
@Command(name = "verify",
         description = "Phase 2: Compare source vs destination branch/tag SHAs",
         mixinStandardHelpOptions = true)
public class VerifyCommand implements Callable<Integer> {

    @Option(names = "--projects-file",
            description = "Verify only these paths (one per line) instead of enumerating the whole group.")
    private Path projectsFile;

    @Option(names = "--check-metadata",
            description = "Also compare counts of issues, merge requests, labels, and milestones.")
    private boolean checkMetadata;

    private final VerifyService verifyService;

    public VerifyCommand(VerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @Override
    public Integer call() {
        return verifyService.execute(projectsFile, checkMetadata);
    }
}
