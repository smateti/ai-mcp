package com.example.gitlabmigration.service;

import com.example.gitlabmigration.client.GitlabApiClient;
import com.example.gitlabmigration.client.GitlabRestClientFactory;
import com.example.gitlabmigration.config.MigrationProperties;
import com.example.gitlabmigration.model.MetadataCounts;
import com.example.gitlabmigration.model.RefSnapshot;
import com.example.gitlabmigration.report.FailureReportWriter;
import com.example.gitlabmigration.report.SyncReportWriter;
import com.example.gitlabmigration.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 2 orchestration: compare source vs destination refs and classify
 * each project as in_sync, out_of_sync, or missing.
 * Direct port of verify_sync.py main() logic.
 */
@Service
public class VerifyService {

    private static final Logger log = LoggerFactory.getLogger(VerifyService.class);
    private static final String PHASE = "verify";

    private final MigrationProperties props;
    private final GitlabRestClientFactory clientFactory;
    private final GitlabApiClient apiClient;
    private final ProjectEnumerationService enumerationService;

    public VerifyService(MigrationProperties props,
                         GitlabRestClientFactory clientFactory,
                         GitlabApiClient apiClient,
                         ProjectEnumerationService enumerationService) {
        this.props = props;
        this.clientFactory = clientFactory;
        this.apiClient = apiClient;
        this.enumerationService = enumerationService;
    }

    public int execute(Path projectsFile, boolean checkMetadata) {
        try {
            Files.createDirectories(Path.of(props.getReportsDir()));
        } catch (IOException e) {
            log.error("Could not create reports directory: {}", e.getMessage());
            return 1;
        }

        try (FailureReportWriter failures = new FailureReportWriter(
                props.getReportsDir(), "verify_failures.csv")) {

            RestClient src = clientFactory.createSourceClient();
            RestClient dst = clientFactory.createDestinationClient();

            // Enumerate paths
            List<String> paths;
            if (projectsFile != null) {
                paths = enumerationService.loadPathsFromFile(projectsFile);
            } else {
                paths = enumerationService.listFromSource(src).stream()
                        .map(p -> p.pathWithNamespace())
                        .collect(Collectors.toList());
            }
            log.info("Verifying {} projects ...", paths.size());

            String ts = TimeUtils.utcNowFileSafe();
            int inSync = 0, outOfSync = 0, missing = 0, errors = 0;

            try (SyncReportWriter report = new SyncReportWriter(props.getReportsDir(), ts)) {
                for (int n = 0; n < paths.size(); n++) {
                    String path = paths.get(n);
                    try {
                        RefSnapshot srcRefs = apiClient.getRefs(
                                src, props.getSource().getUrl(), path);
                        if (srcRefs == null) {
                            log.warn("[{}/{}] {} vanished from source, skipping",
                                    n + 1, paths.size(), path);
                            continue;
                        }

                        RefSnapshot dstRefs = apiClient.getRefs(
                                dst, props.getDestination().getUrl(), path);

                        MetadataCounts srcMeta = null, dstMeta = null;
                        if (checkMetadata && dstRefs != null) {
                            srcMeta = apiClient.getMetadataCounts(
                                    src, props.getSource().getUrl(), path);
                            dstMeta = apiClient.getMetadataCounts(
                                    dst, props.getDestination().getUrl(), path);
                        }

                        if (dstRefs == null) {
                            missing++;
                            report.writeMissing(path, srcRefs.branches().size(),
                                    srcRefs.tags().size());
                            log.warn("[{}/{}] MISSING     {}", n + 1, paths.size(), path);
                            continue;
                        }

                        List<String> diffs = diffRefs(srcRefs, dstRefs);
                        if (srcMeta != null) {
                            diffs.addAll(diffMetadata(srcMeta, dstMeta));
                        }

                        if (!diffs.isEmpty()) {
                            outOfSync++;
                            report.writeOutOfSync(path,
                                    srcRefs.branches().size(), dstRefs.branches().size(),
                                    srcRefs.tags().size(), dstRefs.tags().size(),
                                    srcMeta, dstMeta, diffs);
                            log.warn("[{}/{}] OUT_OF_SYNC {} ({} diffs)",
                                    n + 1, paths.size(), path, diffs.size());
                        } else {
                            inSync++;
                            report.writeInSync(path,
                                    srcRefs.branches().size(), dstRefs.branches().size(),
                                    srcRefs.tags().size(), dstRefs.tags().size(),
                                    srcMeta, dstMeta);
                            if ((n + 1) % 100 == 0) {
                                log.info("[{}/{}] progress ... last: {} in_sync",
                                        n + 1, paths.size(), path);
                            }
                        }
                    } catch (RestClientException e) {
                        errors++;
                        log.error("[{}/{}] {} API error: {}",
                                n + 1, paths.size(), path, e.getMessage());
                        failures.add(PHASE, path, "verification API error: " + e.getMessage());
                    }
                }

                log.info("Done. in_sync={} out_of_sync={} missing={} errors={}",
                        inSync, outOfSync, missing, errors);
                log.info("Full report : {}", report.getReportPath());
                log.info("Out-of-sync list (input for resync): {}", report.getOutOfSyncPath());
                log.info("Missing list (input for transfer --projects-file): {}",
                        report.getMissingPath());
            }

            return 0;

        } catch (IOException e) {
            log.error("Verify failed with I/O error: {}", e.getMessage(), e);
            return 1;
        }
    }

