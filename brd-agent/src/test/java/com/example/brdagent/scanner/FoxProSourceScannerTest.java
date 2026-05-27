package com.example.brdagent.scanner;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

// PROVENANCE: unit test for FoxProSourceScanner — golden slice step 9

class FoxProSourceScannerTest {

    /**
     * The fixtures/scanner directory contains SAMPLE.prg and README.txt.
     * Only the .prg file should be returned.
     */
    @Test
    void scanFindsOnlyPrgFiles() throws IOException {
        Path fixtureDir = Path.of("src/test/resources/fixtures").toAbsolutePath().normalize();
        FoxProSourceScanner scanner = new FoxProSourceScanner(fixtureDir.toString());

        List<SourceFile> files = scanner.scan(List.of("scanner"));

        assertEquals(1, files.size(), "Should find exactly one .prg file");
    }

    @Test
    void scannedFileHasCorrectMetadata() throws IOException {
        Path fixtureDir = Path.of("src/test/resources/fixtures").toAbsolutePath().normalize();
        FoxProSourceScanner scanner = new FoxProSourceScanner(fixtureDir.toString());

        List<SourceFile> files = scanner.scan(List.of("scanner"));
        SourceFile file = files.get(0);

        assertEquals("scanner/SAMPLE.prg", file.path());
        assertEquals("prg", file.fileType());
    }

    @Test
    void scannedFileContentIsReadCorrectly() throws IOException {
        Path fixtureDir = Path.of("src/test/resources/fixtures").toAbsolutePath().normalize();
        FoxProSourceScanner scanner = new FoxProSourceScanner(fixtureDir.toString());

        List<SourceFile> files = scanner.scan(List.of("scanner"));
        String content = files.get(0).content();

        assertTrue(content.contains("PROCEDURE UpdateCustomerName"),
                "Content should contain the first procedure");
        assertTrue(content.contains("PROCEDURE GetCustomerBalance"),
                "Content should contain the second procedure");
    }

    @Test
    void scanOfNonexistentDirectoryReturnsEmpty() throws IOException {
        Path fixtureDir = Path.of("src/test/resources/fixtures").toAbsolutePath().normalize();
        FoxProSourceScanner scanner = new FoxProSourceScanner(fixtureDir.toString());

        List<SourceFile> files = scanner.scan(List.of("nonexistent"));

        assertTrue(files.isEmpty(), "Should return empty list for missing directory");
    }

    @Test
    void scanOfEmptyListReturnsEmpty() throws IOException {
        Path fixtureDir = Path.of("src/test/resources/fixtures").toAbsolutePath().normalize();
        FoxProSourceScanner scanner = new FoxProSourceScanner(fixtureDir.toString());

        List<SourceFile> files = scanner.scan(List.of());

        assertTrue(files.isEmpty(), "Should return empty list for empty input");
    }
}
