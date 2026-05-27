package com.example.brdagent.scanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// PROVENANCE: FoxProSourceScanner — walks source directories, filters by file type, produces SourceFile stream

/**
 * Scans legacy FoxPro source directories and produces a list of {@link SourceFile}
 * records for the extraction agent to process.
 * <p>
 * Supports all FoxPro file types:
 * <ul>
 *   <li>Text: .prg (program), .h (header)</li>
 *   <li>Forms: .scx (form structure), .sct (form memo/code), .sca (form backup)</li>
 *   <li>Reports: .frx (report structure), .frt (report memo), .fra (report backup)</li>
 *   <li>Menus: .mnx (menu structure), .mnt (menu memo), .mna (menu backup)</li>
 *   <li>Classes: .vcx (class structure), .vct (class memo/code), .vca (class backup)</li>
 * </ul>
 * FoxPro text files (.prg, .h) are read with Windows-1252 charset.
 * Binary-hybrid files are read with ISO-8859-1 to safely capture embedded
 * text without throwing on binary bytes.
 * Files that cannot be read are skipped with a warning.
 */
@ApplicationScoped
public class FoxProSourceScanner {

    private static final Logger LOG = LoggerFactory.getLogger(FoxProSourceScanner.class);

    /** Text-based FoxPro source extensions → file type label. */
    private static final Map<String, String> TEXT_EXTENSIONS = Map.of(
            ".prg", "prg",
            ".h",   "h"
    );

    /** Binary-hybrid FoxPro extensions → file type label. */
    private static final Map<String, String> BINARY_EXTENSIONS = Map.ofEntries(
            // Forms / Screens
            Map.entry(".scx", "scx"),
            Map.entry(".sct", "sct"),
            Map.entry(".sca", "sca"),
            // Reports
            Map.entry(".frx", "frx"),
            Map.entry(".frt", "frt"),
            Map.entry(".fra", "fra"),
            // Menus
            Map.entry(".mnx", "mnx"),
            Map.entry(".mnt", "mnt"),
            Map.entry(".mna", "mna"),
            // Visual Class Libraries
            Map.entry(".vcx", "vcx"),
            Map.entry(".vct", "vct"),
            Map.entry(".vca", "vca")
    );

    /** All supported extensions for quick membership check. */
    private static final Set<String> ALL_EXTENSIONS;
    static {
        var all = new java.util.HashSet<>(TEXT_EXTENSIONS.keySet());
        all.addAll(BINARY_EXTENSIONS.keySet());
        ALL_EXTENSIONS = Set.copyOf(all);
    }

    /** Windows-1252 for FoxPro text source files. */
    private static final Charset CP1252 = Charset.forName("windows-1252");

    /** ISO-8859-1 for binary-hybrid files — never throws on any byte value. */
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

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
     * Scans the given relative paths (under {@code paths.source_root}) for FoxPro files.
     *
     * @param relativePaths directories to scan, relative to source root
     * @return list of SourceFile records with content loaded (unreadable files skipped)
     * @throws IOException if a directory walk itself fails
     */
    public List<SourceFile> scan(List<String> relativePaths) throws IOException {
        Path root = Path.of(sourceRoot).toAbsolutePath().normalize();
        LOG.info("Scanning source root: {}", root);

        return relativePaths.stream()
                .map(root::resolve)
                .flatMap(this::walkDirectory)
                .filter(Objects::nonNull)
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
                    .filter(this::isSupportedFile)
                    .map(path -> toSourceFileSafe(path, root));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + dir, e);
        }
    }

    private boolean isSupportedFile(Path path) {
        String ext = extensionOf(path);
        return ALL_EXTENSIONS.contains(ext);
    }

    /**
     * Reads a file into a SourceFile, returning null on failure instead of throwing.
     */
    private SourceFile toSourceFileSafe(Path path, Path root) {
        try {
            String ext = extensionOf(path);
            String fileType = TEXT_EXTENSIONS.getOrDefault(ext,
                    BINARY_EXTENSIONS.getOrDefault(ext, "unknown"));
            Charset charset = TEXT_EXTENSIONS.containsKey(ext) ? CP1252 : LATIN1;

            String relativePath = root.relativize(path).toString().replace('\\', '/');
            String content = Files.readString(path, charset);

            if (content.isEmpty()) {
                LOG.warn("Empty file, skipping: {}", relativePath);
                return null;
            }

            LOG.debug("Read {} ({} chars, charset={})", relativePath, content.length(), charset);
            return new SourceFile(relativePath, fileType, content);
        } catch (Exception e) {
            LOG.warn("Failed to read file, skipping: {} — {}", path, e.getMessage());
            return null;
        }
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
}
