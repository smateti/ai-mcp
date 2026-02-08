package com.enterprise.cobol.service;

import com.enterprise.cobol.service.CobolParserService.Dependency;
import com.enterprise.cobol.service.CobolParserService.ParsedParagraph;
import com.enterprise.cobol.service.CobolParserService.ParsedProgram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CobolParserServiceTest {

    private CobolParserService parserService;

    @TempDir
    Path tempDir;

    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        parserService = new CobolParserService();
        // Copy test COBOL file from classpath to temp dir
        String source = new String(getClass().getResourceAsStream("/test-program.cbl").readAllBytes());
        testFile = tempDir.resolve("TESTPROG.cbl");
        Files.writeString(testFile, source);
    }

    @Test
    void testParseProgramId() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getProgramName()).isEqualTo("TESTPROG");
        assertThat(program.getProgramId()).isEqualTo("TESTPROG");
    }

    @Test
    void testParseAuthor() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getAuthor()).isEqualTo("TEST AUTHOR");
    }

    @Test
    void testParseCopyStatements() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getCopybooks()).contains("COMMON");
    }

    @Test
    void testParseStaticCallStatements() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getCalledPrograms()).contains("SUBPROG1");

        List<String> callTargets = program.getDependencies().stream()
                .filter(d -> "CALL".equals(d.getType()))
                .map(Dependency::getTargetName)
                .collect(Collectors.toList());
        assertThat(callTargets).contains("SUBPROG1");
    }

    @Test
    void testParseExecCics() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.isUsesCics()).isTrue();

        List<Dependency> cicsDeps = program.getDependencies().stream()
                .filter(d -> "CICS".equals(d.getType()))
                .collect(Collectors.toList());
        assertThat(cicsDeps).isNotEmpty();
        assertThat(cicsDeps.get(0).getTargetName()).isEqualTo("SEND");
        assertThat(cicsDeps.get(0).getDetails()).containsKey("mapName");
    }

    @Test
    void testParseExecSql() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.isUsesDb2()).isTrue();

        List<Dependency> sqlDeps = program.getDependencies().stream()
                .filter(d -> "DB2".equals(d.getType()))
                .collect(Collectors.toList());
        assertThat(sqlDeps).isNotEmpty();
        assertThat(sqlDeps.get(0).getTargetName()).isEqualTo("SELECT");
        assertThat(sqlDeps.get(0).getDetails()).containsKey("tableName");
        assertThat(sqlDeps.get(0).getDetails().get("tableName")).isEqualTo("ACCOUNTS");
    }

    @Test
    void testParseExecIdms() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.isUsesIdms()).isTrue();

        List<Dependency> idmsDeps = program.getDependencies().stream()
                .filter(d -> "IDMS".equals(d.getType()))
                .collect(Collectors.toList());
        assertThat(idmsDeps).isNotEmpty();
        assertThat(idmsDeps.get(0).getTargetName()).isEqualTo("BIND");
    }

    @Test
    void testParseSqlStatements() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getSqlStatements()).isNotEmpty();
        assertThat(program.getSqlStatements().get(0)).startsWith("SELECT:");
    }

    @Test
    void testParseCopyDependencies() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        List<Dependency> copyDeps = program.getDependencies().stream()
                .filter(d -> "COPY".equals(d.getType()))
                .collect(Collectors.toList());
        assertThat(copyDeps).isNotEmpty();
        assertThat(copyDeps.stream().map(Dependency::getTargetName))
                .contains("COMMON");
    }

    @Test
    void testParseParagraphs() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        List<ParsedParagraph> paragraphs = program.getParagraphs();
        assertThat(paragraphs).isNotEmpty();

        List<String> paraNames = paragraphs.stream()
                .map(ParsedParagraph::getParagraphName)
                .collect(Collectors.toList());
        assertThat(paraNames).contains("MAIN-PROGRAM", "INIT-PROGRAM",
                "PROCESS-TRANSACTION", "ERROR-HANDLER");
    }

    @Test
    void testParagraphSourceCodeCaptured() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        ParsedParagraph processPara = program.getParagraphs().stream()
                .filter(p -> "PROCESS-TRANSACTION".equals(p.getParagraphName()))
                .findFirst().orElseThrow();

        assertThat(processPara.getSourceCode()).contains("EXEC SQL");
        assertThat(processPara.isHasExecSql()).isTrue();
        assertThat(processPara.getStartLine()).isGreaterThan(0);
        assertThat(processPara.getEndLine()).isGreaterThanOrEqualTo(processPara.getStartLine());
    }

    @Test
    void testParagraphPerformsCalls() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        ParsedParagraph mainPara = program.getParagraphs().stream()
                .filter(p -> "MAIN-PROGRAM".equals(p.getParagraphName()))
                .findFirst().orElseThrow();

        assertThat(mainPara.getPerformsCalls())
                .contains("INIT-PROGRAM", "PROCESS-TRANSACTION");
    }

    @Test
    void testClassifyProgramTypeCics() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        // Has EXEC CICS → should be classified as CICS
        assertThat(program.getProgramType()).isEqualTo("CICS");
    }

    @Test
    void testClassifyProgramTypeBatch() throws IOException {
        // Create a batch-style program (no CICS, has file ops)
        String batchSource = """
               IDENTIFICATION DIVISION.
               PROGRAM-ID. CBBATCH1.
               PROCEDURE DIVISION.
               MAIN-PARA.
                   OPEN INPUT ACCT-FILE.
                   READ ACCT-FILE.
                   CLOSE ACCT-FILE.
                   STOP RUN.
        """;
        Path batchFile = tempDir.resolve("CBBATCH1.cbl");
        Files.writeString(batchFile, batchSource);
        ParsedProgram program = parserService.parseProgram(batchFile);
        assertThat(program.getProgramType()).isEqualTo("BATCH");
    }

    @Test
    void testClassifyProgramTypeSubroutine() throws IOException {
        String subSource = """
               IDENTIFICATION DIVISION.
               PROGRAM-ID. UTILPROG.
               PROCEDURE DIVISION.
               MAIN-PARA.
                   MOVE 'A' TO WS-VAR.
                   STOP RUN.
        """;
        Path subFile = tempDir.resolve("UTILPROG.cbl");
        Files.writeString(subFile, subSource);
        ParsedProgram program = parserService.parseProgram(subFile);
        assertThat(program.getProgramType()).isEqualTo("SUBROUTINE");
    }

    @Test
    void testExtractConditionNames() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getConditionNames()).isNotEmpty();
        // Should contain 88-level conditions like WS-CREDIT, WS-DEBIT, etc.
        assertThat(program.getConditionNames().stream().anyMatch(
                c -> c.contains("WS-CREDIT") || c.contains("WS-DEBIT")
        )).isTrue();
    }

    @Test
    void testExtractDataStructures() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getDataStructures()).isNotEmpty();
        // Should have WS-TRANSACTION-REC structure
        assertThat(program.getDataStructures().stream()
                .anyMatch(ds -> ds.contains("WS-TRANSACTION-REC"))).isTrue();
    }

    @Test
    void testExtractFileOperations() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getFileOperations()).isNotEmpty();
        assertThat(program.getFileOperations().stream()
                .anyMatch(fo -> fo.contains("ACCT-FILE"))).isTrue();
    }

    @Test
    void testExtractCalculations() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        // PROCESS-TRANSACTION paragraph has a COMPUTE statement
        ParsedParagraph processPara = program.getParagraphs().stream()
                .filter(p -> "PROCESS-TRANSACTION".equals(p.getParagraphName()))
                .findFirst().orElseThrow();
        assertThat(processPara.getCalculations()).isNotEmpty();
        assertThat(processPara.getCalculations().stream()
                .anyMatch(c -> c.toUpperCase().contains("COMPUTE"))).isTrue();
    }

    @Test
    void testLineCountCorrect() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getLineCount()).isGreaterThan(0);
    }

    @Test
    void testParagraphCountMatchesList() throws IOException {
        ParsedProgram program = parserService.parseProgram(testFile);
        assertThat(program.getParagraphCount()).isEqualTo(program.getParagraphs().size());
    }

    @Test
    void testProgramWithNoProgramId() throws IOException {
        // Program without PROGRAM-ID should default to filename
        String noIdSource = """
               IDENTIFICATION DIVISION.
               PROCEDURE DIVISION.
               MAIN-PARA.
                   STOP RUN.
        """;
        Path file = tempDir.resolve("NOIDPROG.cbl");
        Files.writeString(file, noIdSource);
        ParsedProgram program = parserService.parseProgram(file);
        assertThat(program.getProgramName()).isEqualTo("NOIDPROG");
    }
}
