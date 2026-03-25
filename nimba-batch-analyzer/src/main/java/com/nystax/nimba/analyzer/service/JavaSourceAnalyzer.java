package com.nystax.nimba.analyzer.service;

import com.nystax.nimba.analyzer.model.BatchExitUsage;
import com.nystax.nimba.analyzer.model.FunctionCallInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private String extractSimpleClassName(Path javaFile) {
        String fileName = javaFile.getFileName().toString();
        return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }
}
