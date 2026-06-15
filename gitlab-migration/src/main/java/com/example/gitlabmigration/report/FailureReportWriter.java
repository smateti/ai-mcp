package com.example.gitlabmigration.report;

import com.example.gitlabmigration.util.TimeUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only CSV failure report shared by all three phases.
 * Every phase writes failures in the same shape:
 *   timestamp, phase, project_path, reason
 *
 * The file is opened in append mode and flushed per row.
 */
public class FailureReportWriter implements AutoCloseable {

    private static final String HEADER = "timestamp,phase,project_path,reason";

    private final Path filePath;
    private final BufferedWriter writer;

    public FailureReportWriter(String reportsDir, String filename) throws IOException {
        this.filePath = Path.of(reportsDir, filename);
        Files.createDirectories(filePath.getParent());

        boolean isNew = !Files.exists(filePath);
        this.writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (isNew) {
            writer.write(HEADER);
            writer.newLine();
            writer.flush();
        }
    }

    /** Record one failure row and flush immediately. */
    public synchronized void add(String phase, String projectPath, String reason) {
        try {
            writer.write(String.join(",",
                    TimeUtils.utcNow(),
                    csvEscape(phase),
                    csvEscape(projectPath),
                    csvEscape(reason)));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write failure report", e);
        }
    }

    public Path getFilePath() {
        return filePath;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    static String csvEscape(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
