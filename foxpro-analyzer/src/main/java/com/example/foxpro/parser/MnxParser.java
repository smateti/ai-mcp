package com.example.foxpro.parser;

import com.example.foxpro.model.MnxMenu;
import com.example.foxpro.model.MnxMenuItem;
import com.example.foxpro.model.MnxMenuPad;
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
 * Parses Visual FoxPro menu definition files (.mnx/.mnt).
 *
 * MNX files are DBF tables where each record defines a menu component.
 * The .mnt companion file stores memo field content (code snippets, prompts).
 *
 * MNX Record Types (OBJTYPE field):
 *   1 = Menu system header (contains setup/cleanup code)
 *   2 = Menu bar definition
 *   3 = Menu popup/submenu definition (pad)
 *   4 = Menu item (actual clickable items)
 *
 * MNX Item Types (OBJCODE field):
 *   0   = Command (executes a single-line command)
 *   67  = Submenu reference ('C' in ASCII - links to a popup)
 *   78  = Procedure snippet ('N' in ASCII - multi-line code block)
 *   80  = Pad definition ('P' in ASCII)
 *   22  = Bar number reference
 *
 * The menu structure is hierarchical:
 *   Menu Bar -> Pads -> Popups -> Items
 *
 * .mna files are compiled/app versions (similar to .vca for classes).
 */
public class MnxParser {

    private static final Logger logger = LoggerFactory.getLogger(MnxParser.class);
    private static final Charset VFP_CHARSET = Charset.forName("windows-1252");

