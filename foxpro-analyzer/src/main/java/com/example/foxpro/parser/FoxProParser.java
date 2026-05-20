package com.example.foxpro.parser;

import com.example.foxpro.model.FoxProModule;
import com.example.foxpro.model.FoxProProcedure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses FoxPro .prg source files into structured modules with procedures/functions.
 */
public class FoxProParser {

    private static final Logger logger = LoggerFactory.getLogger(FoxProParser.class);

    // Pattern to match PROCEDURE or FUNCTION declarations
    private static final Pattern PROC_PATTERN = Pattern.compile(
            "^\\s*(PROCEDURE|FUNCTION)\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // Pattern to match DEFINE CLASS declarations (used in VCX-exported .prg files)
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*DEFINE\\s+CLASS\\s+(\\w+)\\s+AS\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // Pattern to match ENDDEFINE (end of class block)
    private static final Pattern ENDDEFINE_PATTERN = Pattern.compile(
            "^\\s*ENDDEFINE",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );



    /**
     * Scans a directory for .prg files and parses each one.
     */
    public List<FoxProModule> parseDirectory(String directoryPath) throws IOException {
        List<FoxProModule> modules = new ArrayList<>();
        Path dir = Paths.get(directoryPath);

        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a valid directory: " + directoryPath);
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".prg") || name.endsWith(".mpr");
                    })
                 .sorted()
                 .forEach(path -> {
                     try {
                         FoxProModule module = parseFile(path);
                         modules.add(module);
                         logger.info("Parsed: {} ({} procedures)",
                                 module.getFileName(), module.getProcedures().size());
                     } catch (IOException e) {
                         logger.error("Failed to parse: {}", path, e);
                     }
                 });
        }

        return modules;
    }

    /**
     * Parses a single .prg file into a FoxProModule.
     * Handles both standalone PROCEDURE/FUNCTION blocks and DEFINE CLASS blocks.
     */
    public FoxProModule parseFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String fileName = filePath.getFileName().toString();

        FoxProModule module = new FoxProModule(fileName, filePath.toString());

        // Extract header comment (leading comment block before any code)
        String headerComment = extractHeaderComment(content);
        module.setHeaderComment(headerComment);

        // Find DEFINE CLASS blocks first (these are self-contained with ENDDEFINE)
        List<ProcBoundary> classBoundaries = findClassBoundaries(content);

        // Find all procedure/function boundaries (outside of class definitions)
        List<ProcBoundary> procBoundaries = findProcedureBoundaries(content);

        // Merge and sort all boundaries by start index
        List<ProcBoundary> allBoundaries = new ArrayList<>();
        allBoundaries.addAll(classBoundaries);
        allBoundaries.addAll(procBoundaries);

        // Remove procedures that fall inside a class block (they're part of the class)
        allBoundaries.removeIf(b -> {
            if (b.type.equals("CLASS")) return false;
            for (ProcBoundary cls : classBoundaries) {
                if (b.startIndex > cls.startIndex && b.startIndex < cls.endIndex) {
                    return true;
                }
            }
            return false;
        });

        allBoundaries.sort(Comparator.comparingInt(b -> b.startIndex));

        if (allBoundaries.isEmpty()) {
            // Entire file is the main code (no procedures or classes)
            module.setMainCode(content);
        } else {
            // Main code is everything before the first procedure/class
            String mainCode = content.substring(0, allBoundaries.get(0).startIndex).trim();
            module.setMainCode(mainCode);

            // Extract each procedure/class block
            for (int i = 0; i < allBoundaries.size(); i++) {
                ProcBoundary boundary = allBoundaries.get(i);

                int endIndex;
                if (boundary.endIndex > 0) {
                    // Class blocks have an explicit end (ENDDEFINE)
                    endIndex = boundary.endIndex;
                } else {
                    // Procedures end where the next block begins
                    endIndex = (i + 1 < allBoundaries.size())
                            ? allBoundaries.get(i + 1).startIndex
                            : content.length();
                }

                String procBody = content.substring(boundary.startIndex, endIndex).trim();

                FoxProProcedure proc = new FoxProProcedure(boundary.name, boundary.type);
                proc.setBody(procBody);
                proc.setComment(extractProcComment(procBody));
                module.addProcedure(proc);
            }
        }

        return module;
    }

    /**
     * Extracts the leading comment block from source content.
     */
    private String extractHeaderComment(String content) {
        StringBuilder comment = new StringBuilder();
        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("&&") || trimmed.isEmpty()) {
                comment.append(line).append("\n");
            } else {
                break;
            }
        }

        return comment.toString().trim();
    }

    /**
     * Finds the start positions and names of all PROCEDURE/FUNCTION declarations.
     */
    private List<ProcBoundary> findProcedureBoundaries(String content) {
        List<ProcBoundary> boundaries = new ArrayList<>();
        Matcher matcher = PROC_PATTERN.matcher(content);

        while (matcher.find()) {
            // Find the start of the line containing this match
            int lineStart = content.lastIndexOf('\n', matcher.start());
            lineStart = (lineStart == -1) ? 0 : lineStart + 1;

            // Check if there's a comment block above (part of the procedure header)
            int procStart = findProcStartWithComments(content, lineStart);

            boundaries.add(new ProcBoundary(
                    matcher.group(2),           // procedure name
                    matcher.group(1).toUpperCase(), // type (PROCEDURE or FUNCTION)
                    procStart
            ));
        }

        return boundaries;
    }

    /**
     * Looks backwards from a procedure declaration to include preceding comment block.
     */
    private int findProcStartWithComments(String content, int procLineStart) {
        String before = content.substring(0, procLineStart);
        String[] lines = before.split("\n");

        int commentStart = procLineStart;
        // Walk backwards through preceding lines to find comment block
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("&&") || trimmed.isEmpty()) {
                // Calculate position
                int pos = 0;
                for (int j = 0; j < i; j++) {
                    pos += lines[j].length() + 1; // +1 for newline
                }
                commentStart = pos;
            } else {
                break;
            }
        }

        return commentStart;
    }

    /**
     * Extracts the comment lines immediately following a PROCEDURE/FUNCTION declaration.
     */
    private String extractProcComment(String procBody) {
        StringBuilder comment = new StringBuilder();
        String[] lines = procBody.split("\n");
        boolean pastDeclaration = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!pastDeclaration) {
                if (trimmed.toUpperCase().startsWith("PROCEDURE") ||
                    trimmed.toUpperCase().startsWith("FUNCTION") ||
                    trimmed.toUpperCase().startsWith("DEFINE CLASS")) {
                    pastDeclaration = true;
                }
                continue;
            }

            // Collect comment lines after declaration
            if (trimmed.startsWith("*") || trimmed.startsWith("&&")) {
                comment.append(trimmed).append("\n");
            } else if (!trimmed.isEmpty()) {
                break;
            }
        }

        return comment.toString().trim();
    }

    /**
     * Finds DEFINE CLASS ... ENDDEFINE blocks and returns their boundaries.
     */
    private List<ProcBoundary> findClassBoundaries(String content) {
        List<ProcBoundary> boundaries = new ArrayList<>();
        Matcher classMatcher = CLASS_PATTERN.matcher(content);

        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            int classStart = classMatcher.start();

            // Find preceding comment block
            int lineStart = content.lastIndexOf('\n', classStart);
            lineStart = (lineStart == -1) ? 0 : lineStart + 1;
            int blockStart = findProcStartWithComments(content, lineStart);

            // Find matching ENDDEFINE
            Matcher endMatcher = ENDDEFINE_PATTERN.matcher(content);
            int classEnd = content.length();
            if (endMatcher.find(classMatcher.end())) {
                // Include the ENDDEFINE line itself
                int endOfLine = content.indexOf('\n', endMatcher.end());
                classEnd = (endOfLine == -1) ? content.length() : endOfLine + 1;
            }

            boundaries.add(new ProcBoundary(className, "CLASS", blockStart, classEnd));
        }

        return boundaries;
    }

    /**
     * Internal class to track procedure/class boundary positions.
     */
    private static class ProcBoundary {
        final String name;
        final String type;
        final int startIndex;
        final int endIndex; // -1 for procedures (end determined by next block), >0 for classes

        ProcBoundary(String name, String type, int startIndex) {
            this(name, type, startIndex, -1);
        }

        ProcBoundary(String name, String type, int startIndex, int endIndex) {
            this.name = name;
            this.type = type;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }
}
