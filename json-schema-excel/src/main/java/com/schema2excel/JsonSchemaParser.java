package com.schema2excel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Parses a JSON Schema file and builds a tree of SchemaNode objects.
 * Supports $ref resolution, nested objects, arrays, allOf/oneOf/anyOf.
 */
public class JsonSchemaParser {

    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode rootSchema;

    public SchemaNode parse(String filePath, String rootTypeName) throws IOException {
        rootSchema = mapper.readTree(new File(filePath));

        // Try to find the root type in definitions/schemas first
        JsonNode targetSchema = findDefinition(rootTypeName);
        if (targetSchema == null) {
            // Fall back to root schema itself
            targetSchema = rootSchema;
        }

        String rootName = rootTypeName != null ? rootTypeName
                : getTextOrDefault(targetSchema, "title", "Root");
        String rootType = getTextOrDefault(targetSchema, "type", "object");
        String rootDesc = getTextOrDefault(targetSchema, "description", "");

        SchemaNode root = new SchemaNode(rootName, rootType, rootDesc, "", rootName, false);
        parseProperties(targetSchema, root, rootName);
        return root;
    }

    private JsonNode findDefinition(String name) {
        if (name == null) return null;

        // Search in definitions, $defs, and root properties
        for (String defKey : List.of("definitions", "$defs")) {
            if (rootSchema.has(defKey)) {
                JsonNode defs = rootSchema.get(defKey);
                if (defs.has(name)) {
                    return defs.get(name);
                }
            }
        }

        // Check if root title matches the requested name
        String title = getTextOrDefault(rootSchema, "title", "");
        if (name.equalsIgnoreCase(title)) {
            return rootSchema;
        }

        return null;
    }

    private void parseProperties(JsonNode schemaNode, SchemaNode parentNode, String parentPath) {
        JsonNode resolved = resolveRef(schemaNode);

        // Handle allOf / oneOf / anyOf by merging their properties
        for (String combiner : List.of("allOf", "oneOf", "anyOf")) {
            if (resolved.has(combiner) && resolved.get(combiner).isArray()) {
                for (JsonNode subSchema : resolved.get(combiner)) {
                    parseProperties(subSchema, parentNode, parentPath);
                }
            }
        }

        Set<String> requiredFields = getRequiredFields(resolved);

        JsonNode properties = resolved.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode fieldSchema = resolveRef(entry.getValue());
                String fieldPath = parentPath + "." + fieldName;
                boolean isRequired = requiredFields.contains(fieldName);

                String type = determineType(fieldSchema);
                String desc = getTextOrDefault(fieldSchema, "description", "");

                SchemaNode childNode = new SchemaNode(fieldName, type, desc, parentNode.getName(), fieldPath, isRequired);
                parentNode.addChild(childNode);

                // Recurse into nested objects
                if ("object".equals(type)) {
                    parseProperties(fieldSchema, childNode, fieldPath);
                }

                // Recurse into array items
                if ("array".equals(type) && fieldSchema.has("items")) {
                    JsonNode items = resolveRef(fieldSchema.get("items"));
                    String itemType = determineType(items);
                    if ("object".equals(itemType)) {
                        parseProperties(items, childNode, fieldPath + "[]");
                    }
                }
            }
        }
    }

    private JsonNode resolveRef(JsonNode node) {
        if (node == null) return node;
        if (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            // Handle internal references like "#/definitions/Address"
            if (ref.startsWith("#/")) {
                String[] parts = ref.substring(2).split("/");
                JsonNode current = rootSchema;
                for (String part : parts) {
                    current = current.get(part);
                    if (current == null) return node;
                }
                return resolveRef(current);
            }
        }
        return node;
    }

    private String determineType(JsonNode fieldSchema) {
        if (fieldSchema.has("type")) {
            return fieldSchema.get("type").asText();
        }
        if (fieldSchema.has("properties")) {
            return "object";
        }
        if (fieldSchema.has("items")) {
            return "array";
        }
        if (fieldSchema.has("allOf") || fieldSchema.has("oneOf") || fieldSchema.has("anyOf")) {
            return "object";
        }
        if (fieldSchema.has("enum")) {
            return "enum";
        }
        return "unknown";
    }

    private Set<String> getRequiredFields(JsonNode schemaNode) {
        Set<String> required = new HashSet<>();
        if (schemaNode.has("required") && schemaNode.get("required").isArray()) {
            for (JsonNode r : schemaNode.get("required")) {
                required.add(r.asText());
            }
        }
        return required;
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && node.get(field).isTextual()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
