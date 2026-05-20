package com.example.foxpro.model;

/**
 * Represents an object (control/member) contained within a VCX class.
 * Each control on a form or container class gets its own record in the VCX table.
 */
public class VcxObject {

    private final String objName;
    private final String className;
    private final String baseClass;
    private final String parentObj;
    private String properties;
    private String methods;

    public VcxObject(String objName, String className, String baseClass, String parentObj) {
        this.objName = objName;
        this.className = className;
        this.baseClass = baseClass;
        this.parentObj = parentObj;
    }

    public String getObjName() {
        return objName;
    }

    public String getClassName() {
        return className;
    }

    public String getBaseClass() {
        return baseClass;
    }

    public String getParentObj() {
        return parentObj;
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

    @Override
    public String toString() {
        return "VcxObject{" + objName + " (" + baseClass + ")}";
    }
}
