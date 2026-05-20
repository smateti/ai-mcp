"""
Extractor for UI elements: buttons, menu items, form controls, dialogs,
and their associated event handlers / actions.

Scans all source files and extracts:
- Menu bars and items (DEFINE PAD/BAR, ON SELECTION)
- Buttons and controls (ADD OBJECT, CommandButton)
- Event handlers (PROCEDURE obj.Click, obj.DblClick, etc.)
- Form references (DO FORM, THISFORM interactions)
- User dialogs (MESSAGEBOX, INPUTBOX, WAIT WINDOW)
"""
import logging
import os
import re

from extractors.base_extractor import BaseExtractor

logger = logging.getLogger(__name__)


class UiExtractor(BaseExtractor):
    """Extracts UI controls, buttons, menus, and their event handlers."""

    # Regex patterns for UI constructs
    PATTERNS = {
        # Menu system
        "define_pad": re.compile(
            r'^\s*DEFINE\s+PAD\s+(\w+)\s+OF\s+(\w+)\s+PROMPT\s+"([^"]*)"',
            re.IGNORECASE | re.MULTILINE,
        ),
        "define_bar": re.compile(
            r'^\s*DEFINE\s+BAR\s+(\d+)\s+OF\s+(\w+)\s+PROMPT\s+"([^"]*)"',
            re.IGNORECASE | re.MULTILINE,
        ),
        "on_selection_bar": re.compile(
            r"^\s*ON\s+SELECTION\s+BAR\s+(\d+)\s+OF\s+(\w+)\s+(.+?)$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "on_selection_pad": re.compile(
            r"^\s*ON\s+SELECTION\s+PAD\s+(\w+)\s+OF\s+(\w+)\s+(.+?)$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "on_pad": re.compile(
            r"^\s*ON\s+PAD\s+(\w+)\s+OF\s+(\w+)\s+ACTIVATE\s+POPUP\s+(\w+)",
            re.IGNORECASE | re.MULTILINE,
        ),
        # Form controls
        "add_object": re.compile(
            r"^\s*ADD\s+OBJECT\s+(\w+)\s+AS\s+(\w+)(?:\s+WITH\s+(.+?))?$",
            re.IGNORECASE | re.MULTILINE,
        ),
        # Event handlers (obj.Click, obj.DblClick, obj.InteractiveChange, etc.)
        "event_handler": re.compile(
            r"^\s*PROCEDURE\s+(\w+)\.(Click|DblClick|RightClick|InteractiveChange|"
            r"GotFocus|LostFocus|Init|Destroy|Valid|When|Activate|Deactivate|"
            r"KeyPress|MouseDown|MouseUp|Resize|Timer)\b",
            re.IGNORECASE | re.MULTILINE,
        ),
        # DO FORM calls
        "do_form": re.compile(
            r"^\s*DO\s+FORM\s+(\S+?)(?:\s+WITH\s+(.+?))?(?:\s+TO\s+(\w+))?\s*$",
            re.IGNORECASE | re.MULTILINE,
        ),
        # MESSAGEBOX calls
        "messagebox": re.compile(
            r'MESSAGEBOX\s*\(\s*"([^"]*)"',
            re.IGNORECASE,
        ),
        # INPUTBOX / GETFILE / PUTFILE dialogs
        "input_dialog": re.compile(
            r"(INPUTBOX|GETFILE|PUTFILE|LOCFILE|GETDIR)\s*\(",
            re.IGNORECASE,
        ),
        # WAIT WINDOW messages
        "wait_window": re.compile(
            r'^\s*WAIT\s+WINDOW\s+"([^"]*)"',
            re.IGNORECASE | re.MULTILINE,
        ),
        # THISFORM property access (Caption, Enabled, Visible, Value)
        "thisform_prop": re.compile(
            r"THISFORM\.(\w+)\.(\w+)\s*=\s*(.+?)$",
            re.IGNORECASE | re.MULTILINE,
        ),
    }

    def extract_directory(self, directory):
        """Scan all source files and extract UI patterns."""
        ui_info = {
            "menus": {},         # menu_name -> {pads, bars with prompts and actions}
            "controls": [],      # ADD OBJECT controls with type and properties
            "event_handlers": [],  # obj.Event -> handler code snippet
            "form_calls": [],    # DO FORM references
            "dialogs": [],       # MESSAGEBOX, INPUTBOX, etc.
            "wait_messages": [], # WAIT WINDOW messages
            "summary_text": "",
        }

        for root, _dirs, files in os.walk(directory):
            for fname in sorted(files):
                ext = os.path.splitext(fname)[1].lower()
                if ext in (".prg", ".mpr", ".spr"):
                    fpath = os.path.join(root, fname)
                    try:
                        source = self.read_file(fpath)
                        self._extract_from_source(source, fname, ui_info)
                    except Exception as e:
                        logger.debug("UI extraction failed for %s: %s", fname, e)

        ui_info["summary_text"] = self._build_summary(ui_info)
        return ui_info

    def _extract_from_source(self, source, filename, ui_info):
        """Extract all UI patterns from a source string."""

        # ---- Menu items ----
        # DEFINE PAD
        for match in self.PATTERNS["define_pad"].finditer(source):
            pad_name = match.group(1)
            menu_name = match.group(2)
            prompt = match.group(3).replace("\\<", "")  # strip hotkey marker
            if menu_name not in ui_info["menus"]:
                ui_info["menus"][menu_name] = {"pads": [], "bars": [], "file": filename}
            ui_info["menus"][menu_name]["pads"].append({
                "name": pad_name, "prompt": prompt,
            })

        # DEFINE BAR
        for match in self.PATTERNS["define_bar"].finditer(source):
            bar_num = match.group(1)
            popup_name = match.group(2)
            prompt = match.group(3).replace("\\<", "").replace("\\-", "---")
            if prompt == "---":
                continue  # separator
            if popup_name not in ui_info["menus"]:
                ui_info["menus"][popup_name] = {"pads": [], "bars": [], "file": filename}
            ui_info["menus"][popup_name]["bars"].append({
                "bar": bar_num, "prompt": prompt, "action": "",
            })

        # ON SELECTION BAR -> map action to bar
        for match in self.PATTERNS["on_selection_bar"].finditer(source):
            bar_num = match.group(1)
            popup_name = match.group(2)
            action = match.group(3).strip()
            if popup_name in ui_info["menus"]:
                for bar in ui_info["menus"][popup_name]["bars"]:
                    if bar["bar"] == bar_num:
                        bar["action"] = action
                        break

        # ON PAD -> links pad to popup
        for match in self.PATTERNS["on_pad"].finditer(source):
            pad_name = match.group(1)
            menu_name = match.group(2)
            popup_name = match.group(3)
            if menu_name in ui_info["menus"]:
                for pad in ui_info["menus"][menu_name]["pads"]:
                    if pad["name"] == pad_name:
                        pad["submenu"] = popup_name
                        break

        # ---- Controls (ADD OBJECT) ----
        for match in self.PATTERNS["add_object"].finditer(source):
            obj_name = match.group(1)
            obj_class = match.group(2)
            with_clause = match.group(3) or ""
            # Extract Caption if present
            caption = ""
            cap_match = re.search(r'Caption\s*=\s*"([^"]*)"', with_clause, re.IGNORECASE)
            if cap_match:
                caption = cap_match.group(1)
            ui_info["controls"].append({
                "name": obj_name,
                "class": obj_class,
                "caption": caption,
                "properties": with_clause[:200],
                "file": filename,
            })

        # ---- Event handlers ----
        for match in self.PATTERNS["event_handler"].finditer(source):
            obj_name = match.group(1)
            event_name = match.group(2)
            # Extract the handler body (up to next ENDPROC or PROCEDURE)
            start_pos = match.end()
            handler_code = self._extract_handler_body(source, start_pos)
            ui_info["event_handlers"].append({
                "control": obj_name,
                "event": event_name,
                "code": handler_code[:500],
                "file": filename,
            })

        # ---- DO FORM calls ----
        for match in self.PATTERNS["do_form"].finditer(source):
            form_name = match.group(1).strip("()'\"")
            params = match.group(2) or ""
            result_var = match.group(3) or ""
            ui_info["form_calls"].append({
                "form": form_name,
                "params": params.strip(),
                "result": result_var,
                "file": filename,
            })

        # ---- MESSAGEBOX dialogs ----
        for match in self.PATTERNS["messagebox"].finditer(source):
            msg = match.group(1)
            if msg:
                ui_info["dialogs"].append({
                    "type": "MESSAGEBOX",
                    "message": msg[:200],
                    "file": filename,
                })

        # ---- WAIT WINDOW messages ----
        for match in self.PATTERNS["wait_window"].finditer(source):
            msg = match.group(1)
            if msg:
                ui_info["wait_messages"].append({
                    "message": msg[:200],
                    "file": filename,
                })

    def _extract_handler_body(self, source, start_pos):
        """Extract event handler code from start_pos until ENDPROC or next PROCEDURE."""
        end_patterns = [
            re.compile(r"^\s*ENDPROC", re.IGNORECASE | re.MULTILINE),
            re.compile(r"^\s*PROCEDURE\s+", re.IGNORECASE | re.MULTILINE),
            re.compile(r"^\s*FUNCTION\s+", re.IGNORECASE | re.MULTILINE),
            re.compile(r"^\s*ENDDEFINE", re.IGNORECASE | re.MULTILINE),
        ]
        remaining = source[start_pos:]
        min_end = len(remaining)
        for pat in end_patterns:
            m = pat.search(remaining)
            if m and m.start() < min_end:
                min_end = m.start()
        return remaining[:min_end].strip()

    def _build_summary(self, ui_info):
        """Build a human-readable summary of UI elements."""
        lines = []
        lines.append("# UI Controls & Navigation Summary")
        lines.append("")

        # Menus
        if ui_info["menus"]:
            lines.append("## Menu Structure")
            lines.append("")
            for menu_name, menu_data in sorted(ui_info["menus"].items()):
                lines.append(f"### Menu: {menu_name} (in {menu_data['file']})")
                lines.append("")
                if menu_data["pads"]:
                    lines.append("**Menu Bar Pads:**")
                    for pad in menu_data["pads"]:
                        submenu = f" -> {pad.get('submenu', '')}" if pad.get("submenu") else ""
                        lines.append(f"- {pad['prompt']}{submenu}")
                    lines.append("")
                if menu_data["bars"]:
                    lines.append("| Item | Action |")
                    lines.append("|------|--------|")
                    for bar in menu_data["bars"]:
                        action = bar["action"] if bar["action"] else "*(no action)*"
                        lines.append(f"| {bar['prompt']} | `{action}` |")
                    lines.append("")

        # Controls
        if ui_info["controls"]:
            lines.append("## Form Controls")
            lines.append("")
            lines.append("| Control | Type | Caption | File |")
            lines.append("|---------|------|---------|------|")
            for ctrl in ui_info["controls"]:
                caption = ctrl["caption"] if ctrl["caption"] else "-"
                lines.append(f"| {ctrl['name']} | {ctrl['class']} | {caption} | {ctrl['file']} |")
            lines.append("")

        # Event handlers
        if ui_info["event_handlers"]:
            lines.append("## Event Handlers (Button Clicks & Interactions)")
            lines.append("")
            for handler in ui_info["event_handlers"]:
                lines.append(f"### {handler['control']}.{handler['event']} (in {handler['file']})")
                lines.append("")
                if handler["code"]:
                    lines.append(f"```foxpro\n{handler['code']}\n```")
                else:
                    lines.append("*(empty handler)*")
                lines.append("")

        # Form calls
        if ui_info["form_calls"]:
            lines.append("## Form Navigation (DO FORM)")
            lines.append("")
            lines.append("| Form | Parameters | Called From |")
            lines.append("|------|-----------|-------------|")
            for fc in ui_info["form_calls"]:
                params = fc["params"] if fc["params"] else "-"
                lines.append(f"| {fc['form']} | {params} | {fc['file']} |")
            lines.append("")

        # Dialogs
        if ui_info["dialogs"]:
            lines.append("## User Dialogs")
            lines.append("")
            for dlg in ui_info["dialogs"]:
                lines.append(f"- **{dlg['type']}** in {dlg['file']}: \"{dlg['message']}\"")
            lines.append("")

        # Wait messages
        if ui_info["wait_messages"]:
            lines.append("## Status Messages (WAIT WINDOW)")
            lines.append("")
            for wm in ui_info["wait_messages"]:
                lines.append(f"- {wm['file']}: \"{wm['message']}\"")
            lines.append("")

        return "\n".join(lines)