    /** Produce a list of ref differences between source and target. */
    public List<String> diffRefs(RefSnapshot src, RefSnapshot dst) {
        List<String> diffs = new ArrayList<>();
        diffRefKind(diffs, "branch", src.branches(), dst.branches());
        diffRefKind(diffs, "tag", src.tags(), dst.tags());

        if (!Objects.equals(src.defaultBranch(), dst.defaultBranch())) {
            diffs.add("default branch differs (src '" + src.defaultBranch()
                    + "' != dst '" + dst.defaultBranch() + "')");
        }
        return diffs;
    }

    private void diffRefKind(List<String> diffs, String kind,
                              Map<String, String> src, Map<String, String> dst) {
        // Missing on target
        src.keySet().stream().filter(n -> !dst.containsKey(n)).sorted()
                .forEach(n -> diffs.add(kind + " '" + n + "' missing on target"));
        // Only on target
        dst.keySet().stream().filter(n -> !src.containsKey(n)).sorted()
                .forEach(n -> diffs.add(kind + " '" + n + "' only on target"));
        // SHA differs
        src.keySet().stream().filter(dst::containsKey).sorted()
                .filter(n -> !src.get(n).equals(dst.get(n)))
                .forEach(n -> diffs.add(kind + " '" + n + "' SHA differs (src "
                        + src.get(n).substring(0, Math.min(8, src.get(n).length()))
                        + " != dst "
                        + dst.get(n).substring(0, Math.min(8, dst.get(n).length()))
                        + ")"));
    }

    /** Compare metadata counts between source and target. */
    public List<String> diffMetadata(MetadataCounts src, MetadataCounts dst) {
        List<String> diffs = new ArrayList<>();
        compareCount(diffs, "issues", src.issues(), dst.issues());
        compareCount(diffs, "merge_requests", src.mergeRequests(), dst.mergeRequests());
        compareCount(diffs, "labels", src.labels(), dst.labels());
        compareCount(diffs, "milestones", src.milestones(), dst.milestones());
        return diffs;
    }

    private void compareCount(List<String> diffs, String kind, int srcCount, int dstCount) {
        if (srcCount == -1 || dstCount == -1) return;
        if (srcCount != dstCount) {
            diffs.add(kind + " count differs (src " + srcCount + " != dst " + dstCount + ")");
        }
    }
}
