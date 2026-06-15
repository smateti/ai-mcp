package com.example.gitlabmigration.command;

import com.example.gitlabmigration.service.TransferService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Phase 1 CLI subcommand: throttled bulk import of projects.
 */
@Component
@Command(name = "transfer",
         description = "Phase 1: Throttled bulk import of projects via direct transfer API",
         mixinStandardHelpOptions = true)
public class TransferCommand implements Callable<Integer> {

    @Option(names = "--dry-run",
            description = "Show the batch plan without calling any write API.")
    private boolean dryRun;

    @Option(names = "--projects-file",
            description = "File with one source path_with_namespace per line; "
                    + "overrides group enumeration.")
    private Path projectsFile;

    @Option(names = "--skip-existing",
            description = "Check the destination for each pending path and skip any project "
                    + "that already exists there.")
    private boolean skipExisting;

    private final TransferService transferService;

    public TransferCommand(TransferService transferService) {
        this.transferService = transferService;
    }

    @Override
    public Integer call() {
        return transferService.execute(dryRun, projectsFile, skipExisting);
    }
}
