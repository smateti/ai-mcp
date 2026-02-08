package com.enterprise.cobol.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CopybookResolverTest {

    private CopybookResolver resolver;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        resolver = new CopybookResolver();
    }

    @Test
    void testBuildCopybookMap() throws IOException {
        Path cpyDir = tempDir.resolve("cpy");
        Files.createDirectories(cpyDir);
        Files.writeString(cpyDir.resolve("COMMON.cpy"), "       01  WS-COMMON PIC X(10).");
        Files.writeString(cpyDir.resolve("ACCTDATA.cpy"), "       01  ACCT-DATA PIC X(20).");

        Map<String, Path> map = resolver.buildCopybookMap(List.of(cpyDir));

        assertThat(map).hasSize(2);
        assertThat(map).containsKey("COMMON");
        assertThat(map).containsKey("ACCTDATA");
    }

    @Test
    void testBuildCopybookMapCaseInsensitive() throws IOException {
        Path cpyDir = tempDir.resolve("cpy");
        Files.createDirectories(cpyDir);
        Files.writeString(cpyDir.resolve("common.cpy"), "       01  WS-COMMON PIC X(10).");

        Map<String, Path> map = resolver.buildCopybookMap(List.of(cpyDir));
        assertThat(map).containsKey("COMMON");
    }

    @Test
    void testBuildCopybookMapSkipsMissingDir() {
        Path missingDir = tempDir.resolve("nonexistent");
        Map<String, Path> map = resolver.buildCopybookMap(List.of(missingDir));
        assertThat(map).isEmpty();
    }

    @Test
    void testResolveAll() throws IOException {
        Path cpyDir = tempDir.resolve("cpy");
        Files.createDirectories(cpyDir);
        Files.writeString(cpyDir.resolve("COMMON.cpy"), "       01  WS-COMMON PIC X(10).");

        Map<String, Path> copybookMap = resolver.buildCopybookMap(List.of(cpyDir));

        String programSource = "       COPY COMMON.";
        Map<String, String> resolved = resolver.resolveAll(programSource, copybookMap);

        assertThat(resolved).hasSize(1);
        assertThat(resolved).containsKey("COMMON");
        assertThat(resolved.get("COMMON")).contains("WS-COMMON");
    }

    @Test
    void testResolveNestedCopybooks() throws IOException {
        Path cpyDir = tempDir.resolve("cpy");
        Files.createDirectories(cpyDir);
        // OUTER copies INNER
        Files.writeString(cpyDir.resolve("OUTER.cpy"), "       01 OUTER-REC.\n           COPY INNER.");
        Files.writeString(cpyDir.resolve("INNER.cpy"), "       05  INNER-FLD PIC X(5).");

        Map<String, Path> copybookMap = resolver.buildCopybookMap(List.of(cpyDir));

        String programSource = "       COPY OUTER.";
        Map<String, String> resolved = resolver.resolveAll(programSource, copybookMap);

        assertThat(resolved).containsKey("OUTER");
        assertThat(resolved).containsKey("INNER");
    }

    @Test
    void testResolveMissingCopybook() throws IOException {
        Map<String, Path> copybookMap = Map.of();
        String programSource = "       COPY MISSING.";
        Map<String, String> resolved = resolver.resolveAll(programSource, copybookMap);
        assertThat(resolved).isEmpty();
    }

    @Test
    void testResolveMultipleCopybooks() throws IOException {
        Path cpyDir = tempDir.resolve("cpy");
        Files.createDirectories(cpyDir);
        Files.writeString(cpyDir.resolve("CPY1.cpy"), "       01  REC1 PIC X(10).");
        Files.writeString(cpyDir.resolve("CPY2.cpy"), "       01  REC2 PIC X(10).");

        Map<String, Path> copybookMap = resolver.buildCopybookMap(List.of(cpyDir));

        String programSource = "       COPY CPY1.\n       COPY CPY2.";
        Map<String, String> resolved = resolver.resolveAll(programSource, copybookMap);

        assertThat(resolved).hasSize(2);
        assertThat(resolved).containsKey("CPY1");
        assertThat(resolved).containsKey("CPY2");
    }

    @Test
    void testResolveFromMultipleDirectories() throws IOException {
        Path cpyDir1 = tempDir.resolve("cpy1");
        Path cpyDir2 = tempDir.resolve("cpy2");
        Files.createDirectories(cpyDir1);
        Files.createDirectories(cpyDir2);
        Files.writeString(cpyDir1.resolve("CPY1.cpy"), "       01  REC1 PIC X(10).");
        Files.writeString(cpyDir2.resolve("CPY2.cpy"), "       01  REC2 PIC X(10).");

        Map<String, Path> copybookMap = resolver.buildCopybookMap(List.of(cpyDir1, cpyDir2));

        assertThat(copybookMap).hasSize(2);
    }

    @Test
    void testNoDuplicateResolution() throws IOException {
        Path cpyDir = tempDir.resolve("cpy");
        Files.createDirectories(cpyDir);
        Files.writeString(cpyDir.resolve("COMMON.cpy"), "       01  WS-COMMON PIC X(10).");

        Map<String, Path> copybookMap = resolver.buildCopybookMap(List.of(cpyDir));

        // Program references same copybook twice
        String programSource = "       COPY COMMON.\n       COPY COMMON.";
        Map<String, String> resolved = resolver.resolveAll(programSource, copybookMap);

        assertThat(resolved).hasSize(1);
    }
}
