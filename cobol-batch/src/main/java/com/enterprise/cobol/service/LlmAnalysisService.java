package com.enterprise.cobol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LlmAnalysisService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.analysis-model}")
    private String analysisModel;

    @Value("${llm.timeout-seconds}")
    private int timeoutSeconds;

    @Value("${llm.temperature}")
    private double temperature;

    @Value("${llm.max-tokens}")
    private int maxTokens;

    private static final String PROGRAM_SYSTEM_PROMPT = """
            You are a senior business analyst specializing in mainframe modernization and
            microservice decomposition. Analyze this COBOL program and describe the BUSINESS
            LOGIC it implements.

            Focus on:
            1. What business capability does this program provide? (e.g., "processes credit
               card transactions", "manages customer account updates")
            2. What business entities does it operate on? (reference the copybook data
               structures to identify entities like Customer, Account, Transaction, etc.)
            3. What business rules and validations does it enforce?
            4. What business decisions or calculations does it make?
            5. What other programs/services does it depend on and why?
            6. If this were a microservice, what would its API look like?

            Do NOT describe:
            - COBOL syntax or language constructs
            - File I/O operations (OPEN, CLOSE, READ, WRITE)
            - Technical error handling or ABEND processing
            - Screen layout or BMS map details
            """;

    public static final String BUSINESS_RULES_SYSTEM_PROMPT = """
            You are analyzing COBOL source code. List the business rules you find.

            A business rule is domain logic that a business analyst would care about:
            - Validations on business data (account numbers, amounts, dates, limits)
            - Calculations (interest, fees, balances, totals)
            - Threshold/limit checks (credit limits, minimum amounts)
            - Eligibility conditions (who qualifies for what)
            - Business decisions (approve/reject, categorize, route)
            - Status transitions (active/inactive, open/closed)

            Skip technical/infrastructure concerns: file I/O status checks, CICS SEND/RECEIVE,
            screen handling, ABEND processing, program flow control (GOBACK, STOP RUN).

            Output one rule per line. Start each line with a category tag in brackets:
            [VALIDATION] Account number must be numeric and non-zero
            [CALCULATION] Monthly interest = principal * annual rate / 12
            [LIMIT_CHECK] Transaction rejected if amount exceeds credit limit

            If this program has no business rules (e.g. it is a utility program), output:
            NO_BUSINESS_RULES
            """;

    private static final String PARAGRAPH_SYSTEM_PROMPT = """
            You are a senior business analyst. Explain what business logic this COBOL paragraph
            implements as if describing a method in a microservice.

            Describe:
            1. What business action or decision is performed
            2. What business rules are applied (validations, calculations, conditions)
            3. What business data is read, created, or modified
            4. What would this look like as a REST API operation or service method

            Do NOT describe COBOL syntax, file I/O, or screen operations.
            """;

    public LlmAnalysisService(@Value("${llm.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public String analyzeProgram(CobolParserService.ParsedProgram program,
                                  Map<String, String> copybooks,
                                  List<CobolParserService.Dependency> dependencies) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Program: ").append(program.getProgramName())
                .append(" (Type: ").append(program.getProgramType())
                .append(", Author: ").append(program.getAuthor()).append(")\n\n");

        // Add dependencies summary
        if (!dependencies.isEmpty()) {
            userPrompt.append("Dependencies:\n");
            Map<String, List<CobolParserService.Dependency>> byType = dependencies.stream()
                    .collect(Collectors.groupingBy(CobolParserService.Dependency::getType));
            byType.forEach((type, deps) -> {
                userPrompt.append("  ").append(type).append(": ");
                userPrompt.append(deps.stream()
                        .map(CobolParserService.Dependency::getTargetName)
                        .distinct().collect(Collectors.joining(", ")));
                userPrompt.append("\n");
            });
            userPrompt.append("\n");
        }

        // Add extracted data structures from WORKING-STORAGE
        if (program.getDataStructures() != null && !program.getDataStructures().isEmpty()) {
            userPrompt.append("Data Structures (WORKING-STORAGE records):\n");
            int dsChars = 0;
            for (String ds : program.getDataStructures()) {
                if (dsChars + ds.length() > 1500) break;
                userPrompt.append("  ").append(ds).append("\n");
                dsChars += ds.length();
            }
            userPrompt.append("\n");
        }

        // Add SQL statements
        if (program.getSqlStatements() != null && !program.getSqlStatements().isEmpty()) {
            userPrompt.append("SQL Statements:\n");
            for (String sql : program.getSqlStatements()) {
                userPrompt.append("  ").append(truncate(sql, 300)).append("\n");
            }
            userPrompt.append("\n");
        }

        // Add 88-level condition names (business state definitions)
        if (program.getConditionNames() != null && !program.getConditionNames().isEmpty()) {
            userPrompt.append("Business Conditions (88-level):\n");
            int condChars = 0;
            for (String cond : program.getConditionNames()) {
                if (condChars > 800) break;
                userPrompt.append("  ").append(cond).append("\n");
                condChars += cond.length();
            }
            userPrompt.append("\n");
        }

        // Add file operations
        if (program.getFileOperations() != null && !program.getFileOperations().isEmpty()) {
            userPrompt.append("File Operations:\n");
            for (String fo : program.getFileOperations()) {
                userPrompt.append("  ").append(fo).append("\n");
            }
            userPrompt.append("\n");
        }

        // Add copybook data structures (truncated)
        if (!copybooks.isEmpty()) {
            userPrompt.append("Copybook Data Structures:\n");
            int cpyChars = 0;
            for (var entry : copybooks.entrySet()) {
                String content = entry.getValue();
                if (cpyChars + content.length() > 1500) {
                    content = content.substring(0, Math.max(0, 1500 - cpyChars)) + "\n... (truncated)";
                }
                userPrompt.append("--- ").append(entry.getKey()).append(" ---\n")
                        .append(content).append("\n");
                cpyChars += content.length();
                if (cpyChars > 1500) break;
            }
            userPrompt.append("\n");
        }

        // Add key paragraphs (truncated to fit context)
        userPrompt.append("Key Paragraphs:\n");
        int paraChars = 0;
        for (var para : program.getParagraphs()) {
            String paraCode = para.getSourceCode();
            if (paraChars + paraCode.length() > 3000) {
                if (paraChars < 2500) {
                    paraCode = paraCode.substring(0, Math.max(0, 3000 - paraChars)) + "\n... (truncated)";
                } else {
                    userPrompt.append("  ").append(para.getParagraphName()).append(" (source omitted)\n");
                    continue;
                }
            }
            userPrompt.append("--- ").append(para.getParagraphName()).append(" ---\n")
                    .append(paraCode).append("\n");
            paraChars += paraCode.length();
        }

        return callLlm(PROGRAM_SYSTEM_PROMPT, userPrompt.toString());
    }

    public String analyzeParagraph(String paragraphCode, String paragraphName,
                                    String programContext, Map<String, String> copybooks) {
        return analyzeParagraph(paragraphCode, paragraphName, programContext, copybooks, null, null, null);
    }

    public String analyzeParagraph(String paragraphCode, String paragraphName,
                                    String programContext, Map<String, String> copybooks,
                                    List<String> businessRules, List<String> dataAccess,
                                    List<String> calculations) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Program context: ").append(programContext).append("\n\n");
        userPrompt.append("Paragraph: ").append(paragraphName).append("\n");
        userPrompt.append("Code:\n").append(truncate(paragraphCode, 2000)).append("\n");

        // Include extracted business rules
        if (businessRules != null && !businessRules.isEmpty()) {
            userPrompt.append("\nExtracted Business Rules (conditions/decisions):\n");
            for (String rule : businessRules) {
                userPrompt.append("  ").append(truncate(rule, 200)).append("\n");
            }
        }

        if (dataAccess != null && !dataAccess.isEmpty()) {
            userPrompt.append("\nSQL/Data Access:\n");
            for (String sql : dataAccess) {
                userPrompt.append("  ").append(truncate(sql, 200)).append("\n");
            }
        }

        if (calculations != null && !calculations.isEmpty()) {
            userPrompt.append("\nCalculations:\n");
            for (String calc : calculations) {
                userPrompt.append("  ").append(calc).append("\n");
            }
        }

        if (!copybooks.isEmpty()) {
            userPrompt.append("\nRelevant data structures:\n");
            int chars = 0;
            for (var entry : copybooks.entrySet()) {
                if (chars > 1000) break;
                String content = truncate(entry.getValue(), 500);
                userPrompt.append("--- ").append(entry.getKey()).append(" ---\n")
                        .append(content).append("\n");
                chars += content.length();
            }
        }

        return callLlm(PARAGRAPH_SYSTEM_PROMPT, userPrompt.toString());
    }

    public List<String> extractBusinessRules(CobolParserService.ParsedProgram program,
                                               Map<String, String> copybooks) {
        return extractBusinessRules(program, copybooks, null);
    }

    public List<String> extractBusinessRules(CobolParserService.ParsedProgram program,
                                               Map<String, String> copybooks,
                                               String customPrompt) {
        String systemPrompt = (customPrompt != null && !customPrompt.isBlank())
                ? customPrompt : BUSINESS_RULES_SYSTEM_PROMPT;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Program: ").append(program.getProgramName())
                .append(" (Type: ").append(program.getProgramType()).append(")\n\n");

        // Data structures help the LLM understand field meanings
        if (program.getDataStructures() != null && !program.getDataStructures().isEmpty()) {
            userPrompt.append("Data Structures:\n");
            int chars = 0;
            for (String ds : program.getDataStructures()) {
                if (chars > 1500) break;
                userPrompt.append("  ").append(ds).append("\n");
                chars += ds.length();
            }
            userPrompt.append("\n");
        }

        // 88-level conditions define business states
        if (program.getConditionNames() != null && !program.getConditionNames().isEmpty()) {
            userPrompt.append("Business State Definitions (88-level):\n");
            int chars = 0;
            for (String cond : program.getConditionNames()) {
                if (chars > 800) break;
                userPrompt.append("  ").append(cond).append("\n");
                chars += cond.length();
            }
            userPrompt.append("\n");
        }

        // Copybook data structures
        if (!copybooks.isEmpty()) {
            userPrompt.append("Copybook Data Structures:\n");
            int cpyChars = 0;
            for (var entry : copybooks.entrySet()) {
                String content = entry.getValue();
                if (cpyChars + content.length() > 1500) {
                    content = content.substring(0, Math.max(0, 1500 - cpyChars)) + "\n...(truncated)";
                }
                userPrompt.append("--- ").append(entry.getKey()).append(" ---\n")
                        .append(content).append("\n");
                cpyChars += content.length();
                if (cpyChars > 1500) break;
            }
            userPrompt.append("\n");
        }

        // Feed actual paragraph source code - this is where business rules live
        userPrompt.append("COBOL Source Code (read this to find business rules):\n");
        int paraChars = 0;
        for (var para : program.getParagraphs()) {
            String code = para.getSourceCode();
            if (paraChars + code.length() > 6000) {
                if (paraChars < 5500) {
                    code = code.substring(0, Math.max(0, 6000 - paraChars)) + "\n...(truncated)";
                } else {
                    continue;
                }
            }
            userPrompt.append("--- ").append(para.getParagraphName()).append(" ---\n")
                    .append(code).append("\n");
            paraChars += code.length();
        }

        log.info("  Calling LLM for business rules extraction: {} (custom prompt: {})",
                program.getProgramName(), customPrompt != null && !customPrompt.isBlank());
        String response = callLlm(systemPrompt, userPrompt.toString());
        log.info("  LLM business rules raw response for {} ({} chars): {}",
                program.getProgramName(),
                response != null ? response.length() : 0,
                response != null && response.length() > 1000 ? response.substring(0, 1000) + "..." : response);

        // Parse response into individual rules
        List<String> rules = new ArrayList<>();
        if (response != null) {
            // Only skip if response is essentially just NO_BUSINESS_RULES (not buried in other text)
            String trimmedResponse = response.trim().toUpperCase();
            if (trimmedResponse.equals("NO_BUSINESS_RULES")
                    || trimmedResponse.startsWith("NO_BUSINESS_RULES\n")
                    || trimmedResponse.startsWith("NO_BUSINESS_RULES.")) {
                log.info("  LLM reported no business rules for {}", program.getProgramName());
                return rules;
            }
            for (String line : response.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("---") || trimmed.startsWith("```")) continue;

                // Strip leading numbers, bullets, markdown bold
                String cleaned = trimmed.replaceFirst("^\\d+[.)\\-]\\s*", "")
                        .replaceFirst("^[-*]\\s*", "")
                        .replaceAll("\\*\\*", "")
                        .replaceAll("__", "")
                        .trim();
                if (cleaned.isEmpty() || cleaned.length() < 15) continue;

                String upper = cleaned.toUpperCase();

                // Skip LLM preamble/header lines
                if (upper.startsWith("BASED ON") || upper.startsWith("HERE ARE")
                        || upper.startsWith("THE FOLLOWING") || upper.startsWith("BELOW ARE")
                        || upper.startsWith("NOTE:") || upper.startsWith("THE PROGRAM")
                        || upper.startsWith("THIS PROGRAM") || upper.startsWith("BUSINESS RULES")) continue;

                // Skip "no rules found" filler
                if (upper.contains("NO EXPLICIT") || upper.contains("NO RULES")
                        || upper.contains("NO BUSINESS RULE") || upper.contains("NONE FOUND")
                        || upper.contains("NOT IDENTIFIED") || upper.contains("NO_BUSINESS_RULES")) continue;

                // Normalize category tag format: [CATEGORY] or CATEGORY: or CATEGORY -
                // Try to extract and normalize to [CATEGORY] format
                cleaned = normalizeRuleLine(cleaned);
                if (cleaned == null) continue;

                upper = cleaned.toUpperCase();

                // Filter obvious technical noise
                if (upper.contains("FILE-STATUS") || upper.contains("FILE STATUS")
                        || upper.contains("ABEND") || upper.contains("GOBACK")
                        || upper.contains("APPL-AOK") || upper.contains("APPL-EOF")
                        || upper.contains("DFHRESP") || upper.contains("SEND MAP")
                        || upper.contains("RECEIVE MAP") || upper.contains("END-OF-FILE")
                        || upper.contains("EOF FLAG")) {
                    log.debug("    Filtered technical noise: {}", cleaned);
                    continue;
                }

                // Filter data movement (not rules, just copying fields)
                if (upper.contains("MOVED DIRECTLY FROM") || upper.contains("COPIED DIRECTLY")
                        || upper.contains("MOVED FROM THE INPUT") || upper.contains("COPIED FROM THE INPUT")) {
                    log.debug("    Filtered data movement: {}", cleaned);
                    continue;
                }

                // Filter hallucinated/hedging rules
                if (upper.contains("NOT EXPLICITLY SHOWN") || upper.contains("IMPLIED BY")
                        || upper.contains("FOR DEBUGGING")) {
                    log.debug("    Filtered hedging/debug: {}", cleaned);
                    continue;
                }

                rules.add(cleaned);
            }
        }

        log.info("  Extracted {} business rules for {}", rules.size(), program.getProgramName());
        return rules;
    }

    /**
     * Normalize a rule line to [CATEGORY] description format.
     * Handles: [CATEGORY] desc, CATEGORY: desc, CATEGORY - desc
     * Returns null if the line doesn't contain a recognizable category.
     */
    private String normalizeRuleLine(String line) {
        String[] validCategories = {
                "VALIDATION", "CALCULATION", "LIMIT_CHECK", "ELIGIBILITY",
                "BUSINESS_DECISION", "STATUS_TRANSITION", "RATE_FEE", "THRESHOLD",
                "DATA_MAPPING", "LIMIT CHECK"
        };

        // Already in [CATEGORY] format
        if (line.startsWith("[")) {
            int end = line.indexOf(']');
            if (end > 0) {
                String cat = line.substring(1, end).toUpperCase().trim();
                for (String valid : validCategories) {
                    if (cat.equals(valid) || cat.replace(" ", "_").equals(valid)) {
                        String desc = line.substring(end + 1).trim();
                        if (desc.startsWith(":") || desc.startsWith("-")) desc = desc.substring(1).trim();
                        if (desc.length() < 10) return null;
                        return "[" + valid + "] " + desc;
                    }
                }
            }
            return null;
        }

        // CATEGORY: description or CATEGORY - description format
        String upper = line.toUpperCase();
        for (String cat : validCategories) {
            if (upper.startsWith(cat + ":") || upper.startsWith(cat + " :")) {
                String desc = line.substring(line.toUpperCase().indexOf(":") + 1).trim();
                if (desc.length() < 10) return null;
                return "[" + cat + "] " + desc;
            }
            if (upper.startsWith(cat + " -") || upper.startsWith(cat + "-")) {
                int dashIdx = upper.indexOf("-", cat.length());
                String desc = line.substring(dashIdx + 1).trim();
                if (desc.length() < 10) return null;
                return "[" + cat + "] " + desc;
            }
        }

        // No recognized category — check if line still looks like a rule (has substance)
        // Accept lines that don't have a category but describe a rule
        if (line.contains(":")) {
            // Could be "Category: Description" with non-standard category
            // Just keep it as-is if it looks substantive
            String beforeColon = line.substring(0, line.indexOf(":")).trim().toUpperCase();
            // If what's before the colon looks like a short label, treat it as a category
            if (beforeColon.length() < 25 && beforeColon.length() > 2
                    && !beforeColon.contains(".")
                    && !beforeColon.matches(".*\\d{3,}.*")) {
                return "[BUSINESS_DECISION] " + line;
            }
        }

        return null;
    }

    private String callLlm(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> request = Map.of(
                    "model", analysisModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );

            String response = webClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            return "Analysis failed: " + e.getMessage();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... (truncated)";
    }
}