    /**
     * Scans a directory (recursively) for .mnx files and parses each one.
     */
    public List<MnxMenu> parseDirectory(String directoryPath) throws IOException {
        List<MnxMenu> menus = new ArrayList<>();
        Path dir = Paths.get(directoryPath);

        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a valid directory: " + directoryPath);
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().toLowerCase().endsWith(".mnx"))
                 .sorted()
                 .forEach(path -> {
                     try {
                         MnxMenu menu = parseMnxFile(path);
                         if (menu != null) {
                             menus.add(menu);
                             logger.info("Parsed MNX: {} ({} pads, {} items)",
                                     menu.getFileName(),
                                     menu.getMenuPads().size(),
                                     menu.getMenuItems().size());
                         }
                     } catch (Exception e) {
                         logger.error("Failed to parse MNX: {}", path, e);
                     }
                 });
        }

        return menus;
    }

    /**
     * Parses a single .mnx file (with its companion .mnt memo file).
     */
    public MnxMenu parseMnxFile(Path mnxPath) throws IOException {
        String fileName = mnxPath.getFileName().toString();
        MnxMenu menu = new MnxMenu(fileName, mnxPath.toString());

        // Verify companion .mnt file exists
        Path mntPath = getCompanionFile(mnxPath, ".mnt");
        if (!Files.exists(mntPath)) {
            logger.warn("Missing .mnt memo file for: {}. Code snippets won't be available.", mnxPath);
        }

        // Try reading as DBF
        List<MnxRecord> records = readDbfRecords(mnxPath);

        if (records.isEmpty()) {
            // Fallback: try extracting from .mnt as raw text
            records = readWithFallback(mnxPath);
        }

        if (records.isEmpty()) {
            logger.warn("No records found in: {}", mnxPath);
            return menu;
        }

        // Process records by type
        for (MnxRecord record : records) {
            switch (record.objType) {
                case 1:
                    // Menu system header - contains setup/cleanup code
                    menu.setSetupCode(safe(record.setup));
                    menu.setCleanupCode(safe(record.cleanup));
                    break;

                case 2:
                    // Menu bar - usually just structural
                    break;

                case 3:
                    // Menu pad (top-level menu bar item)
                    MnxMenuPad pad = new MnxMenuPad(
                            safe(record.name),
                            safe(record.prompt)
                    );
                    pad.setSkipFor(safe(record.skipFor));
                    pad.setMessage(safe(record.message));
                    menu.addMenuPad(pad);
                    break;

                case 4:
                    // Menu item (clickable item within a popup)
                    MnxMenuItem item = new MnxMenuItem(
                            safe(record.name),
                            safe(record.prompt),
                            record.objType,
                            record.objCode
                    );
                    item.setCommand(safe(record.command));
                    item.setProcedure(safe(record.procedure));
                    item.setMessage(safe(record.message));
                    item.setKeyName(safe(record.keyName));
                    item.setKeyLabel(safe(record.keyLabel));
                    item.setSkipFor(safe(record.skipFor));
                    item.setLevelName(safe(record.levelName));
                    menu.addMenuItem(item);
                    break;

                default:
                    // Unknown type - still capture if it has code
                    if (!safe(record.command).isBlank() || !safe(record.procedure).isBlank()) {
                        MnxMenuItem unknownItem = new MnxMenuItem(
                                safe(record.name),
                                safe(record.prompt),
                                record.objType,
                                record.objCode
                        );
                        unknownItem.setCommand(safe(record.command));
                        unknownItem.setProcedure(safe(record.procedure));
                        menu.addMenuItem(unknownItem);
                    }
                    break;
            }
        }

        return menu;
    }

    /**
     * Reads all records from an MNX (DBF-format) file using JavaDBF.
     */
    private List<MnxRecord> readDbfRecords(Path mnxPath) throws IOException {
        List<MnxRecord> records = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(mnxPath.toFile());
             DBFReader reader = new DBFReader(fis, VFP_CHARSET)) {

            int fieldCount = reader.getFieldCount();
            logger.debug("MNX {} has {} fields", mnxPath.getFileName(), fieldCount);

            // Build field name to index map
            Map<String, Integer> fieldMap = new HashMap<>();
            for (int i = 0; i < fieldCount; i++) {
                fieldMap.put(reader.getField(i).getName().toUpperCase(), i);
            }

            DBFRow row;
            while ((row = reader.nextRow()) != null) {
                MnxRecord record = new MnxRecord();
                record.objType = getIntField(row, fieldMap, "OBJTYPE");
                record.objCode = getIntField(row, fieldMap, "OBJCODE");
                record.name = getStringField(row, "NAME");
                record.prompt = getStringField(row, "PROMPT");
                record.command = getStringField(row, "COMMAND");
                record.message = getStringField(row, "MESSAGE");
                record.procedure = getStringField(row, "PROCEDURE");
                record.setup = getStringField(row, "SETUP");
                record.cleanup = getStringField(row, "CLEANUP");
                record.keyName = getStringField(row, "KEYNAME");
                record.keyLabel = getStringField(row, "KEYLABEL");
                record.skipFor = getStringField(row, "SKIPFOR");
                record.levelName = getStringField(row, "LEVELNAME");
                record.numItems = getIntField(row, fieldMap, "NUMITEMS");
                records.add(record);
            }

        } catch (Exception e) {
            logger.error("Error reading MNX DBF file {}: {}", mnxPath, e.getMessage());
            return new ArrayList<>();
        }

        return records;
    }

    /**
     * Fallback reader for MNX files that JavaDBF can't handle.
     * Attempts to extract code from the .mnt memo file as raw text.
     */
    private List<MnxRecord> readWithFallback(Path mnxPath) {
        logger.info("Attempting fallback parsing for: {}", mnxPath);
        List<MnxRecord> records = new ArrayList<>();

        try {
            Path mntPath = getCompanionFile(mnxPath, ".mnt");
            if (Files.exists(mntPath)) {
                String content = Files.readString(mntPath, VFP_CHARSET);

                // Look for recognizable code patterns in the memo file
                // MNT files contain blocks separated by null bytes or specific markers
                String[] blocks = content.split("\u0000+");

                for (String block : blocks) {
                    String trimmed = block.trim();
                    if (trimmed.isEmpty() || trimmed.length() < 5) continue;

                    // Check if this block looks like executable code
                    if (looksLikeCode(trimmed)) {
                        MnxRecord record = new MnxRecord();
                        record.objType = 4; // Treat as menu item

                        if (trimmed.contains("\n")) {
                            // Multi-line = procedure
                            record.objCode = 78;
                            record.procedure = trimmed;
                            record.prompt = extractFirstComment(trimmed);
                        } else {
                            // Single line = command
                            record.objCode = 0;
                            record.command = trimmed;
                            record.prompt = trimmed.length() > 50
                                    ? trimmed.substring(0, 50) + "..."
                                    : trimmed;
                        }

                        record.name = "extracted_" + records.size();
                        records.add(record);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Fallback parsing failed for: {}", mnxPath, e);
        }

        return records;
    }

    /**
     * Heuristic check if a text block looks like FoxPro code.
     */
    private boolean looksLikeCode(String text) {
        String upper = text.toUpperCase();
        return upper.contains("DO ") || upper.contains("SET ") ||
               upper.contains("IF ") || upper.contains("SELECT") ||
               upper.contains("THISFORM") || upper.contains("THIS.") ||
               upper.startsWith("*") || upper.contains("PROCEDURE") ||
               upper.contains("PUBLIC") || upper.contains("LOCAL") ||
               upper.contains("MESSAGEBOX") || upper.contains("USE ");
    }

    /**
     * Extracts the first comment line from a code block for use as a label.
     */
    private String extractFirstComment(String code) {
        for (String line : code.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("&&")) {
                return trimmed.substring(1).trim();
            }
        }
        String firstLine = code.split("\n")[0].trim();
        return firstLine.length() > 50 ? firstLine.substring(0, 50) + "..." : firstLine;
    }

    private String getStringField(DBFRow row, String fieldName) {
        try {
            String value = row.getString(fieldName);
            return value != null ? value : "";
        } catch (Exception e) {
            return "";
        }
    }

    private int getIntField(DBFRow row, Map<String, Integer> fieldMap, String fieldName) {
        if (!fieldMap.containsKey(fieldName)) return 0;
        try {
            Number value = (Number) row.getObject(fieldName);
            return value != null ? value.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private Path getCompanionFile(Path original, String newExtension) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String baseName = (dot > 0) ? name.substring(0, dot) : name;
        return original.getParent().resolve(baseName + newExtension);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Internal record structure matching MNX table columns.
     */
    private static class MnxRecord {
        int objType;
        int objCode;
        String name;
        String prompt;
        String command;
        String message;
        String procedure;
        String setup;
        String cleanup;
        String keyName;
        String keyLabel;
        String skipFor;
        String levelName;
        int numItems;
    }
}
