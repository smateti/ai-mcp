package com.example.foxpro;

import com.example.foxpro.llm.OllamaClient;
import com.example.foxpro.model.*;
import com.example.foxpro.parser.FoxProParser;
import com.example.foxpro.parser.MnxParser;
import com.example.foxpro.parser.VcxParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application that orchestrates parsing FoxPro source code and
 * sending it to Llama 3.1 (via Ollama) for use-case summarization.
 *
 * Supports:
 *   - .prg files (plain-text program files)
 *   - .vcx/.vct files (Visual Class Libraries - binary DBF/FPT format)
 *   - .vca files (compiled class libraries - attempts raw extraction)
 *
 * Usage:
 *   java -jar foxpro-analyzer.jar <foxpro-source-dir> [ollama-url] [model-name]
 *
 * Examples:
 *   java -jar foxpro-analyzer.jar ./sample-foxpro-app
 *   java -jar foxpro-analyzer.jar ./my-foxpro-project http://localhost:11434 llama3.1
 */
public class FoxProAnalyzerApp {

    private static final Logger logger = LoggerFactory.getLogger(FoxProAnalyzerApp.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar foxpro-analyzer.jar <foxpro-source-dir> [ollama-url] [model-name]");
            System.out.println();
            System.out.println("Arguments:");
            System.out.println("  foxpro-source-dir  Path to directory containing .prg/.vcx/.vct/.vca files");
            System.out.println("  ollama-url         Ollama API URL (default: http://localhost:11434)");
            System.out.println("  model-name         Model to use (default: llama3.1)");
            System.out.println();
            System.out.println("Supported file types:");
            System.out.println("  .prg  - FoxPro program source files (plain text)");
            System.out.println("  .vcx  - Visual Class Library table (binary DBF)");
            System.out.println("  .vct  - Visual Class Library memo file (companion to .vcx)");
            System.out.println("  .vca  - Compiled class library (best-effort extraction)");
            System.exit(1);
        }

        String sourceDir = args[0];
        String ollamaUrl = args.length > 1 ? args[1] : "http://localhost:11434";
        String modelName = args.length > 2 ? args[2] : "llama3.1";

