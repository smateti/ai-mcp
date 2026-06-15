package com.example.gitlabmigration.service;

import com.example.gitlabmigration.client.BulkImportClient;
import com.example.gitlabmigration.client.GitlabApiClient;
import com.example.gitlabmigration.client.GitlabRestClientFactory;
import com.example.gitlabmigration.config.MigrationProperties;
import com.example.gitlabmigration.model.ProtectedBranchRule;
import com.example.gitlabmigration.report.FailureReportWriter;
import com.example.gitlabmigration.util.TimeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Phase 3 orchestration: re-sync drifted repositories via git mirror or reimport.
 * Direct port of resync_repos.py main() logic.
 */
@Service
public class ResyncService {

    private static final Logger log = LoggerFactory.getLogger(ResyncService.class);
    private static final String PHASE = "resync";

    private final MigrationProperties props;
    private final GitlabRestClientFactory clientFactory;
    private final GitlabApiClient apiClient;
    private final BulkImportClient bulkImportClient;
    private final GitCommandService gitCommandService;
    private final BranchProtectionService branchProtectionService;
    private final ProjectEnumerationService enumerationService;

    public ResyncService(MigrationProperties props,
                         GitlabRestClientFactory clientFactory,
                         GitlabApiClient apiClient,
                         BulkImportClient bulkImportClient,
                         GitCommandService gitCommandService,
                         BranchProtectionService branchProtectionService,
                         ProjectEnumerationService enumerationService) {
        this.props = props;
        this.clientFactory = clientFactory;
        this.apiClient = apiClient;
        this.bulkImportClient = bulkImportClient;
        this.gitCommandService = gitCommandService;
        this.branchProtectionService = branchProtectionService;
        this.enumerationService = enumerationService;
    }

