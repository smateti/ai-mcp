package com.schema2excel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Reads a generated Excel file (Complex Objects + Schema Hierarchy sheets),
 * reconstructs the object tree, and generates a clean JSON Schema that only
 * includes complex objects that are actually referenced in the hierarchy.
 */
public class ExcelToSchemaGenerator {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * A complex object parsed from the "Complex Objects" sheet.
     */
    private static class ComplexObject {
        String name;
        String path;
        List<FieldInfo> fields = new ArrayList<>();
    }

    /**
     * A field row parsed from either sheet.
     */
    private static class FieldInfo {
        String name;
        String type;
        boolean required;
        String description;
        String path;
        String parent;
    }

    public void generate(String excelPath, String rootTypeName, String outputSchemaPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(excelPath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            // Step 1: Read Complex Objects sheet to know which objects exist and their direct fields
            Map<String, ComplexObject> complexObjects = readComplexObjectsSheet(workbook);

            // Step 2: Read Schema Hierarchy sheet to get the full parent-child tree
            List<FieldInfo> hierarchyRows = readHierarchySheet(workbook);

            // Step 3: Identify which complex objects are actually used starting from the root type
            Set<String> usedPaths = findUsedComplexObjectPaths(rootTypeName, hierarchyRows, complexObjects);

            // Step 4: Build JSON Schema from the hierarchy, only including used complex objects
            ObjectNode schema = buildSchema(rootTypeName, complexObjects, usedPaths);

            // Step 5: Write output
            File outputFile = new File(outputSchemaPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            mapper.writeValue(outputFile, schema);
        }
    }

    // ---- Step 1: Parse "Complex Objects" sheet ----

    private Map<String, ComplexObject> readComplexObjectsSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.getSheet("Complex Objects");
        Map<String, ComplexObject> result = new LinkedHashMap<>();
        if (sheet == null) return result;

        ComplexObject current = null;
        boolean readingFields = false;

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                readingFields = false;
                continue;
            }

            String firstCell = getCellString(row, 0);

            // Detect section header: "Object: name (path)"
            if (firstCell.startsWith("Object: ")) {
                current = parseSectionHeader(firstCell);
                result.put(current.path, current);
                readingFields = false;
                continue;
            }

            // Detect column header row (skip it)
            if ("Field Name".equals(firstCell)) {
                readingFields = true;
                continue;
            }

