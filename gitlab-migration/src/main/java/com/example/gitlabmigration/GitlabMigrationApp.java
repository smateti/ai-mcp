package com.example.gitlabmigration;

import com.example.gitlabmigration.command.ResyncCommand;
import com.example.gitlabmigration.command.TransferCommand;
import com.example.gitlabmigration.command.VerifyCommand;
import com.example.gitlabmigration.config.MigrationProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

/**
 * Spring Boot CLI application for GitLab server-to-server migration.
 *
 * Usage:
 *   java -jar gitlab-migration.jar transfer [--dry-run] [--projects-file FILE] [--skip-existing]
 *   java -jar gitlab-migration.jar verify   [--projects-file FILE] [--check-metadata]
 *   java -jar gitlab-migration.jar resync   --projects-file FILE [--reimport] [--keep-protections] [--sleep N]
 */
@SpringBootApplication
@EnableConfigurationProperties(MigrationProperties.class)
public class GitlabMigrationApp implements CommandLineRunner {

    private final IFactory factory;
    private final TransferCommand transferCommand;
    private final VerifyCommand verifyCommand;
    private final ResyncCommand resyncCommand;

    public GitlabMigrationApp(IFactory factory,
                               TransferCommand transferCommand,
                               VerifyCommand verifyCommand,
                               ResyncCommand resyncCommand) {
        this.factory = factory;
        this.transferCommand = transferCommand;
        this.verifyCommand = verifyCommand;
        this.resyncCommand = resyncCommand;
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(GitlabMigrationApp.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        int exitCode = new CommandLine(new TopCommand(), factory)
                .addSubcommand("transfer", transferCommand)
                .addSubcommand("verify", verifyCommand)
                .addSubcommand("resync", resyncCommand)
                .execute(args);

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    @Command(name = "gitlab-migration",
             description = "GitLab server-to-server migration toolkit",
             mixinStandardHelpOptions = true,
             version = "1.0.0")
    static class TopCommand implements Runnable {
        @Override
        public void run() {
            // No subcommand specified — show help
            new CommandLine(this).usage(System.out);
        }
    }
}