        FoxProAnalyzerApp app = new FoxProAnalyzerApp();
        app.run(sourceDir, ollamaUrl, modelName);
    }

    public void run(String sourceDir, String ollamaUrl, String modelName) {
        System.out.println("=================================================");
        System.out.println("  FoxPro Code Analyzer with Llama 3.1");
        System.out.println("=================================================");
        System.out.println("Source Directory: " + sourceDir);
        System.out.println("Ollama URL:       " + ollamaUrl);
        System.out.println("Model:            " + modelName);
        System.out.println();

        // Initialize components
        FoxProParser prgParser = new FoxProParser();
        VcxParser vcxParser = new VcxParser();
        MnxParser mnxParser = new MnxParser();
        OllamaClient ollamaClient = new OllamaClient(ollamaUrl, modelName);

        // Step 1: Test Ollama connection
        System.out.println("[1/6] Testing connection to Ollama...");
        if (!ollamaClient.testConnection()) {
            System.err.println("ERROR: Cannot connect to Ollama or model '" + modelName + "' not found.");
            System.err.println("Make sure Ollama is running: ollama serve");
            System.err.println("And the model is pulled: ollama pull " + modelName);
            System.exit(1);
        }
        System.out.println("  -> Connected successfully to " + ollamaUrl + " with model " + modelName);
        System.out.println();

        // Step 2: Parse .prg source files
        System.out.println("[2/6] Parsing .prg program files...");
        List<FoxProModule> modules;
        try {
            modules = prgParser.parseDirectory(sourceDir);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to parse .prg files: " + e.getMessage());
            modules = new ArrayList<>();
        }
        System.out.println("  -> Found " + modules.size() + " .prg files");
        int totalProcedures = modules.stream().mapToInt(m -> m.getProcedures().size()).sum();
        System.out.println("  -> Total procedures/functions in .prg: " + totalProcedures);
        System.out.println();

        // Step 3: Parse .vcx/.vct class libraries
        System.out.println("[3/6] Parsing .vcx/.vct Visual Class Libraries...");
        List<VcxLibrary> vcxLibraries;
        try {
            vcxLibraries = vcxParser.parseDirectory(sourceDir);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to parse VCX files: " + e.getMessage());
            vcxLibraries = new ArrayList<>();
        }
        int totalClasses = vcxLibraries.stream().mapToInt(l -> l.getClasses().size()).sum();
        System.out.println("  -> Found " + vcxLibraries.size() + " class library files");
        System.out.println("  -> Total classes in libraries: " + totalClasses);
        System.out.println();

        // Step 4: Parse .mnx/.mnt menu definitions
        System.out.println("[4/6] Parsing .mnx/.mnt Menu Definitions...");
        List<MnxMenu> mnxMenus;
        try {
            mnxMenus = mnxParser.parseDirectory(sourceDir);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to parse MNX files: " + e.getMessage());
            mnxMenus = new ArrayList<>();
        }
        int totalMenuItems = mnxMenus.stream().mapToInt(m -> m.getMenuItems().size()).sum();
        System.out.println("  -> Found " + mnxMenus.size() + " menu definition files");
        System.out.println("  -> Total menu items: " + totalMenuItems);
        System.out.println();

        // Step 5: Analyze with LLM
        System.out.println("[5/6] Analyzing code with Llama 3.1...");
        System.out.println("  (This may take a while depending on code size and GPU performance)");
        System.out.println();

        List<AnalysisResult> results = new ArrayList<>();

        int totalUnits = modules.size() + totalProcedures + totalClasses + mnxMenus.size();
        int current = 0;

        // --- Analyze .prg modules ---
        for (FoxProModule module : modules) {
            current++;
            System.out.printf("  [%d/%d] Analyzing PRG module: %s%n", current, totalUnits, module.getFileName());

            try {
                String context = "File: " + module.getFileName() +
                        " (FoxPro Program File)" +
                        (module.getHeaderComment() != null ? "\nHeader: " + module.getHeaderComment() : "");

                String summary = ollamaClient.summarizeFoxProCode(module.getMainCode(), context);

                results.add(new AnalysisResult(
                        module.getFileName(),
                        module.getFileName() + " (main)",
                        "MODULE",
                        summary
                ));
                System.out.println("    -> Module summary generated");
            } catch (IOException e) {
                logger.error("Failed to analyze module {}: {}", module.getFileName(), e.getMessage());
                System.out.println("    -> ERROR: " + e.getMessage());
            }

            for (FoxProProcedure proc : module.getProcedures()) {
                current++;
                System.out.printf("  [%d/%d] Analyzing %s: %s::%s%n",
                        current, totalUnits, proc.getType(), module.getFileName(), proc.getName());

                try {
                    String context = "File: " + module.getFileName() +
                            ", " + proc.getType() + ": " + proc.getName() +
                            (proc.getComment() != null ? "\nComment: " + proc.getComment() : "");

                    String summary = ollamaClient.summarizeFoxProCode(proc.getBody(), context);

                    results.add(new AnalysisResult(
                            module.getFileName(),
                            proc.getName(),
                            proc.getType(),
                            summary
                    ));
                    System.out.println("    -> Summary generated");
                } catch (IOException e) {
                    logger.error("Failed to analyze {}: {}", proc.getName(), e.getMessage());
                    System.out.println("    -> ERROR: " + e.getMessage());
                }
            }
        }

        // --- Analyze .vcx class libraries ---
        for (VcxLibrary library : vcxLibraries) {
            for (VcxClass vcxClass : library.getClasses()) {
                current++;
                System.out.printf("  [%d/%d] Analyzing VCX class: %s::%s (base: %s)%n",
                        current, totalUnits, library.getFileName(),
                        vcxClass.getClassName(), vcxClass.getBaseClass());

                try {
                    String codeToAnalyze = buildVcxClassCode(vcxClass);

                    if (codeToAnalyze.isBlank()) {
                        System.out.println("    -> Skipped (no method code found)");
                        continue;
                    }

                    String context = "Visual Class Library: " + library.getFileName() +
                            "\nClass: " + vcxClass.getClassName() +
                            "\nBase Class: " + vcxClass.getBaseClass() +
                            "\nParent Class: " + vcxClass.getParentClass() +
                            (vcxClass.getDescription() != null && !vcxClass.getDescription().isBlank()
                                    ? "\nDescription: " + vcxClass.getDescription() : "") +
                            "\nContained Objects: " + vcxClass.getContainedObjects().size();

                    String summary = ollamaClient.summarizeFoxProCode(codeToAnalyze, context);

                    results.add(new AnalysisResult(
                            library.getFileName(),
                            vcxClass.getClassName(),
                            "CLASS (" + vcxClass.getBaseClass() + ")",
                            summary
                    ));
                    System.out.println("    -> Class summary generated");
                } catch (IOException e) {
                    logger.error("Failed to analyze class {}: {}",
                            vcxClass.getClassName(), e.getMessage());
                    System.out.println("    -> ERROR: " + e.getMessage());
                }
            }
        }

        // --- Analyze .mnx menu definitions ---
        for (MnxMenu menu : mnxMenus) {
            current++;
            System.out.printf("  [%d/%d] Analyzing MNX menu: %s (%d pads, %d items)%n",
                    current, totalUnits, menu.getFileName(),
                    menu.getMenuPads().size(), menu.getMenuItems().size());

            try {
                String codeToAnalyze = menu.getAllCode();

                if (codeToAnalyze.isBlank()) {
                    System.out.println("    -> Skipped (no executable code found)");
                    continue;
                }

                String context = "Menu Definition File: " + menu.getFileName() +
                        "\nMenu Structure:\n" + menu.getMenuStructure();

                String summary = ollamaClient.summarizeFoxProCode(codeToAnalyze, context);

                results.add(new AnalysisResult(
                        menu.getFileName(),
                        menu.getFileName().replace(".mnx", ""),
                        "MENU",
                        summary
                ));
                System.out.println("    -> Menu summary generated");
            } catch (IOException e) {
                logger.error("Failed to analyze menu {}: {}", menu.getFileName(), e.getMessage());
                System.out.println("    -> ERROR: " + e.getMessage());
            }
        }

        System.out.println();

        // Step 6: Generate report
        System.out.println("[6/6] Generating analysis report...");
        String reportPath = generateReport(results, sourceDir);
        System.out.println("  -> Report saved to: " + reportPath);
        System.out.println();
        System.out.println("=================================================");
        System.out.println("  Analysis complete!");
        System.out.println("  PRG modules analyzed:  " + modules.size());
        System.out.println("  VCX classes analyzed:  " + totalClasses);
        System.out.println("  MNX menus analyzed:    " + mnxMenus.size());
        System.out.println("  Total summaries:       " + results.size());
        System.out.println("=================================================");
    }

    /**
     * Builds a comprehensive code representation of a VCX class for LLM analysis.
     * Combines properties, methods, and contained object info.
     */
    private String buildVcxClassCode(VcxClass vcxClass) {
        StringBuilder sb = new StringBuilder();

        // Class header info
        sb.append("* Class: ").append(vcxClass.getClassName()).append("\n");
        sb.append("* Base Class: ").append(vcxClass.getBaseClass()).append("\n");
        sb.append("* Parent Class: ").append(vcxClass.getParentClass()).append("\n");
        sb.append("\n");

        // Properties
        String props = vcxClass.getAllProperties();
        if (!props.isBlank()) {
            sb.append("* === PROPERTIES ===\n");
            sb.append(props).append("\n\n");
        }

        // Contained objects summary
        if (!vcxClass.getContainedObjects().isEmpty()) {
            sb.append("* === CONTAINED OBJECTS ===\n");
            for (VcxObject obj : vcxClass.getContainedObjects()) {
                sb.append("* Object: ").append(obj.getObjName())
                  .append(" (Class: ").append(obj.getClassName())
                  .append(", Base: ").append(obj.getBaseClass())
                  .append(", Parent: ").append(obj.getParentObj())
                  .append(")\n");
            }
            sb.append("\n");
        }

        // Method code (class-level + object-level)
        String methods = vcxClass.getAllMethodCode();
        if (!methods.isBlank()) {
            sb.append("* === METHOD CODE ===\n");
            sb.append(methods);
        }

        return sb.toString();
    }

    /**
     * Generates a markdown report of all analysis results.
     */
    private String generateReport(List<AnalysisResult> results, String sourceDir) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportFileName = "foxpro_analysis_" + timestamp + ".md";
        Path reportPath = Paths.get(sourceDir).getParent() != null
                ? Paths.get(sourceDir).getParent().resolve(reportFileName)
                : Paths.get(reportFileName);

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportPath))) {
            writer.println("# FoxPro Application Analysis Report");
            writer.println();
            writer.println("Generated: " + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println();
            writer.println("Source Directory: `" + sourceDir + "`");
            writer.println();
            writer.println("---");
            writer.println();

            // Summary section
            writer.println("## Summary");
            writer.println();
            long prgCount = results.stream()
                    .filter(r -> r.getUnitType().equals("MODULE") || r.getUnitType().equals("PROCEDURE") || r.getUnitType().equals("FUNCTION"))
                    .count();
            long classCount = results.stream()
                    .filter(r -> r.getUnitType().startsWith("CLASS"))
                    .count();
            long menuCount = results.stream()
                    .filter(r -> r.getUnitType().equals("MENU"))
                    .count();
            writer.println("| Type | Count |");
            writer.println("|------|-------|");
            writer.println("| PRG Modules/Procedures | " + prgCount + " |");
            writer.println("| VCX Classes | " + classCount + " |");
            writer.println("| MNX Menus | " + menuCount + " |");
            writer.println("| **Total** | **" + results.size() + "** |");
            writer.println();
            writer.println("---");
            writer.println();

            // Table of Contents
            writer.println("## Table of Contents");
            writer.println();
            String currentFile = "";
            for (AnalysisResult result : results) {
                if (!result.getSourceFile().equals(currentFile)) {
                    currentFile = result.getSourceFile();
                    writer.println("- **" + currentFile + "**");
                }
                String anchor = (result.getSourceFile() + "-" + result.getUnitName())
                        .toLowerCase().replaceAll("[^a-z0-9]", "-");
                writer.println("  - [" + result.getUnitType() + ": " + result.getUnitName() + "](#" + anchor + ")");
            }
            writer.println();
            writer.println("---");
            writer.println();

            // Detailed summaries
            writer.println("## Detailed Analysis");
            writer.println();

            currentFile = "";
            for (AnalysisResult result : results) {
                if (!result.getSourceFile().equals(currentFile)) {
                    currentFile = result.getSourceFile();
                    writer.println("### " + currentFile);
                    writer.println();
                }

                writer.println("#### " + result.getUnitType() + ": " + result.getUnitName());
                writer.println();
                writer.println(result.getSummary());
                writer.println();
                writer.println("---");
                writer.println();
            }

        } catch (IOException e) {
            logger.error("Failed to write report: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }

        return reportPath.toString();
    }
}