            // Read field data rows
            if (readingFields && current != null && !firstCell.isEmpty()) {
                FieldInfo field = new FieldInfo();
                field.name = firstCell;
                field.type = getCellString(row, 1);
                field.required = "Yes".equalsIgnoreCase(getCellString(row, 2));
                field.description = getCellString(row, 3);
                field.path = getCellString(row, 4);
                field.parent = current.name;
                current.fields.add(field);
            }
        }

        return result;
    }

    private ComplexObject parseSectionHeader(String header) {
        // Format: "Object: name (path)"
        ComplexObject obj = new ComplexObject();
        String content = header.substring("Object: ".length());
        int parenStart = content.lastIndexOf('(');
        int parenEnd = content.lastIndexOf(')');
        if (parenStart >= 0 && parenEnd > parenStart) {
            obj.name = content.substring(0, parenStart).trim();
            obj.path = content.substring(parenStart + 1, parenEnd).trim();
        } else {
            obj.name = content.trim();
            obj.path = content.trim();
        }
        return obj;
    }

    // ---- Step 2: Parse "Schema Hierarchy" sheet ----

    private List<FieldInfo> readHierarchySheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.getSheet("Schema Hierarchy");
        List<FieldInfo> rows = new ArrayList<>();
        if (sheet == null) return rows;

        // Skip header row (row 0)
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String name = getCellString(row, 1);
            if (name.isEmpty()) continue;

            FieldInfo field = new FieldInfo();
            field.parent = getCellString(row, 0);
            field.name = name;
            field.type = getCellString(row, 2);
            field.required = "Yes".equalsIgnoreCase(getCellString(row, 3));
            field.description = getCellString(row, 4);
            field.path = getCellString(row, 5);
            rows.add(field);
        }
        return rows;
    }

    // ---- Step 3: Find which complex objects are actually referenced ----

    private Set<String> findUsedComplexObjectPaths(String rootTypeName,
                                                    List<FieldInfo> hierarchyRows,
                                                    Map<String, ComplexObject> complexObjects) {
        Set<String> usedPaths = new LinkedHashSet<>();

        // Find the root complex object by name
        String rootPath = findRootPath(rootTypeName, complexObjects);
        if (rootPath != null) {
            usedPaths.add(rootPath);
        }

        // Walk children: if a complex object is used, all complex objects
        // referenced by its fields are also used
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String usedPath : new ArrayList<>(usedPaths)) {
                ComplexObject obj = complexObjects.get(usedPath);
                if (obj == null) continue;
                for (FieldInfo field : obj.fields) {
                    if (isComplexType(field.type)) {
                        String fieldPath = field.path;
                        for (String candidatePath : complexObjects.keySet()) {
                            if (candidatePath.equals(fieldPath) ||
                                candidatePath.startsWith(fieldPath + "[]")) {
                                if (usedPaths.add(candidatePath)) {
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return usedPaths;
    }

    private String findRootPath(String rootTypeName, Map<String, ComplexObject> complexObjects) {
        // Match by name (case-insensitive)
        for (Map.Entry<String, ComplexObject> entry : complexObjects.entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(rootTypeName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isComplexType(String type) {
        return "object".equals(type) || "array".equals(type);
    }

    // ---- Step 4: Build JSON Schema ----

    private ObjectNode buildSchema(String rootTypeName,
                                   Map<String, ComplexObject> complexObjects,
                                   Set<String> usedPaths) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");

        // Find root object by the given root type name
        String rootPath = findRootPath(rootTypeName, complexObjects);
        ComplexObject root = rootPath != null ? complexObjects.get(rootPath) : null;

        if (root == null) {
            System.out.println("  WARNING: Root type '" + rootTypeName + "' not found in Complex Objects sheet.");
            schema.put("type", "object");
            return schema;
        }

        schema.put("title", root.name);
        schema.put("type", "object");

        // Build properties for the root object
        buildObjectProperties(schema, root, complexObjects, usedPaths);

        // Report unused complex objects
        for (Map.Entry<String, ComplexObject> entry : complexObjects.entrySet()) {
            if (!usedPaths.contains(entry.getKey())) {
                System.out.println("  Skipped unused complex object: " + entry.getValue().name
                        + " (" + entry.getKey() + ")");
            }
        }

        return schema;
    }

    private void buildObjectProperties(ObjectNode parentSchema, ComplexObject complexObj,
                                       Map<String, ComplexObject> complexObjects,
                                       Set<String> usedPaths) {
        ObjectNode properties = mapper.createObjectNode();
        ArrayNode requiredArr = mapper.createArrayNode();

        for (FieldInfo field : complexObj.fields) {
            ObjectNode fieldNode = mapper.createObjectNode();

            if (isComplexType(field.type)) {
                // Find the child complex object for this field
                ComplexObject childComplex = findComplexObjectForField(field, complexObjects, usedPaths);

                if ("array".equals(field.type)) {
                    fieldNode.put("type", "array");
                    if (!field.description.isEmpty()) {
                        fieldNode.put("description", field.description);
                    }
                    if (childComplex != null) {
                        ObjectNode items = mapper.createObjectNode();
                        items.put("type", "object");
                        buildObjectProperties(items, childComplex, complexObjects, usedPaths);
                        fieldNode.set("items", items);
                    }
                } else {
                    // object type
                    fieldNode.put("type", "object");
                    if (!field.description.isEmpty()) {
                        fieldNode.put("description", field.description);
                    }
                    if (childComplex != null) {
                        buildObjectProperties(fieldNode, childComplex, complexObjects, usedPaths);
                    }
                }
            } else {
                fieldNode.put("type", field.type.isEmpty() ? "string" : field.type);
                if (!field.description.isEmpty()) {
                    fieldNode.put("description", field.description);
                }
            }

            properties.set(field.name, fieldNode);
            if (field.required) {
                requiredArr.add(field.name);
            }
        }

        if (properties.size() > 0) {
            parentSchema.set("properties", properties);
        }
        if (requiredArr.size() > 0) {
            parentSchema.set("required", requiredArr);
        }
    }

    /**
     * Finds the complex object definition that corresponds to a given field.
     * Matches by path (field.path or field.path + "[]" for arrays).
     * Only returns objects that are in the usedPaths set.
     */
    private ComplexObject findComplexObjectForField(FieldInfo field,
                                                    Map<String, ComplexObject> complexObjects,
                                                    Set<String> usedPaths) {
        // Direct path match
        for (Map.Entry<String, ComplexObject> entry : complexObjects.entrySet()) {
            String objPath = entry.getKey();
            if (!usedPaths.contains(objPath)) continue;

            // For arrays, the complex object's fields live at fieldPath[]
            // For objects, the complex object path matches the field path
            if (objPath.equals(field.path)) {
                return entry.getValue();
            }
        }

        // For fields whose child complex object name matches
        for (Map.Entry<String, ComplexObject> entry : complexObjects.entrySet()) {
            String objPath = entry.getKey();
            if (!usedPaths.contains(objPath)) continue;

            if (objPath.startsWith(field.path + ".") || objPath.startsWith(field.path + "[]")) {
                return entry.getValue();
            }
        }

        return null;
    }

    // ---- Utility ----

    private String getCellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
