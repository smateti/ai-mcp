package com.schema2excel;

import java.io.File;

/**
 * Main entry point with two modes, both requiring a root complex type name:
 *   schema2excel: JSON Schema  -> Excel
 *   excel2schema: Excel        -> JSON Schema (only used complex objects)
 */
public class App {

    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        String mode = args[0];
        String rootType = args[1];
        String inputPath = args[2];

        try {
            switch (mode) {
                case "schema2excel": {
                    String outputPath = args.length >= 4 ? args[3] : "schema-output.xlsx";
                    runSchemaToExcel(rootType, inputPath, outputPath);
                    break;
                }
                case "excel2schema": {
                    String outputPath = args.length >= 4 ? args[3] : "generated-schema.json";
                    runExcelToSchema(rootType, inputPath, outputPath);
                    break;
                }
                default:
                    System.err.println("Unknown mode: " + mode);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSchemaToExcel(String rootType, String inputPath, String outputPath) throws Exception {
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        System.out.println("Root type: " + rootType);
        System.out.println("Parsing JSON Schema: " + inputPath);
        JsonSchemaParser parser = new JsonSchemaParser();
        SchemaNode root = parser.parse(inputPath, rootType);

        System.out.println("Generating Excel: " + outputPath);
        ExcelGenerator generator = new ExcelGenerator();
        generator.generate(root, outputPath);

        System.out.println("Done! Excel file created at: " + outputFile.getAbsolutePath());
    }

    private static void runExcelToSchema(String rootType, String inputPath, String outputPath) throws Exception {
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        System.out.println("Root type: " + rootType);
        System.out.println("Reading Excel: " + inputPath);
        System.out.println("Walking from root '" + rootType + "', skipping unused complex objects...");
        ExcelToSchemaGenerator generator = new ExcelToSchemaGenerator();
        generator.generate(inputPath, rootType, outputPath);

        System.out.println("Done! JSON Schema created at: " + outputFile.getAbsolutePath());
    }

    private static void printUsage() {
        System.out.println("JSON Schema <-> Excel Tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  schema2excel <RootType> <schema.json> [output.xlsx]");
        System.out.println("      Convert JSON Schema to Excel, starting from the given root type");
        System.out.println();
        System.out.println("  excel2schema <RootType> <input.xlsx> [output-schema.json]");
        System.out.println("      Convert Excel back to JSON Schema, walking from the root type");
        System.out.println("      Only includes complex objects reachable from the root (skips unused ones)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  schema2excel Order schema.json order-output.xlsx");
        System.out.println("  excel2schema Order order-output.xlsx regenerated-schema.json");
    }
}
