package com.example.foxpro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete VCX class library file (one .vcx/.vct pair).
 * A single VCX file can contain multiple class definitions.
 */
public class VcxLibrary {

    private final String fileName;
    private final String filePath;
    private final List<VcxClass> classes;

    public VcxLibrary(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.classes = new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<VcxClass> getClasses() {
        return classes;
    }

    public void addClass(VcxClass vcxClass) {
        this.classes.add(vcxClass);
    }

    @Override
    public String toString() {
        return "VcxLibrary{" + fileName + ", classes=" + classes.size() + "}";
    }
}
