package com.example.foxpro.model;

/**
 * Represents a single menu item within an MNX menu definition.
 */
public class MnxMenuItem {

    private final String name;
    private final String prompt;
    private final int objType;
    private final int objCode;
    private String command;
    private String procedure;
    private String message;
    private String keyName;
    private String keyLabel;
    private String skipFor;
    private String levelName;

    public MnxMenuItem(String name, String prompt, int objType, int objCode) {
        this.name = name;
        this.prompt = prompt;
        this.objType = objType;
        this.objCode = objCode;
    }

    public String getName() {
        return name;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getObjType() {
        return objType;
    }

    public int getObjCode() {
        return objCode;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getProcedure() {
        return procedure;
    }

    public void setProcedure(String procedure) {
        this.procedure = procedure;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyLabel() {
        return keyLabel;
    }

    public void setKeyLabel(String keyLabel) {
        this.keyLabel = keyLabel;
    }

    public String getSkipFor() {
        return skipFor;
    }

    public void setSkipFor(String skipFor) {
        this.skipFor = skipFor;
    }

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }

    /**
     * Returns true if this menu item has any executable code.
     */
    public boolean hasCode() {
        return (command != null && !command.isBlank()) ||
               (procedure != null && !procedure.isBlank());
    }

    /**
     * Returns a description of the item type based on OBJCODE.
     */
    public String getItemTypeDescription() {
        return switch (objCode) {
            case 0 -> "Command";
            case 67 -> "Submenu";  // 'C'
            case 78 -> "Procedure"; // 'N'
            case 80 -> "Pad";      // 'P'
            default -> "Unknown(" + objCode + ")";
        };
    }

    @Override
    public String toString() {
        return "MenuItem{" + prompt + " [" + getItemTypeDescription() + "]}";
    }
}
