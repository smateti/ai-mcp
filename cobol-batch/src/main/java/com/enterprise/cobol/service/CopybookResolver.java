package com.enterprise.cobol.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CopybookResolver {

    private static final Pattern COPY_PATTERN = Pattern.compile("(?i)\\bCOPY\\s+([A-Za-z0-9-]+)");
    private static final int MAX_DEPTH = 3;

    public Map<String, Path> buildCopybookMap(List<Path> copybookDirs) {
        Map<String, Path> copybookMap = new HashMap<>();
        for (Path dir : copybookDirs) {
            if (!Files.isDirectory(dir)) {
                log.warn("Copybook directory not found: {}", dir);
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        String name = file.getFileName().toString();
                        String baseName = name.replaceFirst("\\.[^.]+$", "").toUpperCase();
                        copybookMap.putIfAbsent(baseName, file);
                    }
                }
            } catch (IOException e) {
                log.error("Error scanning copybook directory: {}", dir, e);
            }
        }
        log.info("Built copybook map with {} entries from {} directories", copybookMap.size(), copybookDirs.size());
        return copybookMap;
    }

    public Map<String, String> resolveAll(String programSource, Map<String, Path> copybookMap) {
        Map<String, String> resolved = new LinkedHashMap<>();
        Set<String> referenced = extractCopyNames(programSource);

        for (String copyName : referenced) {
            resolveCopybook(copyName, copybookMap, resolved, 0);
        }
        return resolved;
    }

    private void resolveCopybook(String copyName, Map<String, Path> copybookMap,
                                  Map<String, String> resolved, int depth) {
        if (depth > MAX_DEPTH || resolved.containsKey(copyName)) return;

        Path path = copybookMap.get(copyName.toUpperCase());
        if (path == null) {
            log.debug("Copybook not found: {}", copyName);
            return;
        }

        try {
            String content = Files.readString(path);
            resolved.put(copyName.toUpperCase(), content);

            // Resolve nested copies
            Set<String> nested = extractCopyNames(content);
            for (String nestedCopy : nested) {
                resolveCopybook(nestedCopy, copybookMap, resolved, depth + 1);
            }
        } catch (IOException e) {
            log.error("Error reading copybook {}: {}", copyName, e.getMessage());
        }
    }

    private Set<String> extractCopyNames(String source) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = COPY_PATTERN.matcher(source);
        while (m.find()) {
            names.add(m.group(1).toUpperCase());
        }
        return names;
    }
}
