package com.nystax.nimba.analyzer.service;

import com.nystax.nimba.analyzer.model.BatchExitUsage;
import com.nystax.nimba.analyzer.model.FunctionCallInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JavaSourceAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceAnalyzer.class);

    private static final Pattern BATCH_EXIT_PATTERN = Pattern.compile(
            "throw\\s+new\\s+BatchExitException\\s*\\(([^)]+)\\)");
    private static final Pattern NIMBA_STATUS_PATTERN = Pattern.compile(
            "NimbaStatus\\.(\\w+)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern WAS_CLIENT_USAGE = Pattern.compile(
            "(\\w+Client)\\s*\\.\\s*(\\w+)\\s*\\(");
    private static final Pattern WAS_IMPORT = Pattern.compile(
            "import\\s+gov\\.nystax\\.was\\.functions\\.");
    private static final Pattern DB_HELPER_CONSTRUCTOR = Pattern.compile(
            "new\\s+NimbusDatabaseHelperImpl\\s*\\(\\s*(\\w+|\"[^\"]+\")\\s*\\)");
    private static final Pattern VARIABLE_ASSIGN = Pattern.compile(
            "(\\w+)\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern NIMBUS_FUNCTION_IMPORT = Pattern.compile(
            "import\\s+gov\\.nystax\\.nimbus\\.function\\.client\\.(\\w+Function)");
    private static final Pattern NIMBUS_FUNCTION_CALL = Pattern.compile(
            "(\\w+Function)\\.execute\\s*\\(");
    private static final Pattern SQL_QUERY_PATTERN = Pattern.compile(
            "(?:executeQuery|executeUpdate|prepareStatement|prepareCall)\\s*\\(\\s*\"([^\"]+)\"");

    public List<BatchExitUsage> findBatchExitUsages(List<Path> javaFiles) {
        List<BatchExitUsage> usages = new ArrayList<>();
        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile);
                String className = extractSimpleClassName(javaFile);
                String[] lines = content.split("\n");

                for (int i = 0; i < lines.length; i++) {
                    Matcher m = BATCH_EXIT_PATTERN.matcher(lines[i]);
                    if (m.find()) {
                        usages.add(parseBatchExitArgs(className, i + 1, m.group(1)));
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to read file for exit analysis: {}", javaFile);
            }
        }
        return usages;
    }

    public List<FunctionCallInfo> findFunctionCalls(Path javaFile) {
        List<FunctionCallInfo> calls = new ArrayList<>();
        try {
            String content = Files.readString(javaFile);
            if (!WAS_IMPORT.matcher(content).find()) {
                return calls;
            }

            String className = extractSimpleClassName(javaFile);
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = WAS_CLIENT_USAGE.matcher(lines[i]);
                while (m.find()) {
                    calls.add(new FunctionCallInfo(m.group(1), m.group(2), className, i + 1));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read file for function call analysis: {}", javaFile);
        }
        return calls;
    }

    public List<FunctionCallInfo> findAllFunctionCalls(Path javaFile) {
        List<FunctionCallInfo> calls = new ArrayList<>();
        try {
            String content = Files.readString(javaFile);
            String className = extractSimpleClassName(javaFile);
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = WAS_CLIENT_USAGE.matcher(lines[i]);
                while (m.find()) {
                    calls.add(new FunctionCallInfo(m.group(1), m.group(2), className, i + 1));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read file: {}", javaFile);
        }
        return calls;
    }

    /**
     * Finds datasource names used in NimbusDatabaseHelperImpl constructors.
     * Handles both direct string literals and variable references.
     */
    public List<String> findDatasourceNames(Path javaFile) {
        List<String> dsNames = new ArrayList<>();
        try {
            String content = Files.readString(javaFile);
            if (!content.contains("NimbusDatabaseHelperImpl")) {
                return dsNames;
            }

            // Collect all variable assignments to string literals
            java.util.Map<String, String> varValues = new java.util.HashMap<>();
            Matcher varMatcher = VARIABLE_ASSIGN.matcher(content);
            while (varMatcher.find()) {
                varValues.put(varMatcher.group(1), varMatcher.group(2));
            }

            // Find NimbusDatabaseHelperImpl constructor calls
            Matcher dbMatcher = DB_HELPER_CONSTRUCTOR.matcher(content);
            while (dbMatcher.find()) {
                String arg = dbMatcher.group(1).trim();
                if (arg.startsWith("\"") && arg.endsWith("\"")) {
                    // Direct string literal
                    dsNames.add(arg.substring(1, arg.length() - 1));
                } else {
                    // Variable reference — resolve from assignments
                    String resolved = varValues.get(arg);
                    if (resolved != null) {
                        dsNames.add(resolved);
                    } else {
                        dsNames.add(arg + " (variable)");
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read file for datasource analysis: {}", javaFile);
        }
        return dsNames;
    }

    /**
     * Finds Nimbus function calls (e.g., SendEmailFunction.execute(...)) in a Java source file.
     * Only considers functions imported from gov.nystax.nimbus.function.client.
     * Skips commented-out lines. Returns function names without the "Function" suffix.
     */
    public List<String> findNimbusFunctionCalls(Path javaFile) {
        List<String> functions = new ArrayList<>();
        try {
            String content = Files.readString(javaFile);

            // Collect imported Nimbus function classes
            java.util.Set<String> importedFunctions = new java.util.HashSet<>();
            Matcher importMatcher = NIMBUS_FUNCTION_IMPORT.matcher(content);
            while (importMatcher.find()) {
                importedFunctions.add(importMatcher.group(1));
            }
            if (importedFunctions.isEmpty()) {
                return functions;
            }

            // Find active (non-commented) function calls
            String[] lines = content.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue;

                Matcher callMatcher = NIMBUS_FUNCTION_CALL.matcher(line);
                while (callMatcher.find()) {
                    String funcClass = callMatcher.group(1);
                    if (importedFunctions.contains(funcClass)) {
                        // Strip "Function" suffix to get Nimbus function name
                        String funcName = funcClass.endsWith("Function")
                                ? funcClass.substring(0, funcClass.length() - 8)
                                : funcClass;
                        if (!functions.contains(funcName)) {
                            functions.add(funcName);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read file for Nimbus function analysis: {}", javaFile);
        }
        return functions;
    }

    /**
     * Finds SQL queries in Java source from executeQuery(), executeUpdate(),
     * prepareStatement(), and prepareCall() calls with inline string literals.
     */
    public List<String> findSqlQueries(Path javaFile) {
        List<String> queries = new ArrayList<>();
        try {
            String content = Files.readString(javaFile);
            String[] lines = content.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue;

                Matcher m = SQL_QUERY_PATTERN.matcher(line);
                while (m.find()) {
                    String sql = m.group(1).trim();
                    if (!queries.contains(sql)) {
                        queries.add(sql);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read file for SQL query analysis: {}", javaFile);
        }
        return queries;
    }

    /**
     * Scans all Java files and builds an index of className -> Nimbus functions called.
     * Only includes classes that actually call at least one Nimbus function.
     */
    public Map<String, List<String>> buildNimbusFunctionIndex(List<Path> javaFiles) {
        Map<String, List<String>> index = new HashMap<>();
        for (Path javaFile : javaFiles) {
            List<String> funcs = findNimbusFunctionCalls(javaFile);
            if (!funcs.isEmpty()) {
                String className = extractSimpleClassName(javaFile);
                index.put(className, funcs);
            }
        }
        log.info("Nimbus function index: {} classes with function calls", index.size());
        index.forEach((cls, funcs) -> log.info("  {} -> {}", cls, funcs));
        return index;
    }

    /**
     * Finds simple class names imported or referenced by the given Java source file.
     * Returns only classes present in the candidateClasses set (to limit to classes with Nimbus functions).
     */
    public Set<String> findReferencedClassNames(Path javaFile, Set<String> candidateClasses) {
        Set<String> referenced = new HashSet<>();
        try {
            String content = Files.readString(javaFile);
            for (String candidate : candidateClasses) {
                // Check if the class name appears anywhere in the source (import, field type, method call, etc.)
                if (content.contains(candidate)) {
                    // Don't match the file's own class name
                    String ownClass = extractSimpleClassName(javaFile);
                    if (!candidate.equals(ownClass)) {
                        referenced.add(candidate);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read file for reference analysis: {}", javaFile);
        }
        return referenced;
    }

    private BatchExitUsage parseBatchExitArgs(String className, int lineNumber, String args) {
        String status = "UNKNOWN";
        String message = "";

        Matcher statusMatcher = NIMBA_STATUS_PATTERN.matcher(args);
        if (statusMatcher.find()) {
            status = statusMatcher.group(1);
        } else {
            Matcher strMatcher = STRING_LITERAL.matcher(args);
            if (strMatcher.find()) {
                status = strMatcher.group(1);
            }
        }

        List<String> strings = new ArrayList<>();
        Matcher allStrings = STRING_LITERAL.matcher(args);
        while (allStrings.find()) {
            strings.add(allStrings.group(1));
        }
        if (strings.size() >= 2) {
            message = strings.get(strings.size() - 1);
        } else if (strings.size() == 1 && statusMatcher.reset().find()) {
            message = strings.get(0);
        }

        return new BatchExitUsage(className, lineNumber, status, message);
    }

    public String extractSimpleClassName(Path javaFile) {
        String fileName = javaFile.getFileName().toString();
        return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }
}
