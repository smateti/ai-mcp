package com.example.gitlabmigration.report;

import com.example.gitlabmigration.model.MetadataCounts;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.gitlabmigration.report.FailureReportWriter.csvEscape;

/**
 * Writes the Phase 2 verification outputs:
 * - sync_report_{ts}.csv (full comparison)
 * - out_of_sync_{ts}.txt (drifted paths for resync input)
 * - missing_{ts}.txt (missing paths for transfer retry)
 */
public class SyncReportWriter implements AutoCloseable {

    private static final String CSV_HEADER = String.join(",",
            "project_path", "status",
            "branches_src", "branches_dst",
            "tags_src", "tags_dst",
            "issues_src", "issues_dst",
            "mrs_src", "mrs_dst",
            "labels_src", "labels_dst",
            "milestones_src", "milestones_dst",
            "differences");

    private final BufferedWriter reportWriter;
    private final BufferedWriter outOfSyncWriter;
    private final BufferedWriter missingWriter;
    private final Path reportPath;
    private final Path outOfSyncPath;
    private final Path missingPath;

    public SyncReportWriter(String reportsDir, String timestamp) throws IOException {
        Path dir = Path.of(reportsDir);
        Files.createDirectories(dir);

        reportPath = dir.resolve("sync_report_" + timestamp + ".csv");
        outOfSyncPath = dir.resolve("out_of_sync_" + timestamp + ".txt");
        missingPath = dir.resolve("missing_" + timestamp + ".txt");

        reportWriter = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8);
        outOfSyncWriter = Files.newBufferedWriter(outOfSyncPath, StandardCharsets.UTF_8);
        missingWriter = Files.newBufferedWriter(missingPath, StandardCharsets.UTF_8);

        reportWriter.write(CSV_HEADER);
        reportWriter.newLine();
    }

    /** Write a row for a missing project. */
    public void writeMissing(String path, int srcBranches, int srcTags) throws IOException {
        String row = String.join(",",
                csvEscape(path), "missing",
                String.valueOf(srcBranches), "-",
                String.valueOf(srcTags), "-",
                metaPlaceholders(),
                csvEscape("project not found on target"));
        reportWriter.write(row);
        reportWriter.newLine();

        missingWriter.write(path);
        missingWriter.newLine();
    }

    /** Write a row for an out-of-sync project. */
    public void writeOutOfSync(String path, int srcBranches, int dstBranches,
                                int srcTags, int dstTags,
                                MetadataCounts srcMeta, MetadataCounts dstMeta,
                                List<String> diffs) throws IOException {
        String diffStr = diffs.stream().limit(10).collect(Collectors.joining("; "));
        String row = String.join(",",
                csvEscape(path), "out_of_sync",
                String.valueOf(srcBranches), String.valueOf(dstBranches),
                String.valueOf(srcTags), String.valueOf(dstTags),
                metaColumns(srcMeta, dstMeta),
                csvEscape(diffStr));
        reportWriter.write(row);
        reportWriter.newLine();

        outOfSyncWriter.write(path);
        outOfSyncWriter.newLine();
    }

    /** Write a row for an in-sync project. */
    public void writeInSync(String path, int srcBranches, int dstBranches,
                             int srcTags, int dstTags,
                             MetadataCounts srcMeta, MetadataCounts dstMeta) throws IOException {
        String row = String.join(",",
                csvEscape(path), "in_sync",
                String.valueOf(srcBranches), String.valueOf(dstBranches),
                String.valueOf(srcTags), String.valueOf(dstTags),
                metaColumns(srcMeta, dstMeta),
                "");
        reportWriter.write(row);
        reportWriter.newLine();
    }

    private String metaColumns(MetadataCounts src, MetadataCounts dst) {
        if (src == null || dst == null) return metaPlaceholders();
        return String.join(",",
                metaCell(src.issues()), metaCell(dst.issues()),
                metaCell(src.mergeRequests()), metaCell(dst.mergeRequests()),
                metaCell(src.labels()), metaCell(dst.labels()),
                metaCell(src.milestones()), metaCell(dst.milestones()));
    }

    private String metaPlaceholders() {
        return "-,-,-,-,-,-,-,-";
    }

    private String metaCell(int value) {
        return value == -1 ? "?" : String.valueOf(value);
    }

    public Path getReportPath() { return reportPath; }
    public Path getOutOfSyncPath() { return outOfSyncPath; }
    public Path getMissingPath() { return missingPath; }

    @Override
    public void close() throws IOException {
        reportWriter.close();
        outOfSyncWriter.close();
        missingWriter.close();
    }
}
