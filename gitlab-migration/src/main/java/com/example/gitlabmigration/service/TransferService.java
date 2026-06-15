package com.example.gitlabmigration.service;

import com.example.gitlabmigration.client.BulkImportClient;
import com.example.gitlabmigration.client.GitlabApiClient;
import com.example.gitlabmigration.client.GitlabRestClientFactory;
import com.example.gitlabmigration.config.MigrationProperties;
import com.example.gitlabmigration.model.BulkImportResult;
import com.example.gitlabmigration.model.ProjectInfo;
import com.example.gitlabmigration.model.TransferStateEntry;
import com.example.gitlabmigration.report.FailureReportWriter;
import com.example.gitlabmigration.state.TransferStateManager;
import com.example.gitlabmigration.util.PathEncoder;
import com.example.gitlabmigration.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Phase 1 orchestration: throttled bulk import of projects from source to destination.
 * Direct port of transfer_repos.py main() logic.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final String PHASE = "transfer";

    private final MigrationProperties props;
    private final GitlabRestClientFactory clientFactory;
    private final GitlabApiClient apiClient;
    private final BulkImportClient bulkImportClient;
    private final ProjectEnumerationService enumerationService;
    private final NamespaceService namespaceService;
    private final TransferStateManager stateManager;

    public TransferService(MigrationProperties props,
                           GitlabRestClientFactory clientFactory,
                           GitlabApiClient apiClient,
                           BulkImportClient bulkImportClient,
                           ProjectEnumerationService enumerationService,
                           NamespaceService namespaceService,
                           TransferStateManager stateManager) {
        this.props = props;
        this.clientFactory = clientFactory;
        this.apiClient = apiClient;
        this.bulkImportClient = bulkImportClient;
        this.enumerationService = enumerationService;
        this.namespaceService = namespaceService;
        this.stateManager = stateManager;
    }

    public int execute(boolean dryRun, Path projectsFile, boolean skipExisting) {
        try {
            Files.createDirectories(Path.of(props.getReportsDir()));
        } catch (IOException e) {
            log.error("Could not create reports directory: {}", e.getMessage());
            return 1;
        }
        log.info("transfer phase starting (version 1.2.0)");

        try (FailureReportWriter failures = new FailureReportWriter(
                props.getReportsDir(), "transfer_failures.csv")) {

            RestClient src = clientFactory.createSourceClient();
            RestClient dst = clientFactory.createDestinationClient();

            // ---- enumerate ----
            List<ProjectInfo> projects;
            if (projectsFile != null) {
                projects = enumerationService.classifyPathsFromFile(src, projectsFile,
                        (path, reason) -> failures.add(PHASE, path, reason));
            } else {
                projects = enumerationService.listFromSource(src);
            }

            // ---- resume filter ----
            Map<String, TransferStateEntry> state = stateManager.load();
            List<ProjectInfo> pending = new ArrayList<>();
            for (ProjectInfo p : projects) {
                TransferStateEntry entry = state.get(p.pathWithNamespace());
                if (entry == null || !"finished".equals(entry.status())) {
                    pending.add(p);
                }
            }
            log.info("{} already finished (state file), {} pending.",
                    projects.size() - pending.size(), pending.size());

            // ---- skip existing ----
            if (skipExisting && !pending.isEmpty()) {
                log.info("Checking destination for {} pending paths (--skip-existing) ...",
                        pending.size());
                List<ProjectInfo> stillPending = new ArrayList<>();
                for (int n = 0; n < pending.size(); n++) {
                    ProjectInfo p = pending.get(n);
                    if ("group".equals(p.entityType())) {
                        stillPending.add(p);
                        continue;
                    }
                    if (apiClient.getProject(dst, props.getDestination().getUrl(),
                            p.pathWithNamespace()) != null) {
                        state.put(p.pathWithNamespace(), new TransferStateEntry(
                                "finished", null, "already existed on destination",
                                TimeUtils.utcNow()));
                        log.info("  [{}/{}] exists on destination, skipping: {}",
                                n + 1, pending.size(), p.pathWithNamespace());
                    } else {
                        stillPending.add(p);
                    }
                }
                stateManager.save(state);
                log.info("{} skipped (already on destination), {} remain to transfer.",
                        pending.size() - stillPending.size(), stillPending.size());
                pending = stillPending;
            }

            // ---- batch ----
            int batchSize = props.getBatchSize();
            List<List<ProjectInfo>> batches = new ArrayList<>();
            for (int i = 0; i < pending.size(); i += batchSize) {
                batches.add(pending.subList(i, Math.min(i + batchSize, pending.size())));
            }

            if (dryRun) {
                for (int i = 0; i < batches.size(); i++) {
                    List<ProjectInfo> b = batches.get(i);
                    log.info("Batch {}/{}: {}", i + 1, batches.size(),
                            String.join(", ", b.stream()
                                    .map(ProjectInfo::pathWithNamespace).toList()));
                }
                log.info("Dry run complete - nothing submitted.");
                return 0;
            }

            // ---- transfer loop ----
            Map<String, Integer> nsCache = new HashMap<>();
            for (int i = 0; i < batches.size(); i++) {
                List<ProjectInfo> batch = batches.get(i);
                log.info("=== Batch {}/{} ({} projects) at {} ===",
                        i + 1, batches.size(), batch.size(), TimeUtils.utcNow());

                // Ensure destination namespaces exist
                List<ProjectInfo> ready = new ArrayList<>();
                for (ProjectInfo p : batch) {
                    String fullPath = p.pathWithNamespace();
                    int lastSlash = fullPath.lastIndexOf('/');
                    String namespace = lastSlash > 0 ? fullPath.substring(0, lastSlash) : "";

                    if (namespaceService.ensureNamespace(dst, props.getDestination().getUrl(),
                            namespace, nsCache)) {
                        ready.add(p);
                    } else {
                        failures.add(PHASE, fullPath,
                                "destination namespace '" + namespace
                                        + "' missing and could not be created");
                        state.put(fullPath, new TransferStateEntry(
                                "failed", null, null, TimeUtils.utcNow()));
                    }
                }
                if (ready.isEmpty()) {
                    stateManager.save(state);
                    continue;
                }
                batch = ready;

                Integer bulkId = bulkImportClient.submitBatch(dst,
                        props.getDestination().getUrl(),
                        props.getSourceUrlForImport(), props.getSource().getToken(),
                        batch);

                if (bulkId == null) {
                    for (ProjectInfo p : batch) {
                        failures.add(PHASE, p.pathWithNamespace(),
                                "bulk import submission failed (see migration.log)");
                        state.put(p.pathWithNamespace(), new TransferStateEntry(
                                "failed", null, null, TimeUtils.utcNow()));
                    }
                    stateManager.save(state);
                    sleep(props.getSleepBetweenBatchesSeconds());
                    continue;
                }

                Map<String, BulkImportResult> results = bulkImportClient.waitForBatch(
                        dst, props.getDestination().getUrl(), bulkId,
                        props.getPollIntervalSeconds(), props.getBatchTimeoutSeconds());

                for (ProjectInfo p : batch) {
                    String path = p.pathWithNamespace();
                    BulkImportResult res = results.getOrDefault(path,
                            new BulkImportResult(path, "timeout", List.of()));
                    state.put(path, new TransferStateEntry(
                            res.status(), bulkId, null, TimeUtils.utcNow()));

                    if ("finished".equals(res.status())) {
                        log.info("  OK  {}", path);
                    } else {
                        String reason = res.status();
                        if (!res.failures().isEmpty()) {
                            reason += " | " + res.failures().get(0);
                        }
                        log.error("  FAIL {} -> {}", path, reason);
                        failures.add(PHASE, path, reason);
                    }
                }
                stateManager.save(state);

                if (i < batches.size() - 1) {
                    log.info("Sleeping {}s before next batch ...",
                            props.getSleepBetweenBatchesSeconds());
                    sleep(props.getSleepBetweenBatchesSeconds());
                }
            }

            long done = state.values().stream()
                    .filter(e -> "finished".equals(e.status())).count();
            log.info("Transfer pass complete: {}/{} finished. Failures (if any) in {}",
                    done, projects.size(), failures.getFilePath());
            return 0;

        } catch (IOException e) {
            log.error("Transfer failed with I/O error: {}", e.getMessage(), e);
            return 1;
        }
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted");
        }
    }
}
