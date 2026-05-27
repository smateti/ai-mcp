package com.example.brdagent.scanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// PROVENANCE: FoxProSourceScanner — walks source directories, filters by file type, produces SourceFile stream
// Golden slice: .prg files only

/**
 * Scans legacy FoxPro source directories and produces a list of {@link SourceFile}
 * records for the extraction agent to process.
 * <p>
 * Golden slice: only {@code .prg} files are included. Full file-type support
 * (.scx, .frx, .mnx, .dbf-schema.json) will be added in a later phase.
 */
@ApplicationScoped
public class FoxProSourceScanner {

    private static final Logger LOG = LoggerFactory.getLogger(FoxProSourceScanner.class);

    @Inject
    @ConfigProperty(name = "paths.source_root", defaultValue = "./legacy")
    private String sourceRoot;

    FoxProSourceScanner() {
        // CDI
    }

    // Test constructor
    FoxProSourceScanner(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    /**
     * Scans the given relative paths (under {@code paths.source_root}) for .prg files.
     *
     * @param relativePaths directories to scan, relative to source root
     * @return list of SourceFile records with content loaded
     * @throws IOException if any directory walk or file read fails
     */
    public List<SourceFile> scan(List<String> relativePaths) throws IOException {
        Path root = Path.of(sourceRoot).toAbsolutePath().normalize();
        LOG.info("Scanning source root: {}", root);

        return relativePaths.stream()
                .map(root::resolve)
                .flatMap(this::walkDirectory)
                .toList();
    }

    private Stream<SourceFile> walkDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            LOG.warn("Path is not a directory, skipping: {}", dir);
            return Stream.empty();
        }
        try {
            Path root = Path.of(sourceRoot).toAbsolutePath().normalize();
            return Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .filter(this::isPrgFile)
                    .map(path -> toSourceFile(path, root));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + dir, e);
        }
    }

    private boolean isPrgFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".prg");
    }

    private SourceFile toSourceFile(Path path, Path root) {
        try {
            String relativePath = root.relativize(path).toString().replace('\\', '/');
            String content = Files.readString(path, StandardCharsets.UTF_8);
            LOG.debug("Read {} ({} chars)", relativePath, content.length());
            return new SourceFile(relativePath, "prg", content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + path, e);
        }
    }
}
