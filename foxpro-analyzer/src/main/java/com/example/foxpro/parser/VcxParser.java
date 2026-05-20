package com.example.foxpro.parser;

import com.example.foxpro.model.VcxClass;
import com.example.foxpro.model.VcxLibrary;
import com.example.foxpro.model.VcxObject;
import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Parses Visual FoxPro class library files (.vcx/.vct).
 *
 * VCX files are DBF (dBASE) table files with an accompanying .vct memo file (FPT format).
 * The table structure contains these key fields:
 *
 * - PLATFORM   (C): "COMMENT" = class header, "WINDOWS" = object record, "" = file header
 * - UNIQUEID   (C): Unique identifier for each record
 * - TIMESTAMP  (N): Modification timestamp
 * - CLASS      (M): For objects: the class they're an instance of. For class headers: parent class
 * - CLASSLOC   (M): Path to the library containing the parent class
 * - BASECLASS  (M): The VFP base class (form, commandbutton, textbox, etc.)
 * - OBJNAME    (M): Name of the class or object
 * - PARENT     (M): Parent container name (for contained objects)
 * - PROPERTIES (M): Property name=value pairs, one per line
 * - PROTECTED  (M): List of protected properties/methods
 * - METHODS    (M): Complete method source code for this object
 * - OBJCODE    (M): Compiled p-code (binary, skip)
 * - OLE        (M): OLE object data (binary, skip)
 * - OLE2       (M): Additional OLE data
 * - RESERVED1  (M): Class description / metadata
 * - RESERVED2  (M): Class icon file
 * - RESERVED3  (M): Designer metadata
 * - RESERVED4-8(M): Various internal metadata
 * - USER       (M): User-defined metadata
 *
 * Note: .vca files are typically the compiled/app version of class libraries.
 * They have similar structure but may not be directly readable as DBF.
 */
public class VcxParser {

    private static final Logger logger = LoggerFactory.getLogger(VcxParser.class);

    private static final Charset VFP_CHARSET = Charset.forName("windows-1252");

