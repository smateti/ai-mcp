package com.example.foxpro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a FoxPro menu system extracted from an MNX file.
 *
 * MNX files are DBF tables where each record defines a menu item.
 * The .mnt companion file stores memo field content (code snippets, prompts).
 *
 * Key MNX table columns:
 * - OBJTYPE  (N): 1=Menu definition, 2=Menu bar, 3=Menu popup/pad, 4=Menu item
 * - OBJCODE  (N): Item type: 0=Command, 67(C)=Submenu, 78(N)=Procedure/snippet
 * - NAME     (M): Internal name of the menu item
 * - PROMPT   (M): Display text shown to user (the menu caption)
 * - COMMAND  (M): FoxPro command to execute when item is selected
 * - MESSAGE  (M): Status bar message
 * - PROCEDURE(M): Multi-line procedure code (for snippet-type items)
 * - SETUP    (M): Setup code that runs before menu is activated
 * - CLEANUP  (M): Cleanup code that runs when menu is deactivated
 * - MARK     (C): Check mark character
 * - KEYNAME  (M): Keyboard shortcut name (e.g., "CTRL+S")
 * - KEYLABEL (M): Keyboard shortcut display label
 * - SKIPFOR  (M): SKIP FOR expression (condition to disable item)
 * - LEVELNAME(M): Name of the menu level/popup
 * - NUMITEMS (N): Number of items in this level
 * - LOCATION (N): Menu location type
 * - SCHEME   (N): Color scheme number
 * - SYSRES   (N): System resource flag
 * - RESNAME  (M): Resource name
 */
public class MnxMenu {

    private final String fileName;
    private final String filePath;
    private String setupCode;
    private String cleanupCode;
    private final List<MnxMenuItem> menuItems;
    private final List<MnxMenuPad> menuPads;

    public MnxMenu(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.menuItems = new ArrayList<>();
        this.menuPads = new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSetupCode() {
        return setupCode;
    }

    public void setSetupCode(String setupCode) {
        this.setupCode = setupCode;
    }

    public String getCleanupCode() {
        return cleanupCode;
    }

    public void setCleanupCode(String cleanupCode) {
        this.cleanupCode = cleanupCode;
    }

    public List<MnxMenuItem> getMenuItems() {
        return menuItems;
    }

    public void addMenuItem(MnxMenuItem item) {
        this.menuItems.add(item);
    }

    public List<MnxMenuPad> getMenuPads() {
        return menuPads;
    }

    public void addMenuPad(MnxMenuPad pad) {
        this.menuPads.add(pad);
    }

    /**
     * Returns all executable code found in this menu for LLM analysis.
     */
    public String getAllCode() {
        StringBuilder sb = new StringBuilder();

        if (setupCode != null && !setupCode.isBlank()) {
            sb.append("* === MENU SETUP CODE ===\n");
            sb.append(setupCode).append("\n\n");
        }

        for (MnxMenuPad pad : menuPads) {
            sb.append("* === MENU PAD: ").append(pad.getPrompt()).append(" ===\n");
            if (pad.getSkipFor() != null && !pad.getSkipFor().isBlank()) {
                sb.append("* SKIP FOR: ").append(pad.getSkipFor()).append("\n");
            }
            sb.append("\n");
        }

        for (MnxMenuItem item : menuItems) {
            if (item.hasCode()) {
                sb.append("* --- Menu Item: ").append(item.getPrompt());
                if (item.getKeyName() != null && !item.getKeyName().isBlank()) {
                    sb.append(" [").append(item.getKeyName()).append("]");
                }
                sb.append(" ---\n");

                if (item.getCommand() != null && !item.getCommand().isBlank()) {
                    sb.append("* Command: ").append(item.getCommand()).append("\n");
                }
                if (item.getProcedure() != null && !item.getProcedure().isBlank()) {
                    sb.append("* Procedure:\n").append(item.getProcedure()).append("\n");
                }
                if (item.getSkipFor() != null && !item.getSkipFor().isBlank()) {
                    sb.append("* SKIP FOR: ").append(item.getSkipFor()).append("\n");
                }
                sb.append("\n");
            }
        }

        if (cleanupCode != null && !cleanupCode.isBlank()) {
            sb.append("* === MENU CLEANUP CODE ===\n");
            sb.append(cleanupCode).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Returns a structural summary of the menu hierarchy.
     */
    public String getMenuStructure() {
        StringBuilder sb = new StringBuilder();
        sb.append("Menu: ").append(fileName).append("\n");
        sb.append("Pads (top-level): ").append(menuPads.size()).append("\n");
        sb.append("Items (total): ").append(menuItems.size()).append("\n");

        for (MnxMenuPad pad : menuPads) {
            sb.append("  [PAD] ").append(pad.getPrompt()).append("\n");
        }

        for (MnxMenuItem item : menuItems) {
            sb.append("    [ITEM] ").append(item.getPrompt());
            if (item.getLevelName() != null && !item.getLevelName().isBlank()) {
                sb.append(" (level: ").append(item.getLevelName()).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "MnxMenu{" + fileName + ", pads=" + menuPads.size() +
                ", items=" + menuItems.size() + "}";
    }
}
