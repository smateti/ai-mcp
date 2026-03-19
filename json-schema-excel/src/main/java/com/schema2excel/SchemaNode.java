package com.schema2excel;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the JSON Schema hierarchy.
 */
public class SchemaNode {

    private String name;
    private String type;
    private String description;
    private String parent;
    private String path;
    private boolean required;
    private List<SchemaNode> children = new ArrayList<>();

    public SchemaNode(String name, String type, String description, String parent, String path, boolean required) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.parent = parent;
        this.path = path;
        this.required = required;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public String getParent() { return parent; }
    public String getPath() { return path; }
    public boolean isRequired() { return required; }
    public List<SchemaNode> getChildren() { return children; }

    public void addChild(SchemaNode child) { children.add(child); }

    public boolean isComplex() {
        return "object".equals(type) || "array".equals(type);
    }
}