    /**
     * Scans a directory (recursively) for .vcx files and parses each one.
     */
    public List<VcxLibrary> parseDirectory(String directoryPath) throws IOException {
        List<VcxLibrary> libraries = new ArrayList<>();
        Path dir = Paths.get(directoryPath);

        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a valid directory: " + directoryPath);
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".vcx");
                    })
                 .sorted()
                 .forEach(path -> {
                     try {
                         VcxLibrary library = parseVcxFile(path);
                         if (library != null) {
                             libraries.add(library);
                             logger.info("Parsed VCX: {} ({} classes)",
                                     library.getFileName(), library.getClasses().size());
                         }
                     } catch (Exception e) {
                         logger.error("Failed to parse VCX: {}", path, e);
                     }
                 });
        }

        return libraries;
    }

    /**
     * Parses a single .vcx file (with its companion .vct memo file).
     * The .vct file must be in the same directory as the .vcx file.
     */
    public VcxLibrary parseVcxFile(Path vcxPath) throws IOException {
        String fileName = vcxPath.getFileName().toString();
        VcxLibrary library = new VcxLibrary(fileName, vcxPath.toString());

        // Verify companion .vct file exists
        Path vctPath = getCompanionFile(vcxPath, ".vct");
        if (!Files.exists(vctPath)) {
            logger.warn("Missing .vct memo file for: {}. Methods/properties won't be available.", vcxPath);
        }

        // Read all records from the VCX (DBF) file
        List<VcxRecord> records = readDbfRecords(vcxPath);

        if (records.isEmpty()) {
            logger.warn("No records found in: {}", vcxPath);
            return library;
        }

        // Group records by class: find class headers and their child objects
        Map<String, VcxClass> classMap = new LinkedHashMap<>();
        Map<String, String> recordToClass = new HashMap<>(); // maps record uniqueid to parent class

        // First pass: identify class header records
        for (VcxRecord record : records) {
            if (isClassHeader(record)) {
                String className = safe(record.objName);
                String baseClass = safe(record.baseClass);
                String parentClass = safe(record.classField);

                VcxClass vcxClass = new VcxClass(className, baseClass, parentClass, fileName);
                vcxClass.setDescription(safe(record.reserved1));
                vcxClass.setProperties(safe(record.properties));
                vcxClass.setMethods(safe(record.methods));

                classMap.put(className, vcxClass);
                library.addClass(vcxClass);
            }
        }

        // Second pass: assign object records to their parent class
        String currentClassName = null;
        for (VcxRecord record : records) {
            if (isClassHeader(record)) {
                currentClassName = safe(record.objName);
            } else if (isObjectRecord(record) && currentClassName != null) {
                VcxClass parentClassObj = classMap.get(currentClassName);
                if (parentClassObj != null) {
                    VcxObject obj = new VcxObject(
                            safe(record.objName),
                            safe(record.classField),
                            safe(record.baseClass),
                            safe(record.parent)
                    );
                    obj.setProperties(safe(record.properties));
                    obj.setMethods(safe(record.methods));
                    parentClassObj.addObject(obj);
                }
            }
        }

        return library;
    }

    /**
     * Reads all records from a VCX (DBF-format) file using JavaDBF.
     */
    private List<VcxRecord> readDbfRecords(Path vcxPath) throws IOException {
        List<VcxRecord> records = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(vcxPath.toFile());
             DBFReader reader = new DBFReader(fis, VFP_CHARSET)) {

            // Log field info for debugging
            int fieldCount = reader.getFieldCount();
            logger.debug("VCX {} has {} fields", vcxPath.getFileName(), fieldCount);

            // Build field name to index map
            Map<String, Integer> fieldMap = new HashMap<>();
            for (int i = 0; i < fieldCount; i++) {
                fieldMap.put(reader.getField(i).getName().toUpperCase(), i);
            }

            DBFRow row;
            while ((row = reader.nextRow()) != null) {
                VcxRecord record = new VcxRecord();
                record.platform = getFieldValue(row, fieldMap, "PLATFORM");
                record.uniqueId = getFieldValue(row, fieldMap, "UNIQUEID");
                record.classField = getFieldValue(row, fieldMap, "CLASS");
                record.classLoc = getFieldValue(row, fieldMap, "CLASSLOC");
                record.baseClass = getFieldValue(row, fieldMap, "BASECLASS");
                record.objName = getFieldValue(row, fieldMap, "OBJNAME");
                record.parent = getFieldValue(row, fieldMap, "PARENT");
                record.properties = getFieldValue(row, fieldMap, "PROPERTIES");
                record.methods = getFieldValue(row, fieldMap, "METHODS");
                record.reserved1 = getFieldValue(row, fieldMap, "RESERVED1");
                record.reserved2 = getFieldValue(row, fieldMap, "RESERVED2");
                record.protectedField = getFieldValue(row, fieldMap, "PROTECTED");
                record.user = getFieldValue(row, fieldMap, "USER");
                records.add(record);
            }

        } catch (Exception e) {
            logger.error("Error reading DBF file {}: {}", vcxPath, e.getMessage());
            // Try fallback approach for non-standard VFP files
            records = readWithFallback(vcxPath);
        }

        return records;
    }

    /**
     * Fallback reader for VCX files that JavaDBF can't handle directly.
     * Attempts raw binary parsing of the memo (FPT) file.
     */
    private List<VcxRecord> readWithFallback(Path vcxPath) {
        logger.info("Attempting fallback parsing for: {}", vcxPath);
        List<VcxRecord> records = new ArrayList<>();

        try {
            Path vctPath = getCompanionFile(vcxPath, ".vct");
            if (Files.exists(vctPath)) {
                // Read the .vct file as raw text, looking for method blocks
                // VCT memo blocks often contain readable FoxPro code
                String content = Files.readString(vctPath, VFP_CHARSET);

                // Extract PROCEDURE/FUNCTION blocks from the memo content
                String[] blocks = content.split("(?i)(?=PROCEDURE\\s|FUNCTION\\s)");

                VcxRecord syntheticRecord = new VcxRecord();
                syntheticRecord.platform = "WINDOWS";
                syntheticRecord.objName = vcxPath.getFileName().toString().replace(".vcx", "");
                syntheticRecord.baseClass = "custom";

                StringBuilder allMethods = new StringBuilder();
                for (String block : blocks) {
                    String trimmed = block.trim();
                    if (trimmed.toUpperCase().startsWith("PROCEDURE") ||
                        trimmed.toUpperCase().startsWith("FUNCTION")) {
                        allMethods.append(trimmed).append("\n\n");
                    }
                }
                syntheticRecord.methods = allMethods.toString();
                if (!syntheticRecord.methods.isBlank()) {
                    records.add(syntheticRecord);
                }
            }
        } catch (IOException e) {
            logger.error("Fallback parsing also failed for: {}", vcxPath, e);
        }

        return records;
    }

    /**
     * Determines if a record is a class header (defines a new class).
     * Class headers typically have PLATFORM="COMMENT" or are the first record
     * after the file header with a non-empty OBJNAME and BASECLASS.
     */
    private boolean isClassHeader(VcxRecord record) {
        String platform = safe(record.platform).trim().toUpperCase();
        String objName = safe(record.objName).trim();
        String baseClass = safe(record.baseClass).trim();

        // Class headers: PLATFORM is "COMMENT" or empty string with valid class info
        // The first record (file header) usually has PLATFORM="" and no OBJNAME
        if (objName.isEmpty() || baseClass.isEmpty()) {
            return false;
        }

        // In VCX files, class headers have an empty PARENT field
        String parent = safe(record.parent).trim();
        return parent.isEmpty();
    }

    /**
     * Determines if a record is an object within a class.
     */
    private boolean isObjectRecord(VcxRecord record) {
        String objName = safe(record.objName).trim();
        String parent = safe(record.parent).trim();

        // Object records have both OBJNAME and PARENT filled
        return !objName.isEmpty() && !parent.isEmpty();
    }

    /**
     * Safely reads a string field value from a DBF row.
     */
    private String getFieldValue(DBFRow row, Map<String, Integer> fieldMap, String fieldName) {
        Integer index = fieldMap.get(fieldName);
        if (index == null) {
            return "";
        }
        try {
            String value = row.getString(fieldName);
            return value != null ? value : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Gets the companion file path (e.g., .vcx -> .vct).
     */
    private Path getCompanionFile(Path original, String newExtension) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String baseName = (dot > 0) ? name.substring(0, dot) : name;
        return original.getParent().resolve(baseName + newExtension);
    }

    /**
     * Null-safe string utility.
     */
    private String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Internal record structure matching VCX table columns.
     */
    private static class VcxRecord {
        String platform;
        String uniqueId;
        String classField;
        String classLoc;
        String baseClass;
        String objName;
        String parent;
        String properties;
        String methods;
        String reserved1;
        String reserved2;
        String protectedField;
        String user;
    }
}
