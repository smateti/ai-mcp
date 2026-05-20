package com.example.foxpro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a class definition extracted from a VCX (Visual Class Library) file.
 *
 * VCX files are DBF-structured tables where each record can be:
 * - A class header (Platform = "COMMENT", defines class metadata)
 * - An object/control within a class
 * - Method code associated with objects
 *
 * Key VCX table columns:
 * - PLATFORM:  "COMMENT" for class header, "WINDOWS" for objects
 * - UNIQUEID:  Unique record identifier
 * - CLASS:     The class name (for instances) or parent class (for headers)
 * - BASECLASS: The VFP base class this derives from
 * - OBJNAME:   Object/class name
 * - PARENT:    Parent container object name
 * - PROPERTIES: Property definitions (memo field)
 * - METHODS:   Method source code (memo field)
 * - RESERVED1-8: Various metadata fields
 */
public class VcxClass {

    private final String className;
    private final String baseClass;
    private final String parentClass;
    private final String libraryFile;
    private String description;
    private String properties;
    private String methods;
    private final List<VcxObject> containedObjects;

    public VcxClass(String className, String baseClass, String parentClass, String libraryFile) {
        this.className = className;
        this.baseClass = baseClass;
        this.parentClass = parentClass;
        this.libraryFile = libraryFile;
        this.containedObjects = new ArrayList<>();
    }

    public String getClassName() {
        return className;
    }

    public String getBaseClass() {
        return baseClass;
    }

    public String getParentClass() {
        return parentClass;
    }

    public String getLibraryFile() {
        return libraryFile;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getMethods() {
        return methods;
    }

    public void setMethods(String methods) {
        this.methods = methods;
    }

    public List<VcxObject> getContainedObjects() {
        return containedObjects;
    }

    public void addObject(VcxObject obj) {
        this.containedObjects.add(obj);
    }

    /**
     * Returns the full method code for this class including all contained objects.
     */
    public String getAllMethodCode() {
        StringBuilder sb = new StringBuilder();

        if (methods != null && !methods.isBlank()) {
            sb.append("* === Class-level methods for: ").append(className).append(" ===\n");
            sb.append(methods).append("\n\n");
        }

        for (VcxObject obj : containedObjects) {
            if (obj.getMethods() != null && !obj.getMethods().isBlank()) {
                sb.append("* === Methods for object: ").append(obj.getObjName())
                  .append(" (").append(obj.getBaseClass()).append(") ===\n");
                sb.append(obj.getMethods()).append("\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * Returns a textual representation of properties for analysis.
     */
    public String getAllProperties() {
        StringBuilder sb = new StringBuilder();

        if (properties != null && !properties.isBlank()) {
            sb.append("* Class properties:\n").append(properties).append("\n\n");
        }

        for (VcxObject obj : containedObjects) {
            if (obj.getProperties() != null && !obj.getProperties().isBlank()) {
                sb.append("* Properties for ").append(obj.getObjName()).append(":\n");
                sb.append(obj.getProperties()).append("\n\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "VcxClass{" + className + " extends " + baseClass +
                ", objects=" + containedObjects.size() + "}";
    }
}
