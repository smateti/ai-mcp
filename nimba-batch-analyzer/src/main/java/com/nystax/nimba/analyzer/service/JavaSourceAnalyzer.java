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
