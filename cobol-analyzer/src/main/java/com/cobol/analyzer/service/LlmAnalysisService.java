package com.cobol.analyzer.service;

import com.cobol.analyzer.config.LlmConfig;
import com.cobol.analyzer.model.AnalysisResult;
import com.cobol.analyzer.model.FileDefinition;
import com.cobol.analyzer.model.ParagraphAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LlmAnalysisService.class);

    private final WebClient webClient;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper;

    public LlmAnalysisService(WebClient.Builder webClientBuilder, LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
        this.webClient = webClientBuilder
                .baseUrl(llmConfig.getBaseUrl())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public AnalysisResult analyzeCobolProgram(String fileName, String cobolCode) {
        long startTime = System.currentTimeMillis();

        AnalysisResult result = new AnalysisResult();
        result.setFileName(fileName);
        result.setRawCode(cobolCode);

        // Extract program ID
        String programId = extractProgramId(cobolCode);
        result.setProgramId(programId != null ? programId : fileName.replace(".cbl", "").replace(".CBL", ""));

        // Extract paragraphs
        List<ParagraphAnalysis> paragraphs = extractParagraphs(cobolCode);
        result.setParagraphDetails(paragraphs);
        result.setParagraphs(paragraphs.stream().map(ParagraphAnalysis::getName).toList());

        // Get overall summary from LLM
        String overallSummary = getOverallSummary(cobolCode);
        result.setOverallSummary(overallSummary);

        // Extract data structures and file operations
        result.setDataStructures(extractDataStructures(cobolCode));
        result.setFileOperations(extractFileOperations(cobolCode));

        // DB2
        result.setDb2Tables(extractDb2Tables(cobolCode));
        result.setDb2Cursors(extractDb2Cursors(cobolCode));
        result.setDb2Operations(extractDb2Operations(cobolCode));

        // IDMS
        result.setIdmsSchemas(extractIdmsSchemas(cobolCode));
        result.setIdmsRecords(extractIdmsRecords(cobolCode));
        result.setIdmsOperations(extractIdmsOperations(cobolCode));

        // CICS
        result.setCicsOperations(extractCicsOperations(cobolCode));

        // Copybooks
        result.setCopybooks(extractCopybooks(cobolCode));

        // Called programs
        result.setCalledPrograms(extractCalledPrograms(cobolCode));

        // File definitions (SELECT / FD)
        result.setFileDefinitions(extractFileDefinitions(cobolCode));

        // Determine program type
        result.setProgramType(determineProgramType(cobolCode));

        // Summarize key paragraphs (limit to avoid too many LLM calls)
        int maxParagraphs = Math.min(paragraphs.size(), 10);
        for (int i = 0; i < maxParagraphs; i++) {
            ParagraphAnalysis para = paragraphs.get(i);
            if (para.getCode() != null && para.getCode().length() > 20) {
                String summary = summarizeParagraph(para.getName(), para.getCode());
                para.setSummary(summary);
            }
        }

        result.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private String extractProgramId(String code) {
        Pattern pattern = Pattern.compile("PROGRAM-ID\\.\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private List<ParagraphAnalysis> extractParagraphs(String code) {
        List<ParagraphAnalysis> paragraphs = new ArrayList<>();

        // Find PROCEDURE DIVISION
        int procDivIndex = code.toUpperCase().indexOf("PROCEDURE DIVISION");
        if (procDivIndex == -1) {
            return paragraphs;
        }

        String procDiv = code.substring(procDivIndex);
        Pattern pattern = Pattern.compile("^\\s{7}([A-Z0-9][A-Z0-9-]*)\\.", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(procDiv);

        List<int[]> paragraphPositions = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            // Skip division/section keywords
            if (!name.contains("DIVISION") && !name.contains("SECTION")) {
                paragraphPositions.add(new int[]{matcher.start(), matcher.end(), name.hashCode()});
                ParagraphAnalysis para = new ParagraphAnalysis();
                para.setName(name);
                paragraphs.add(para);
            }
        }

        // Extract code for each paragraph
        for (int i = 0; i < paragraphPositions.size(); i++) {
            int start = paragraphPositions.get(i)[1];
            int end = (i + 1 < paragraphPositions.size()) ? paragraphPositions.get(i + 1)[0] : procDiv.length();
            String paraCode = procDiv.substring(start, end).trim();
            if (paraCode.length() > 500) {
                paraCode = paraCode.substring(0, 500) + "\n... (truncated)";
            }
            paragraphs.get(i).setCode(paraCode);
        }

        return paragraphs;
    }

    private List<String> extractDataStructures(String code) {
        List<String> structures = new ArrayList<>();
        Pattern pattern = Pattern.compile("^\\s*01\\s+([A-Z0-9-]+)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            structures.add(matcher.group(1));
        }
        return structures;
    }

    private List<String> extractFileOperations(String code) {
        List<String> operations = new ArrayList<>();
        String upperCode = code.toUpperCase();

        if (upperCode.contains("OPEN ")) operations.add("OPEN");
        if (upperCode.contains("CLOSE ")) operations.add("CLOSE");
        if (upperCode.contains("READ ")) operations.add("READ");
        if (upperCode.contains("WRITE ")) operations.add("WRITE");
        if (upperCode.contains("REWRITE ")) operations.add("REWRITE");
        if (upperCode.contains("DELETE ")) operations.add("DELETE");
        if (upperCode.contains("START ")) operations.add("START");
        if (upperCode.contains("EXEC SQL")) operations.add("SQL");
        if (upperCode.contains("EXEC CICS")) operations.add("CICS");
        if (upperCode.contains("EXEC IDMS")) operations.add("IDMS");

        return operations;
    }

    // ==================== DB2 Extraction ====================

    private List<String> extractDb2Tables(String code) {
        Set<String> tables = new LinkedHashSet<>();
        String upperCode = code.toUpperCase();

        // FROM table
        Pattern fromPattern = Pattern.compile("FROM\\s+([A-Z0-9_][A-Z0-9_.]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = fromPattern.matcher(upperCode);
        while (m.find()) {
            String table = m.group(1).trim();
            if (!table.equals("DUAL")) tables.add(table);
        }

        // INTO table (INSERT)
        Pattern insertPattern = Pattern.compile("INSERT\\s+INTO\\s+([A-Z0-9_][A-Z0-9_.]+)", Pattern.CASE_INSENSITIVE);
        m = insertPattern.matcher(upperCode);
        while (m.find()) tables.add(m.group(1).trim());

        // UPDATE table
        Pattern updatePattern = Pattern.compile("UPDATE\\s+([A-Z0-9_][A-Z0-9_.]+)", Pattern.CASE_INSENSITIVE);
        m = updatePattern.matcher(upperCode);
        while (m.find()) tables.add(m.group(1).trim());

        // DELETE FROM table
        Pattern deletePattern = Pattern.compile("DELETE\\s+FROM\\s+([A-Z0-9_][A-Z0-9_.]+)", Pattern.CASE_INSENSITIVE);
        m = deletePattern.matcher(upperCode);
        while (m.find()) tables.add(m.group(1).trim());

        // JOIN table
        Pattern joinPattern = Pattern.compile("JOIN\\s+([A-Z0-9_][A-Z0-9_.]+)", Pattern.CASE_INSENSITIVE);
        m = joinPattern.matcher(upperCode);
        while (m.find()) tables.add(m.group(1).trim());

        // DECLARE ... TABLE
        Pattern declareTablePattern = Pattern.compile("DECLARE\\s+([A-Z0-9_][A-Z0-9_.]+)\\s+TABLE", Pattern.CASE_INSENSITIVE);
        m = declareTablePattern.matcher(upperCode);
        while (m.find()) tables.add(m.group(1).trim());

        return new ArrayList<>(tables);
    }

    private List<String> extractDb2Cursors(String code) {
        Set<String> cursors = new LinkedHashSet<>();

        Pattern pattern = Pattern.compile("DECLARE\\s+([A-Z0-9_-]+)\\s+CURSOR", Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(code);
        while (m.find()) cursors.add(m.group(1).trim());

        return new ArrayList<>(cursors);
    }

    private List<String> extractDb2Operations(String code) {
        List<String> operations = new ArrayList<>();
        String upperCode = code.toUpperCase();

        if (upperCode.contains("EXEC SQL")) {
            if (upperCode.contains("SELECT ")) operations.add("SELECT");
            if (upperCode.contains("INSERT ")) operations.add("INSERT");
            if (upperCode.contains("UPDATE ")) operations.add("UPDATE");
            if (upperCode.contains("DELETE ")) operations.add("DELETE");
            if (upperCode.contains("OPEN ")) operations.add("CURSOR OPEN");
            if (upperCode.contains("FETCH ")) operations.add("FETCH");
            if (upperCode.contains("CLOSE ")) operations.add("CURSOR CLOSE");
            if (upperCode.contains("COMMIT")) operations.add("COMMIT");
            if (upperCode.contains("ROLLBACK")) operations.add("ROLLBACK");
            if (upperCode.contains("DECLARE") && upperCode.contains("CURSOR")) operations.add("DECLARE CURSOR");
            if (upperCode.contains("INCLUDE")) operations.add("INCLUDE");
        }

        return operations;
    }

    // ==================== IDMS Extraction ====================

    private List<String> extractIdmsSchemas(String code) {
        Set<String> schemas = new LinkedHashSet<>();

        // IDMS-CONTROL SECTION / SCHEMA
        Pattern schemaPattern = Pattern.compile("SCHEMA\\s+(?:IS\\s+)?([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = schemaPattern.matcher(code);
        while (m.find()) schemas.add(m.group(1).trim());

        // SUBSCHEMA-NAME
        Pattern subschemaPattern = Pattern.compile("SUBSCHEMA-NAME\\s+(?:IS\\s+)?([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        m = subschemaPattern.matcher(code);
        while (m.find()) schemas.add("SUBSCHEMA: " + m.group(1).trim());

        // DB section
        Pattern dbPattern = Pattern.compile("INVOKE\\s+SUBSCHEMA\\s+([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        m = dbPattern.matcher(code);
        while (m.find()) schemas.add("SUBSCHEMA: " + m.group(1).trim());

        return new ArrayList<>(schemas);
    }

    private List<String> extractIdmsRecords(String code) {
        Set<String> records = new LinkedHashSet<>();

        // OBTAIN/GET/FIND record
        Pattern obtainPattern = Pattern.compile("(?:OBTAIN|GET|FIND|STORE|MODIFY|ERASE)\\s+([A-Z0-9_-]+)\\s", Pattern.CASE_INSENSITIVE);
        Matcher m = obtainPattern.matcher(code);
        while (m.find()) {
            String rec = m.group(1).trim();
            // Filter out IDMS keywords
            if (!rec.matches("(?i)FIRST|LAST|NEXT|PRIOR|CURRENT|OWNER|WITHIN|CALC|ANY|DUPLICATE")) {
                records.add(rec);
            }
        }

        // BIND RECORD record-name
        Pattern bindPattern = Pattern.compile("BIND\\s+RECORD\\s+([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        m = bindPattern.matcher(code);
        while (m.find()) records.add(m.group(1).trim());

        return new ArrayList<>(records);
    }

    private List<String> extractIdmsOperations(String code) {
        List<String> operations = new ArrayList<>();
        String upperCode = code.toUpperCase();

        if (upperCode.contains("EXEC IDMS") || upperCode.contains("IDMS-CONTROL")) {
            if (upperCode.contains("OBTAIN")) operations.add("OBTAIN");
            if (upperCode.contains(" GET ")) operations.add("GET");
            if (upperCode.contains(" FIND ")) operations.add("FIND");
            if (upperCode.contains("STORE ")) operations.add("STORE");
            if (upperCode.contains("MODIFY ")) operations.add("MODIFY");
            if (upperCode.contains("ERASE ")) operations.add("ERASE");
            if (upperCode.contains("CONNECT ")) operations.add("CONNECT");
            if (upperCode.contains("DISCONNECT ")) operations.add("DISCONNECT");
            if (upperCode.contains("BIND ")) operations.add("BIND");
            if (upperCode.contains("READY ")) operations.add("READY");
            if (upperCode.contains("FINISH")) operations.add("FINISH");
            if (upperCode.contains("COMMIT")) operations.add("COMMIT");
            if (upperCode.contains("ROLLBACK")) operations.add("ROLLBACK");
        }

        return operations;
    }

    // ==================== CICS Extraction ====================

    private List<String> extractCicsOperations(String code) {
        List<String> operations = new ArrayList<>();
        String upperCode = code.toUpperCase();

        if (upperCode.contains("EXEC CICS")) {
            if (upperCode.contains("SEND MAP")) operations.add("SEND MAP");
            if (upperCode.contains("RECEIVE MAP")) operations.add("RECEIVE MAP");
            if (upperCode.contains("SEND TEXT")) operations.add("SEND TEXT");
            if (upperCode.contains("RETURN ")) operations.add("RETURN");
            if (upperCode.contains("XCTL ")) operations.add("XCTL");
            if (upperCode.contains("LINK ")) operations.add("LINK");
            if (upperCode.contains("READ ")) operations.add("READ");
            if (upperCode.contains("WRITE ")) operations.add("WRITE");
            if (upperCode.contains("REWRITE ")) operations.add("REWRITE");
            if (upperCode.contains("DELETE ")) operations.add("DELETE");
            if (upperCode.contains("STARTBR")) operations.add("STARTBR");
            if (upperCode.contains("READNEXT")) operations.add("READNEXT");
            if (upperCode.contains("READPREV")) operations.add("READPREV");
            if (upperCode.contains("ENDBR")) operations.add("ENDBR");
            if (upperCode.contains("SYNCPOINT")) operations.add("SYNCPOINT");
            if (upperCode.contains("HANDLE ")) operations.add("HANDLE");
            if (upperCode.contains("ASKTIME")) operations.add("ASKTIME");
            if (upperCode.contains("FORMATTIME")) operations.add("FORMATTIME");
            if (upperCode.contains("WRITEQ")) operations.add("WRITEQ");
            if (upperCode.contains("READQ")) operations.add("READQ");
            if (upperCode.contains("DELETEQ")) operations.add("DELETEQ");
        }

        return operations;
    }

    // ==================== Copybooks ====================

    private List<String> extractCopybooks(String code) {
        Set<String> copybooks = new LinkedHashSet<>();

        // COPY copybook-name.
        Pattern copyPattern = Pattern.compile("\\bCOPY\\s+([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = copyPattern.matcher(code);
        while (m.find()) copybooks.add(m.group(1).trim());

        // EXEC SQL INCLUDE copybook END-EXEC
        Pattern includePattern = Pattern.compile("INCLUDE\\s+([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        m = includePattern.matcher(code);
        while (m.find()) {
            String name = m.group(1).trim();
            if (!name.equalsIgnoreCase("SQLCA") && !name.equalsIgnoreCase("SQLDA")) {
                copybooks.add(name);
            } else {
                copybooks.add(name);
            }
        }

        return new ArrayList<>(copybooks);
    }

    // ==================== Called Programs ====================

    private List<String> extractCalledPrograms(String code) {
        Set<String> programs = new LinkedHashSet<>();

        // CALL 'PROGRAM-NAME'
        Pattern callPattern = Pattern.compile("CALL\\s+['\"]([A-Z0-9_-]+)['\"]", Pattern.CASE_INSENSITIVE);
        Matcher m = callPattern.matcher(code);
        while (m.find()) programs.add(m.group(1).trim());

        // CALL variable (dynamic call)
        Pattern dynCallPattern = Pattern.compile("CALL\\s+([A-Z0-9_-]+)(?:\\s+USING)", Pattern.CASE_INSENSITIVE);
        m = dynCallPattern.matcher(code);
        while (m.find()) {
            String name = m.group(1).trim();
            if (!name.startsWith("'") && !name.startsWith("\"")) {
                programs.add(name + " (dynamic)");
            }
        }

        // EXEC CICS LINK PROGRAM('name')
        Pattern cicsLinkPattern = Pattern.compile("LINK\\s+PROGRAM\\s*\\(\\s*['\"]?([A-Z0-9_-]+)['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);
        m = cicsLinkPattern.matcher(code);
        while (m.find()) programs.add(m.group(1).trim());

        // EXEC CICS XCTL PROGRAM('name')
        Pattern cicsXctlPattern = Pattern.compile("XCTL\\s+PROGRAM\\s*\\(\\s*['\"]?([A-Z0-9_-]+)['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);
        m = cicsXctlPattern.matcher(code);
        while (m.find()) programs.add(m.group(1).trim());

        // EXEC CICS RETURN TRANSID('name')
        Pattern transidPattern = Pattern.compile("TRANSID\\s*\\(\\s*['\"]?([A-Z0-9_-]+)['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);
        m = transidPattern.matcher(code);
        while (m.find()) programs.add("TRANSID: " + m.group(1).trim());

        return new ArrayList<>(programs);
    }

    // ==================== File Definitions ====================

    private List<FileDefinition> extractFileDefinitions(String code) {
        List<FileDefinition> files = new ArrayList<>();

        // SELECT logical-name ASSIGN TO physical-name
        Pattern selectPattern = Pattern.compile(
                "SELECT\\s+([A-Z0-9_-]+)\\s+ASSIGN\\s+(?:TO\\s+)?([A-Z0-9_-]+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = selectPattern.matcher(code);
        while (m.find()) {
            FileDefinition fd = new FileDefinition(m.group(1).trim(), m.group(2).trim());

            // Try to extract ORGANIZATION
            String afterSelect = code.substring(m.end(), Math.min(m.end() + 300, code.length()));
            Pattern orgPattern = Pattern.compile("ORGANIZATION\\s+(?:IS\\s+)?(INDEXED|SEQUENTIAL|RELATIVE)", Pattern.CASE_INSENSITIVE);
            Matcher orgMatcher = orgPattern.matcher(afterSelect);
            if (orgMatcher.find()) fd.setOrganization(orgMatcher.group(1).toUpperCase());

            // Access mode
            Pattern accessPattern = Pattern.compile("ACCESS\\s+(?:MODE\\s+)?(?:IS\\s+)?(SEQUENTIAL|RANDOM|DYNAMIC)", Pattern.CASE_INSENSITIVE);
            Matcher accessMatcher = accessPattern.matcher(afterSelect);
            if (accessMatcher.find()) fd.setAccessMode(accessMatcher.group(1).toUpperCase());

            // Record key
            Pattern keyPattern = Pattern.compile("RECORD\\s+KEY\\s+(?:IS\\s+)?([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
            Matcher keyMatcher = keyPattern.matcher(afterSelect);
            if (keyMatcher.find()) fd.setRecordKey(keyMatcher.group(1));

            // File status
            Pattern statusPattern = Pattern.compile("FILE\\s+STATUS\\s+(?:IS\\s+)?([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
            Matcher statusMatcher = statusPattern.matcher(afterSelect);
            if (statusMatcher.find()) fd.setFileStatus(statusMatcher.group(1));

            files.add(fd);
        }

        return files;
    }

    // ==================== Program Type Detection ====================

    private String determineProgramType(String code) {
        String upperCode = code.toUpperCase();
        List<String> types = new ArrayList<>();

        if (upperCode.contains("EXEC CICS")) types.add("CICS");
        if (upperCode.contains("EXEC SQL")) types.add("DB2");
        if (upperCode.contains("EXEC IDMS") || upperCode.contains("IDMS-CONTROL")) types.add("IDMS");
        if (upperCode.contains("EXEC DLI")) types.add("IMS/DLI");

        boolean hasBatchFiles = upperCode.contains("SELECT ") && upperCode.contains("ASSIGN ");
        if (types.isEmpty() && hasBatchFiles) types.add("Batch");
        if (types.isEmpty()) types.add("Batch");

        return String.join(" / ", types);
    }

    private String getOverallSummary(String cobolCode) {
        // Truncate code if too long
        String codeForAnalysis = cobolCode;
        if (codeForAnalysis.length() > 8000) {
            codeForAnalysis = codeForAnalysis.substring(0, 8000) + "\n... (truncated)";
        }

        String prompt = String.format("""
            You are a COBOL expert specializing in mainframe systems including DB2, IDMS, and CICS.
            Analyze the following COBOL program and provide a comprehensive summary.

            ```cobol
            %s
            ```

            Provide a summary that includes:
            1. The main purpose of this program (1-2 sentences)
            2. Key business functions it performs
            3. Data files, DB2 tables, or IDMS records it processes
            4. Any CICS screen interactions or transaction flow
            5. Programs it calls or is linked to
            6. Any important processing logic

            Keep the summary concise (4-6 sentences total).

            Summary:""", codeForAnalysis);

        return callLlm(prompt);
    }

    private String summarizeParagraph(String paragraphName, String code) {
        String prompt = String.format("""
            Summarize this COBOL paragraph in 1-2 sentences:

            Paragraph: %s
            ```cobol
            %s
            ```

            Summary:""", paragraphName, code);

        return callLlm(prompt);
    }

    private String callLlm(String prompt) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", llmConfig.getModel());
            request.put("prompt", prompt);
            request.put("max_tokens", llmConfig.getMaxTokens());
            request.put("temperature", llmConfig.getTemperature());
            request.put("stream", false);

            log.debug("Calling LLM with prompt length: {}", prompt.length());

            String response = webClient.post()
                    .uri("/v1/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            return extractResponse(response);
        } catch (Exception e) {
            log.error("Error calling LLM: {}", e.getMessage());
            return "Error generating summary: " + e.getMessage();
        }
    }

    private String extractResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("choices") && root.get("choices").isArray() && !root.get("choices").isEmpty()) {
                JsonNode firstChoice = root.get("choices").get(0);
                if (firstChoice.has("text")) {
                    return firstChoice.get("text").asText().trim();
                }
            }
            return "Unable to parse response";
        } catch (Exception e) {
            log.error("Error parsing LLM response: {}", e.getMessage());
            return "Error parsing response";
        }
    }
}