    public int execute(Path projectsFile, boolean reimport,
                       boolean keepProtections, int sleepSeconds) {
        try {
            Files.createDirectories(Path.of(props.getReportsDir()));
        } catch (IOException e) {
            log.error("Could not create reports directory: {}", e.getMessage());
            return 1;
        }

        try (FailureReportWriter failures = new FailureReportWriter(
                props.getReportsDir(), "resync_failures.csv")) {

            RestClient dst = clientFactory.createDestinationClient();

            List<String> paths = enumerationService.loadPathsFromFile(projectsFile);
            String mode = reimport
                    ? "reimport (delete + fresh direct transfer)"
                    : "git mirror sync";
            log.info("Re-syncing {} repositories using {} ...", paths.size(), mode);

            int ok = 0;
            for (int n = 0; n < paths.size(); n++) {
                String path = paths.get(n);
                log.info("[{}/{}] {}", n + 1, paths.size(), path);
                try {
                    if (reimport) {
                        doReimport(path, dst);
                    } else {
                        doMirrorSync(path, dst, keepProtections);
                    }
                    ok++;
                    log.info("    OK");
                } catch (RuntimeException e) {
                    String reason = e.getMessage() != null
                            ? e.getMessage().substring(0, Math.min(500, e.getMessage().length()))
                            : e.getClass().getSimpleName();
                    log.error("    FAIL: {}", reason);
                    failures.add(PHASE, path, reason);
                }

                if (n < paths.size() - 1) {
                    sleep(sleepSeconds);
                }
            }

            log.info("Re-sync complete at {}: {}/{} succeeded. Failures (if any) in {}",
                    TimeUtils.utcNow(), ok, paths.size(), failures.getFilePath());
            log.info("Tip: re-run verify --projects-file {} to confirm.", projectsFile);
            return 0;

        } catch (IOException e) {
            log.error("Resync failed with I/O error: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Mirror one repository: clone --mirror from source, push --mirror to target.
     * Lifts and restores branch protections around the force-push.
     */
    private void doMirrorSync(String path, RestClient dst, boolean keepProtections) {
        String destUrl = props.getDestination().getUrl();
        JsonNode target = apiClient.getProject(dst, destUrl, path);
        if (target == null) {
            throw new RuntimeException("target project missing - run transfer "
                    + "with --projects-file for it instead");
        }

        String srcHttp = props.getSource().getUrl() + "/" + path + ".git";
        String dstHttp = destUrl + "/" + path + ".git";
        int projectId = target.get("id").asInt();
        Path tmpDir = null;
        List<ProtectedBranchRule> savedRules = List.of();

        try {
            tmpDir = Files.createTempDirectory("resync_");
            Path cloneDir = tmpDir.resolve("repo.git");

            // Clone from source
            GitCommandService.ProcessResult cloneResult = gitCommandService.runGit(
                    List.of("clone", "--mirror", srcHttp, cloneDir.toString()),
                    props.getSource().getToken(), null);
            if (cloneResult.exitCode() != 0) {
                throw new RuntimeException("mirror clone failed (exit="
                        + cloneResult.exitCode() + "): "
                        + truncate(cloneResult.stderr(), 800));
            }

            // Remove GitLab internal refs that can't be pushed (merge requests, pipelines, etc.)
            GitCommandService.ProcessResult cleanRefs = gitCommandService.runGit(
                    List.of("for-each-ref", "--format=%(refname)",
                            "refs/merge-requests/", "refs/pipelines/",
                            "refs/environments/", "refs/keep-around/"),
                    null, cloneDir);
            if (cleanRefs.exitCode() == 0 && !cleanRefs.stdout().isBlank()) {
                for (String ref : cleanRefs.stdout().split("\n")) {
                    String trimmed = ref.strip();
                    if (!trimmed.isEmpty()) {
                        gitCommandService.runGit(
                                List.of("update-ref", "-d", trimmed), null, cloneDir);
                    }
                }
                log.info("    cleaned internal refs from mirror clone");
            }

            // Lift protections
            if (!keepProtections) {
                savedRules = branchProtectionService.liftProtections(dst, destUrl, projectId);
                if (!savedRules.isEmpty()) {
                    log.info("    lifted {} protected-branch rule(s)", savedRules.size());
                }
            }

            // Push to destination
            GitCommandService.ProcessResult pushResult = gitCommandService.runGit(
                    List.of("push", "--mirror", dstHttp),
                    props.getDestination().getToken(), cloneDir);
            if (pushResult.exitCode() != 0) {
                throw new RuntimeException("mirror push failed (exit="
                        + pushResult.exitCode() + "): "
                        + truncate(pushResult.stderr(), 800));
            }
        } catch (IOException e) {
            throw new RuntimeException("I/O error during mirror sync: " + e.getMessage(), e);
        } finally {
            // Restore protections
            if (!savedRules.isEmpty()) {
                branchProtectionService.restoreProtections(dst, destUrl, projectId, savedRules);
            }
            // Clean up temp dir
            if (tmpDir != null) {
                deleteRecursively(tmpDir);
            }
        }
    }

    /**
     * DESTRUCTIVE full refresh: delete target project, then fresh bulk import.
     */
    private void doReimport(String path, RestClient dst) {
        String destUrl = props.getDestination().getUrl();
        JsonNode target = apiClient.getProject(dst, destUrl, path);

        if (target != null) {
            int projectId = target.get("id").asInt();
            try {
                dst.delete()
                        .uri(destUrl + "/api/v4/projects/" + projectId)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RuntimeException e) {
                throw new RuntimeException("delete failed: " + e.getMessage(), e);
            }

            log.info("    deleted target project (id={}); waiting for removal ...", projectId);

            // Wait for deletion to complete
            for (int i = 0; i < 30; i++) {
                if (apiClient.getProject(dst, destUrl, path) == null) break;
                sleep(10);
                if (i == 29) {
                    throw new RuntimeException("target project still present after delete "
                            + "(delayed-deletion setting?) - reimport aborted");
                }
            }
        }

        bulkImportClient.submitAndWaitSingle(dst, destUrl,
                props.getSourceUrlForImport(), props.getSource().getToken(),
                path, props.getPollIntervalSeconds(), props.getBatchTimeoutSeconds());
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted");
        }
    }

    private void deleteRecursively(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {
            // Best effort cleanup
        }
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
