package com.example.gitlabmigration.command;

import com.example.gitlabmigration.service.ResyncService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Phase 3 CLI subcommand: re-sync drifted repositories.
 */
@Component
@Command(name = "resync",
         description = "Phase 3: Re-sync drifted repositories via git mirror or reimport",
         mixinStandardHelpOptions = true)
public class ResyncCommand implements Callable<Integer> {

    @Option(names = "--projects-file", required = true,
            description = "File with one path_with_namespace per line "
                    + "(use out_of_sync_{ts}.txt from verify).")
    private Path projectsFile;

    @Option(names = "--reimport",
            description = "DESTRUCTIVE: delete target project and re-run a fresh direct transfer.")
    private boolean reimport;

    @Option(names = "--keep-protections",
            description = "Never modify protected branches on target.")
    private boolean keepProtections;

    @Option(names = "--sleep", defaultValue = "10",
            description = "Seconds to sleep between repos (default 10).")
    private int sleepSeconds;

    private final ResyncService resyncService;

    public ResyncCommand(ResyncService resyncService) {
        this.resyncService = resyncService;
    }

    @Override
    public Integer call() {
        return resyncService.execute(projectsFile, reimport, keepProtections, sleepSeconds);
    }
}
