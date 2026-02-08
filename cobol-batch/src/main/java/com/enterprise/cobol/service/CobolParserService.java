package com.enterprise.cobol.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CobolParserService {

    private static final Pattern PROGRAM_ID_PATTERN =
            Pattern.compile("(?i)PROGRAM-ID\\.\\s+([A-Za-z0-9-]+)");
    private static final Pattern AUTHOR_PATTERN =
            Pattern.compile("(?i)AUTHOR\\.\\s+(.+?)\\.");
    private static final Pattern COPY_PATTERN =
            Pattern.compile("(?i)\\bCOPY\\s+([A-Za-z0-9-]+)");
    private static final Pattern CALL_STATIC_PATTERN =
            Pattern.compile("(?i)\\bCALL\\s+'([A-Za-z0-9-]+)'");
    private static final Pattern CALL_DYNAMIC_PATTERN =
            Pattern.compile("(?i)\\bCALL\\s+([A-Za-z0-9-]+)(?!\\s*')");
    private static final Pattern EXEC_CICS_PATTERN =
            Pattern.compile("(?i)EXEC\\s+CICS\\s+(\\w+)(.*?)END-EXEC", Pattern.DOTALL);
    private static final Pattern EXEC_SQL_PATTERN =
            Pattern.compile("(?i)EXEC\\s+SQL(.*?)END-EXEC", Pattern.DOTALL);
    private static final Pattern EXEC_IDMS_PATTERN =
            Pattern.compile("(?i)EXEC\\s+IDMS(.*?)END-EXEC", Pattern.DOTALL);
    private static final Pattern PERFORM_PATTERN =
            Pattern.compile("(?i)\\bPERFORM\\s+([A-Za-z0-9-]+)");
    private static final Pattern PARAGRAPH_PATTERN =
            Pattern.compile("^       ([A-Z0-9][A-Z0-9-]*)(\\s+SECTION)?\\.\\s*$");
    private static final Pattern SQL_TABLE_PATTERN =
            Pattern.compile("(?i)(?:FROM|INTO|UPDATE|INSERT\\s+INTO|DELETE\\s+FROM)\\s+([A-Za-z0-9_-]+)");
    private static final Pattern CICS_MAP_PATTERN =
            Pattern.compile("(?i)MAP\\s*\\(\\s*'?([A-Za-z0-9-]+)'?\\s*\\)");

    // Business rule extraction patterns
    private static final Pattern CONDITION_88_PATTERN =
            Pattern.compile("^\\s+88\\s+([A-Za-z0-9-]+)\\s+VALUE[S]?\\s+(.+?)\\.$", Pattern.MULTILINE);
    private static final Pattern FILE_SELECT_PATTERN =
            Pattern.compile("(?i)SELECT\\s+([A-Za-z0-9-]+)\\s+ASSIGN\\s+(?:TO\\s+)?([A-Za-z0-9-\"]+)");
    private static final Pattern FD_PATTERN =
            Pattern.compile("(?i)^\\s*FD\\s+([A-Za-z0-9-]+)", Pattern.MULTILINE);

    public ParsedProgram parseProgram(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String source = String.join("\n", lines);
        String fileName = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");

        ParsedProgram program = new ParsedProgram();
        program.setFileName(fileName);
        program.setFilePath(file.toString());
        program.setSourceCode(source);
        program.setLineCount(lines.size());

        // Extract program ID
        Matcher m = PROGRAM_ID_PATTERN.matcher(source);
        program.setProgramName(m.find() ? m.group(1).toUpperCase() : fileName.toUpperCase());
        program.setProgramId(program.getProgramName());

        // Extract author
        m = AUTHOR_PATTERN.matcher(source);
        program.setAuthor(m.find() ? m.group(1).trim() : "UNKNOWN");

        // Extract copybooks
        Set<String> copybooks = new LinkedHashSet<>();
        m = COPY_PATTERN.matcher(source);
        while (m.find()) {
            copybooks.add(m.group(1).toUpperCase());
        }
        program.setCopybooks(new ArrayList<>(copybooks));

        // Extract CALL statements
        Set<String> calledPrograms = new LinkedHashSet<>();
        List<Dependency> dependencies = new ArrayList<>();

        m = CALL_STATIC_PATTERN.matcher(source);
        while (m.find()) {
            String target = m.group(1).toUpperCase();
            calledPrograms.add(target);
            dependencies.add(Dependency.builder()
                    .type("CALL").targetName(target)
                    .details(Map.of("isDynamic", false, "lineNumber", getLineNumber(source, m.start())))
                    .build());
        }
        m = CALL_DYNAMIC_PATTERN.matcher(source);
        while (m.find()) {
            String target = m.group(1).toUpperCase();
            if (!isCobolKeyword(target)) {
                calledPrograms.add(target);
                dependencies.add(Dependency.builder()
                        .type("CALL").targetName(target)
                        .details(Map.of("isDynamic", true, "lineNumber", getLineNumber(source, m.start())))
                        .build());
            }
        }
        program.setCalledPrograms(new ArrayList<>(calledPrograms));

        // Extract EXEC CICS
        boolean hasCics = false;
        m = EXEC_CICS_PATTERN.matcher(source);
        while (m.find()) {
            hasCics = true;
            String command = m.group(1).toUpperCase();
            String body = m.group(2);
            Map<String, Object> details = new HashMap<>();
            details.put("command", command);
            details.put("lineNumber", getLineNumber(source, m.start()));

            Matcher mapMatcher = CICS_MAP_PATTERN.matcher(body);
            if (mapMatcher.find()) {
                details.put("mapName", mapMatcher.group(1));
            }

            dependencies.add(Dependency.builder()
                    .type("CICS").targetName(command)
                    .details(details).build());
        }
        program.setUsesCics(hasCics);

        // Extract EXEC SQL - now capture full SQL text
        boolean hasDb2 = false;
        List<String> sqlStatements = new ArrayList<>();
        m = EXEC_SQL_PATTERN.matcher(source);
        while (m.find()) {
            hasDb2 = true;
            String sqlBody = m.group(1).trim();
            String sqlCommand = extractSqlCommand(sqlBody);
            String cleanSql = cleanCobolSource(sqlBody);
            Map<String, Object> details = new HashMap<>();
            details.put("command", sqlCommand);
            details.put("lineNumber", getLineNumber(source, m.start()));
            details.put("fullSql", cleanSql);

            Matcher tableMatcher = SQL_TABLE_PATTERN.matcher(sqlBody);
            if (tableMatcher.find()) {
                details.put("tableName", tableMatcher.group(1));
            }

            dependencies.add(Dependency.builder()
                    .type("DB2").targetName(sqlCommand)
                    .details(details).build());

            // Store full SQL for program-level
            if (!sqlCommand.equals("INCLUDE") && !sqlCommand.equals("DECLARE")) {
                sqlStatements.add(sqlCommand + ": " + cleanSql);
            }
        }
        program.setUsesDb2(hasDb2);
        program.setSqlStatements(sqlStatements);

        // Extract EXEC IDMS
        boolean hasIdms = false;
        m = EXEC_IDMS_PATTERN.matcher(source);
        while (m.find()) {
            hasIdms = true;
            String idmsBody = m.group(1).trim();
            String idmsCommand = idmsBody.split("\\s+")[0].toUpperCase();
            dependencies.add(Dependency.builder()
                    .type("IDMS").targetName(idmsCommand)
                    .details(Map.of("command", idmsCommand, "lineNumber", getLineNumber(source, m.start())))
                    .build());
        }
        program.setUsesIdms(hasIdms);

        // Add COPY dependencies
        for (String cpyName : copybooks) {
            dependencies.add(Dependency.builder()
                    .type("COPY").targetName(cpyName)
                    .details(Map.of()).build());
        }

        program.setDependencies(dependencies);

        // Extract 88-level condition names
        List<String> conditionNames = new ArrayList<>();
        m = CONDITION_88_PATTERN.matcher(source);
        while (m.find()) {
            String condName = m.group(1).trim();
            String condValue = m.group(2).trim();
            conditionNames.add(condName + " = " + condValue);
        }
        program.setConditionNames(conditionNames);

        // Extract data structures from WORKING-STORAGE
        List<String> dataStructures = extractDataStructures(lines);
        program.setDataStructures(dataStructures);

        // Extract file operations (SELECT/ASSIGN + FD)
        List<String> fileOperations = new ArrayList<>();
        m = FILE_SELECT_PATTERN.matcher(source);
        while (m.find()) {
            fileOperations.add("SELECT " + m.group(1) + " ASSIGN TO " + m.group(2));
        }
        m = FD_PATTERN.matcher(source);
        while (m.find()) {
            fileOperations.add("FD " + m.group(1));
        }
        program.setFileOperations(fileOperations);

        // Extract paragraphs
        List<ParsedParagraph> paragraphs = extractParagraphs(lines, source);
        program.setParagraphs(paragraphs);
        program.setParagraphCount(paragraphs.size());

        // Extract PERFORM calls per paragraph (internal call graph, not external dependencies)
        for (ParsedParagraph para : paragraphs) {
            Set<String> performs = new LinkedHashSet<>();
            Matcher perfMatcher = PERFORM_PATTERN.matcher(para.getSourceCode());
            while (perfMatcher.find()) {
                String target = perfMatcher.group(1).toUpperCase();
                if (!isCobolKeyword(target)) {
                    performs.add(target);
                }
            }
            para.setPerformsCalls(new ArrayList<>(performs));

            // Check paragraph features
            String paraSource = para.getSourceCode();
            para.setHasExecCics(paraSource.toUpperCase().contains("EXEC CICS"));
            para.setHasExecSql(paraSource.toUpperCase().contains("EXEC SQL"));
            para.setHasCallStatement(paraSource.toUpperCase().contains("CALL "));

            // Extract business rules from paragraph
            List<String> businessRules = extractBusinessRules(paraSource);
            para.setBusinessRules(businessRules);

            // Extract data access from paragraph (SQL statements)
            List<String> dataAccess = new ArrayList<>();
            Matcher sqlMatcher = EXEC_SQL_PATTERN.matcher(paraSource);
            while (sqlMatcher.find()) {
                String sql = cleanCobolSource(sqlMatcher.group(1).trim());
                dataAccess.add(sql);
            }
            para.setDataAccess(dataAccess);

            // Extract calculations
            List<String> calculations = extractCalculations(paraSource);
            para.setCalculations(calculations);
        }

        // Classify program type
        program.setProgramType(classifyProgramType(program));

        return program;
    }

    /**
     * Extract business rules: IF conditions, EVALUATE decision tables
     */
    private List<String> extractBusinessRules(String source) {
        List<String> rules = new ArrayList<>();
        String upper = source.toUpperCase();

        // Extract IF conditions - get the condition part between IF and THEN/newline
        extractIfConditions(source, rules);

        // Extract EVALUATE statements (decision tables)
        extractEvaluateStatements(source, rules);

        return rules;
    }

    private void extractIfConditions(String source, List<String> rules) {
        String[] lines = source.split("\n");
        StringBuilder currentIf = null;
        int depth = 0;

        for (String line : lines) {
            String trimmed = line.length() > 6 ? line.substring(6).trim() : line.trim();
            if (trimmed.startsWith("*")) continue; // skip comments
            String upper = trimmed.toUpperCase();

            if (upper.contains("IF ") && !upper.startsWith("END-IF")) {
                // Start capturing IF condition
                int ifIdx = upper.indexOf("IF ");
                String afterIf = trimmed.substring(ifIdx + 3).trim();
                currentIf = new StringBuilder(afterIf);
                depth++;
            } else if (currentIf != null) {
                // Check if condition continues or ends
                if (upper.contains("PERFORM ") || upper.contains("MOVE ") ||
                    upper.contains("DISPLAY ") || upper.contains("SET ") ||
                    upper.contains("ADD ") || upper.contains("SUBTRACT ") ||
                    upper.contains("COMPUTE ") || upper.contains("CALL ") ||
                    upper.contains("EXEC ") || upper.contains("GO TO") ||
                    upper.contains("ELSE") || upper.contains("END-IF") ||
                    upper.contains("NEXT SENTENCE") || upper.trim().isEmpty()) {
                    // Condition ended, save it
                    String condition = cleanCondition(currentIf.toString());
                    if (condition.length() > 5 && condition.length() < 500) {
                        rules.add("IF " + condition);
                    }
                    currentIf = null;
                    depth = 0;
                } else {
                    // Multi-line condition continues
                    currentIf.append(" ").append(trimmed);
                }
            }
        }
        // Save any remaining condition
        if (currentIf != null) {
            String condition = cleanCondition(currentIf.toString());
            if (condition.length() > 5 && condition.length() < 500) {
                rules.add("IF " + condition);
            }
        }
    }

    private void extractEvaluateStatements(String source, List<String> rules) {
        String upper = source.toUpperCase();
        int pos = 0;
        while (true) {
            int evalStart = upper.indexOf("EVALUATE ", pos);
            if (evalStart < 0) break;
            int evalEnd = upper.indexOf("END-EVALUATE", evalStart);
            if (evalEnd < 0) break;

            String evalBlock = source.substring(evalStart, evalEnd + 12);
            String cleanEval = cleanCobolSource(evalBlock);
            if (cleanEval.length() < 1000) {
                rules.add(cleanEval);
            }
            pos = evalEnd + 12;
        }
    }

    /**
     * Extract COMPUTE and arithmetic statements
     */
    private List<String> extractCalculations(String source) {
        List<String> calcs = new ArrayList<>();
        String[] lines = source.split("\n");

        for (String line : lines) {
            String trimmed = line.length() > 6 ? line.substring(6).trim() : line.trim();
            if (trimmed.startsWith("*")) continue;
            String upper = trimmed.toUpperCase();

            if (upper.startsWith("COMPUTE ")) {
                calcs.add(cleanCondition(trimmed));
            } else if (upper.startsWith("ADD ") || upper.startsWith("SUBTRACT ") ||
                       upper.startsWith("MULTIPLY ") || upper.startsWith("DIVIDE ")) {
                calcs.add(cleanCondition(trimmed));
            }
        }
        return calcs;
    }

    /**
     * Extract WORKING-STORAGE 01-level records with their fields
     */
    private List<String> extractDataStructures(List<String> lines) {
        List<String> structures = new ArrayList<>();
        boolean inWorkingStorage = false;
        boolean inRecord = false;
        String currentRecord = null;
        StringBuilder recordDef = null;
        int fieldCount = 0;

        for (String line : lines) {
            String trimmed = line.trim().toUpperCase();

            if (trimmed.startsWith("WORKING-STORAGE SECTION")) {
                inWorkingStorage = true;
                continue;
            }
            if (inWorkingStorage && (trimmed.startsWith("PROCEDURE DIVISION") ||
                    trimmed.startsWith("LINKAGE SECTION") ||
                    trimmed.startsWith("LOCAL-STORAGE SECTION"))) {
                // Save last record
                if (inRecord && recordDef != null && fieldCount > 1) {
                    structures.add(recordDef.toString().trim());
                }
                break;
            }
            if (!inWorkingStorage) continue;

            // Check for 01-level
            if (line.length() >= 10 && trimmed.matches("^01\\s+[A-Z0-9-]+.*")) {
                // Save previous record
                if (inRecord && recordDef != null && fieldCount > 1) {
                    structures.add(recordDef.toString().trim());
                }
                // Start new record
                String recordName = trimmed.split("\\s+")[1].replace(".", "");
                // Skip FILLER and single-value items
                if (recordName.equals("FILLER")) {
                    inRecord = false;
                    continue;
                }
                currentRecord = recordName;
                recordDef = new StringBuilder("01 " + currentRecord);
                fieldCount = 0;
                inRecord = true;
            } else if (inRecord && trimmed.matches("^\\d{2}\\s+.*")) {
                // Sub-field
                fieldCount++;
                String fieldLine = trimmed.replaceAll("\\s+", " ").replace(".", "");
                // Only include fields with PIC or VALUE clauses (meaningful fields)
                if (fieldLine.contains("PIC") || fieldLine.contains("VALUE")) {
                    recordDef.append(" | ").append(fieldLine);
                }
                // Also capture 88-level condition names as they define business states
                if (trimmed.startsWith("88 ")) {
                    recordDef.append(" | ").append(fieldLine);
                }
            }
        }
        // Save last record
        if (inRecord && recordDef != null && fieldCount > 1) {
            structures.add(recordDef.toString().trim());
        }

        return structures;
    }

    private List<ParsedParagraph> extractParagraphs(List<String> lines, String source) {
        List<ParsedParagraph> paragraphs = new ArrayList<>();
        boolean inProcedureDivision = false;
        int paraStart = -1;
        String currentParaName = null;
        String currentType = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim().toUpperCase();

            if (trimmed.startsWith("PROCEDURE DIVISION")) {
                inProcedureDivision = true;
                continue;
            }
            if (!inProcedureDivision) continue;

            // Check for paragraph/section header
            Matcher pm = PARAGRAPH_PATTERN.matcher(line);
            if (pm.matches()) {
                // Save previous paragraph
                if (currentParaName != null && paraStart >= 0) {
                    paragraphs.add(buildParagraph(currentParaName, currentType, paraStart, i - 1, lines));
                }
                currentParaName = pm.group(1).toUpperCase();
                currentType = pm.group(2) != null ? "SECTION" : "PARAGRAPH";
                paraStart = i;
            }
        }
        // Save last paragraph
        if (currentParaName != null && paraStart >= 0) {
            paragraphs.add(buildParagraph(currentParaName, currentType, paraStart, lines.size() - 1, lines));
        }

        return paragraphs;
    }

    private ParsedParagraph buildParagraph(String name, String type, int startLine, int endLine, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= Math.min(endLine, lines.size() - 1); i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return ParsedParagraph.builder()
                .paragraphName(name)
                .type(type)
                .startLine(startLine + 1)
                .endLine(endLine + 1)
                .lineCount(endLine - startLine + 1)
                .sourceCode(sb.toString())
                .build();
    }

    private String classifyProgramType(ParsedProgram program) {
        if (program.isUsesCics()) return "CICS";
        boolean hasFileOps = program.getDependencies().stream()
                .anyMatch(d -> "CICS".equals(d.getType()) ||
                        program.getSourceCode().toUpperCase().contains("OPEN INPUT") ||
                        program.getSourceCode().toUpperCase().contains("OPEN OUTPUT") ||
                        program.getSourceCode().toUpperCase().contains("READ ") ||
                        program.getSourceCode().toUpperCase().contains("WRITE "));
        if (hasFileOps || program.getProgramName().startsWith("CB")) return "BATCH";
        return "SUBROUTINE";
    }

    private String extractSqlCommand(String sqlBody) {
        String upper = sqlBody.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("DECLARE")) return "DECLARE";
        if (upper.startsWith("INCLUDE")) return "INCLUDE";
        return upper.split("\\s+")[0];
    }

    private int getLineNumber(String source, int charIndex) {
        int line = 1;
        for (int i = 0; i < charIndex && i < source.length(); i++) {
            if (source.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Clean COBOL source: remove line numbers (cols 1-6), indicator (col 7), collapse whitespace
     */
    private String cleanCobolSource(String source) {
        StringBuilder sb = new StringBuilder();
        for (String line : source.split("\n")) {
            String content = line.length() > 6 ? line.substring(6).trim() : line.trim();
            if (!content.startsWith("*") && !content.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(content);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * Clean up extracted condition text
     */
    private String cleanCondition(String text) {
        return text.replaceAll("\\s+", " ").replace("\n", " ").trim()
                .replaceAll("\\.$", ""); // remove trailing period
    }

    private boolean isCobolKeyword(String word) {
        return Set.of("VARYING", "UNTIL", "TIMES", "THRU", "THROUGH", "WITH", "TEST",
                "BEFORE", "AFTER", "END-PERFORM", "SECTION", "EXIT", "STOP", "RUN",
                "MOVE", "IF", "ELSE", "END-IF", "EVALUATE", "WHEN", "DISPLAY",
                "ACCEPT", "ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "COMPUTE",
                "STRING", "UNSTRING", "INSPECT", "INITIALIZE", "SET").contains(word.toUpperCase());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedProgram {
        private String programId;
        private String programName;
        private String programType;
        private String author;
        private String fileName;
        private String filePath;
        private String sourceCode;
        private int lineCount;
        private int paragraphCount;
        private boolean usesCics;
        private boolean usesDb2;
        private boolean usesIdms;
        private boolean usesIms;
        private boolean usesMq;
        private List<String> calledPrograms;
        private List<String> copybooks;
        private List<ParsedParagraph> paragraphs;
        private List<Dependency> dependencies;
        // New business rule fields
        private List<String> dataStructures;
        private List<String> sqlStatements;
        private List<String> conditionNames;
        private List<String> fileOperations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedParagraph {
        private String paragraphName;
        private String type;
        private String sourceCode;
        private int startLine;
        private int endLine;
        private int lineCount;
        private List<String> performsCalls;
        private boolean hasExecSql;
        private boolean hasExecCics;
        private boolean hasCallStatement;
        // New business rule fields
        private List<String> businessRules;
        private List<String> dataAccess;
        private List<String> calculations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dependency {
        private String type;
        private String targetName;
        private String callingContext;
        private Map<String, Object> details;
    }
}
