package com.nystax.nimba.analyzer.parser;

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
public class NimbaAnnotationResolver {

    private static final Logger log = LoggerFactory.getLogger(NimbaAnnotationResolver.class);

    private static final Pattern NIMBA_ANNOTATION = Pattern.compile(
            "@Nimba\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern NIMBA_VALUE = Pattern.compile(
            "@Nimba\\s*\\(\\s*value\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern NIMBA_ID = Pattern.compile(
            "@Nimba\\s*\\(\\s*id\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern COMPONENT_ANNOTATION = Pattern.compile(
            "@(?:Component|Service|Repository|Named)\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern CLASS_DECLARATION = Pattern.compile(
            "public\\s+class\\s+(\\w+)");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile(
            "package\\s+([\\w.]+)\\s*;");

    public Map<String, String> resolveAll(List<Path> javaFiles) {
        Map<String, String> idToClass = new LinkedHashMap<>();
        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile);
                String nimbaId = extractNimbaId(content);
                if (nimbaId != null) {
                    String className = extractFullyQualifiedName(content);
                    idToClass.put(nimbaId, className);
                    log.debug("Resolved @Nimba(\"{}\") -> {}", nimbaId, className);
                }
            } catch (IOException e) {
                log.warn("Failed to read Java file: {}", javaFile);
            }
        }
        log.info("Resolved {} @Nimba annotations", idToClass.size());
        return idToClass;
    }

    public Path findSourceFile(String nimbaId, List<Path> javaFiles, Map<String, String> idToClass) {
        String className = idToClass.get(nimbaId);
        if (className == null) return null;

        return findSourceFileByClassName(className, javaFiles);
    }

    public Path findSourceFileByClassName(String fullyQualifiedClassName, List<Path> javaFiles) {
        if (fullyQualifiedClassName == null) return null;

        String simpleClassName = fullyQualifiedClassName.contains(".")
                ? fullyQualifiedClassName.substring(fullyQualifiedClassName.lastIndexOf('.') + 1)
                : fullyQualifiedClassName;

        // Try exact match by converting package to path
        String expectedPath = fullyQualifiedClassName.replace('.', '/') + ".java";
        for (Path p : javaFiles) {
            String normalized = p.toString().replace('\\', '/');
            if (normalized.endsWith(expectedPath)) {
                return p;
            }
        }

        // Fallback: match by simple class name
        return javaFiles.stream()
                .filter(p -> p.getFileName().toString().equals(simpleClassName + ".java"))
                .findFirst()
                .orElse(null);
    }

    private String extractNimbaId(String content) {
        for (Pattern pattern : List.of(NIMBA_ANNOTATION, NIMBA_VALUE, NIMBA_ID)) {
            Matcher m = pattern.matcher(content);
            if (m.find()) return m.group(1);
        }
        // Fallback: @Component / @Named
        Matcher m = COMPONENT_ANNOTATION.matcher(content);
        if (m.find()) return m.group(1);
        return null;
    }

    private String extractFullyQualifiedName(String content) {
        String packageName = "";
        Matcher pkgMatcher = PACKAGE_DECLARATION.matcher(content);
        if (pkgMatcher.find()) {
            packageName = pkgMatcher.group(1);
        }
        String className = "Unknown";
        Matcher clsMatcher = CLASS_DECLARATION.matcher(content);
        if (clsMatcher.find()) {
            className = clsMatcher.group(1);
        }
        return packageName.isEmpty() ? className : packageName + "." + className;
    }
}
