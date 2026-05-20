"""
Report generator - produces structured markdown reports from analysis results.
Supports:
  - Business report (for stakeholders)
  - Technical report (for developers)
  - Both reports simultaneously
  - Batch and incremental (file-by-file) writing
"""
import logging
import os
from datetime import datetime

logger = logging.getLogger(__name__)


class ReportGenerator:
    """Generates business and/or technical markdown reports."""

    def __init__(self, report_type="both"):
        """
        Args:
            report_type: "both", "business", or "technical"
        """
        self.report_type = report_type
        self._biz_file = None
        self._tech_file = None
        self._biz_path = None
        self._tech_path = None
        self._biz_current_file = ""
        self._tech_current_file = ""

    @property
    def _write_business(self):
        return self.report_type in ("both", "business")

    @property
    def _write_technical(self):
        return self.report_type in ("both", "technical")

    # ----------------------------------------------------------------
    # Path helpers
    # ----------------------------------------------------------------
    def _resolve_paths(self, output_path, source_dir):
        """Derive business and technical file paths from the output path."""
        if output_path is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            base_dir = os.path.dirname(source_dir) if os.path.dirname(source_dir) else "."
            base_name = f"foxpro_analysis_{timestamp}"
        else:
            base_dir = os.path.dirname(output_path) or "."
            base_name = os.path.splitext(os.path.basename(output_path))[0]

        if self.report_type == "both":
            biz_path = os.path.join(base_dir, f"{base_name}_business.md")
            tech_path = os.path.join(base_dir, f"{base_name}_technical.md")
        elif self.report_type == "business":
            biz_path = os.path.join(base_dir, f"{base_name}_business.md")
            tech_path = None
        else:
            biz_path = None
            tech_path = os.path.join(base_dir, f"{base_name}_technical.md")

        return biz_path, tech_path

    # ----------------------------------------------------------------
    # Batch mode - generate full reports at once
    # ----------------------------------------------------------------
    def generate(self, results, extraction_stats, source_dir, output_path=None,
                 analyzer_label=None, db_info=None, ui_info=None):
        """
        Generate report(s) in batch mode.

        Returns:
            Tuple of (business_path, technical_path). Either may be None.
        """
        biz_path, tech_path = self._resolve_paths(output_path, source_dir)

        if self._write_business and biz_path:
            lines = []
            lines.extend(self._header(source_dir, extraction_stats, analyzer_label,
                                      title="FoxPro Application — Business Summary"))
            lines.extend(self._table_of_contents(results))
            lines.extend(self._business_analysis(results))
            lines.extend(self._build_db_overview(db_info) if db_info else [])
            lines.extend(self._build_ui_overview(ui_info) if ui_info else [])

            with open(biz_path, "w", encoding="utf-8") as f:
                f.write("\n".join(lines))
            logger.info("Business report written to %s", biz_path)

        if self._write_technical and tech_path:
            lines = []
            lines.extend(self._header(source_dir, extraction_stats, analyzer_label,
                                      title="FoxPro Application — Technical Report"))
            lines.extend(self._table_of_contents(results))
            lines.extend(self._technical_analysis(results))
            lines.extend(self._build_db_section(db_info) if db_info else [])
            lines.extend(self._build_ui_section(ui_info) if ui_info else [])
            lines.extend(self._appendix_python(results))

            with open(tech_path, "w", encoding="utf-8") as f:
                f.write("\n".join(lines))
            logger.info("Technical report written to %s", tech_path)

        return biz_path, tech_path

    # ----------------------------------------------------------------
    # Incremental mode - write as each unit completes
    # ----------------------------------------------------------------
    def start(self, extraction_stats, source_dir, total_units, output_path=None,
              analyzer_label=None):
        """
        Open report file(s) and write headers.
        Call this once before the LLM analysis loop.

        Returns:
            Tuple of (business_path, technical_path). Either may be None.
        """
        biz_path, tech_path = self._resolve_paths(output_path, source_dir)
        self._biz_path = biz_path
        self._tech_path = tech_path
        self._biz_current_file = ""
        self._tech_current_file = ""

        if self._write_business and biz_path:
            self._biz_file = open(biz_path, "w", encoding="utf-8")
            for line in self._header(source_dir, extraction_stats, analyzer_label,
                                     title="FoxPro Application — Business Summary"):
                self._biz_file.write(line + "\n")
            self._biz_file.write("## Business Analysis\n\n")
            self._biz_file.write(f"*Analyzing {total_units} code units — "
                                 f"report updates after each unit completes.*\n\n")
            self._biz_file.flush()
            logger.info("Business report started: %s", biz_path)

        if self._write_technical and tech_path:
            self._tech_file = open(tech_path, "w", encoding="utf-8")
            for line in self._header(source_dir, extraction_stats, analyzer_label,
                                     title="FoxPro Application — Technical Report"):
                self._tech_file.write(line + "\n")
            self._tech_file.write("## Technical Analysis\n\n")
            self._tech_file.write(f"*Analyzing {total_units} code units — "
                                  f"report updates after each unit completes.*\n\n")
            self._tech_file.flush()
            logger.info("Technical report started: %s", tech_path)

        return biz_path, tech_path

    def write_result(self, result):
        """
        Append a single analysis result to the open report file(s).
        Writes business_summary to business report, technical_summary to technical.
        """
        anchor = self._make_anchor(result.source_file, result.unit_name)
        icon = self._type_icon(result.unit_type)

        # ---- Business report ----
        if self._biz_file is not None:
            if result.source_file != self._biz_current_file:
                self._biz_current_file = result.source_file
                self._biz_file.write(f"### {self._biz_current_file}\n\n")

            self._biz_file.write(f'<a id="{anchor}"></a>\n')
            self._biz_file.write(f"#### {icon} {result.unit_type}: {result.unit_name}\n\n")

            biz_text = result.business_summary or result.summary
            self._biz_file.write(biz_text + "\n\n")
            self._biz_file.write("---\n\n")
            self._biz_file.flush()

        # ---- Technical report ----
        if self._tech_file is not None:
            if result.source_file != self._tech_current_file:
                self._tech_current_file = result.source_file
                self._tech_file.write(f"### {self._tech_current_file}\n\n")

            self._tech_file.write(f'<a id="{anchor}"></a>\n')
            self._tech_file.write(f"#### {icon} {result.unit_type}: {result.unit_name}\n\n")

            tech_text = result.technical_summary or result.summary
            self._tech_file.write(tech_text + "\n\n")

            # Collapsible source code (technical only)
            if result.original_code_snippet:
                self._tech_file.write("<details>\n")
                self._tech_file.write("<summary>View FoxPro Source (click to expand)</summary>\n\n")
                self._tech_file.write("```foxpro\n")
                self._tech_file.write(result.original_code_snippet[:2000] + "\n")
                self._tech_file.write("```\n")
                self._tech_file.write("</details>\n\n")

            self._tech_file.write("---\n\n")
            self._tech_file.flush()

    def finish(self, results=None, db_info=None, ui_info=None):
        """
        Write appendix sections and close the report file(s).
        """
        # ---- Finalize business report ----
        if self._biz_file is not None:
            if db_info:
                for line in self._build_db_overview(db_info):
                    self._biz_file.write(line + "\n")
            if ui_info:
                for line in self._build_ui_overview(ui_info):
                    self._biz_file.write(line + "\n")
            self._biz_file.close()
            self._biz_file = None
            logger.info("Business report finalized: %s", self._biz_path)

        # ---- Finalize technical report ----
        if self._tech_file is not None:
            if db_info:
                for line in self._build_db_section(db_info):
                    self._tech_file.write(line + "\n")
            if ui_info:
                for line in self._build_ui_section(ui_info):
                    self._tech_file.write(line + "\n")
            # Python equivalents
            if results:
                has_python = any(r.python_equivalent for r in results)
                if has_python:
                    self._tech_file.write("## Appendix: Python Equivalents\n\n")
                    self._tech_file.write(
                        "The following Python code was auto-generated by vfp2py. "
                        "It provides a reference for understanding the FoxPro logic.\n\n")
                    for r in results:
                        if r.python_equivalent:
                            self._tech_file.write(f"### {r.source_file} :: {r.unit_name}\n\n")
                            self._tech_file.write("```python\n")
                            self._tech_file.write(r.python_equivalent[:4000] + "\n")
                            self._tech_file.write("```\n\n")
            self._tech_file.close()
            self._tech_file = None
            logger.info("Technical report finalized: %s", self._tech_path)

    # ----------------------------------------------------------------
    # Analysis section builders (batch mode)
    # ----------------------------------------------------------------
    def _business_analysis(self, results):
        """Generate business analysis sections (business_summary field)."""
        lines = ["## Business Analysis", ""]
        current_file = ""
        for result in results:
            if result.source_file != current_file:
                current_file = result.source_file
                lines.extend([f"### {current_file}", ""])
            anchor = self._make_anchor(result.source_file, result.unit_name)
            icon = self._type_icon(result.unit_type)
            biz_text = result.business_summary or result.summary
            lines.extend([
                f'<a id="{anchor}"></a>',
                f"#### {icon} {result.unit_type}: {result.unit_name}",
                "",
                biz_text,
                "",
                "---", "",
            ])
        return lines

    def _technical_analysis(self, results):
        """Generate technical analysis sections (technical_summary field)."""
        lines = ["## Technical Analysis", ""]
        current_file = ""
        for result in results:
            if result.source_file != current_file:
                current_file = result.source_file
                lines.extend([f"### {current_file}", ""])
            anchor = self._make_anchor(result.source_file, result.unit_name)
            icon = self._type_icon(result.unit_type)
            tech_text = result.technical_summary or result.summary
            lines.extend([
                f'<a id="{anchor}"></a>',
                f"#### {icon} {result.unit_type}: {result.unit_name}",
                "",
                tech_text,
                "",
            ])
            if result.original_code_snippet:
                lines.extend([
                    "<details>",
                    "<summary>View FoxPro Source (click to expand)</summary>",
                    "",
                    "```foxpro",
                    result.original_code_snippet[:2000],
                    "```",
                    "</details>",
                    "",
                ])
            lines.extend(["---", ""])
        return lines

    # ----------------------------------------------------------------
    # Shared helpers
    # ----------------------------------------------------------------
    def _header(self, source_dir, stats, analyzer_label=None, title=None):
        """Generate report header with summary statistics."""
        analyzer = analyzer_label or "vfp2py + local LLM"
        report_title = title or "FoxPro Application Analysis Report"
        lines = [
            f"# {report_title}",
            "",
            f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            f"**Source Directory:** `{source_dir}`",
            f"**Analyzer:** {analyzer}",
            "",
            "---",
            "",
            "## Executive Summary",
            "",
            "| Category | Count |",
            "|----------|-------|",
        ]
        for category, count in stats.items():
            lines.append(f"| {category} | {count} |")
        total = sum(stats.values())
        lines.append(f"| **Total Analyzed** | **{total}** |")
        lines.extend(["", "---", ""])
        return lines

    def _table_of_contents(self, results):
        """Generate table of contents grouped by source file."""
        lines = ["## Table of Contents", ""]
        current_file = ""
        for result in results:
            if result.source_file != current_file:
                current_file = result.source_file
                lines.append(f"### {current_file}")
            anchor = self._make_anchor(result.source_file, result.unit_name)
            icon = self._type_icon(result.unit_type)
            lines.append(f"- {icon} [{result.unit_type}: {result.unit_name}](#{anchor})")
        lines.extend(["", "---", ""])
        return lines

    def _appendix_python(self, results):
        """Generate appendix with Python conversions (if available)."""
        has_python = any(r.python_equivalent for r in results)
        if not has_python:
            return []
        lines = [
            "## Appendix: Python Equivalents",
            "",
            "The following Python code was auto-generated by vfp2py. "
            "It provides a reference for understanding the FoxPro logic.",
            "",
        ]
        for result in results:
            if result.python_equivalent:
                lines.extend([
                    f"### {result.source_file} :: {result.unit_name}",
                    "",
                    "```python",
                    result.python_equivalent[:4000],
                    "```",
                    "",
                ])
        return lines

    # ----------------------------------------------------------------
    # DB section — full (technical) and overview (business)
    # ----------------------------------------------------------------
    def _build_db_section(self, db_info):
        """Full Database Schema & Data Access section (technical report)."""
        if not db_info or not any([
            db_info.get("tables"), db_info.get("databases"),
            db_info.get("sql_statements"), db_info.get("create_tables"),
            db_info.get("indexes"), db_info.get("relations"),
        ]):
            return []

        lines = ["## Database Schema & Data Access", ""]

        if db_info.get("databases"):
            lines.append("### Databases")
            lines.append("")
            for db in db_info["databases"]:
                lines.append(f"- `{db['database']}` (referenced in {db['file']})")
            lines.append("")

        if db_info.get("tables"):
            lines.append("### Tables")
            lines.append("")
            lines.append("| Table | Path | Aliases | Used In | Fields Written | Fields Read |")
            lines.append("|-------|------|---------|---------|----------------|-------------|")
            for name, info in sorted(db_info["tables"].items()):
                aliases = ", ".join(sorted(info["aliases"])) if info["aliases"] else "-"
                files = ", ".join(sorted(info["files_used_in"]))
                written = ", ".join(sorted(info["fields_written"])) if info["fields_written"] else "-"
                read = ", ".join(sorted(info["fields_read"])) if info["fields_read"] else "-"
                lines.append(f"| {name} | {info['full_path']} | {aliases} | {files} | {written} | {read} |")
            lines.append("")

        if db_info.get("create_tables"):
            lines.append("### Table Definitions (CREATE TABLE)")
            lines.append("")
            for ct in db_info["create_tables"]:
                lines.append(f"**{ct['table']}** (in {ct['file']})")
                lines.append(f"```sql\nCREATE TABLE {ct['table']} ({ct['definition']})\n```")
                lines.append("")

        if db_info.get("sql_statements"):
            lines.append("### SQL Statements")
            lines.append("")
            for i, stmt in enumerate(db_info["sql_statements"], 1):
                lines.append(f"**Query {i}** (in {stmt['file']})")
                lines.append(f"```sql\n{stmt['sql']}\n```")
                lines.append("")

        if db_info.get("indexes"):
            lines.append("### Indexes")
            lines.append("")
            for idx in db_info["indexes"]:
                lines.append(f"- TAG `{idx['tag']}` ON `{idx['expression']}` (in {idx['file']})")
            lines.append("")

        if db_info.get("relations"):
            lines.append("### Table Relations")
            lines.append("")
            for rel in db_info["relations"]:
                lines.append(f"- `{rel['expression']}` INTO `{rel['target']}` (in {rel['file']})")
            lines.append("")

        lines.extend(["---", ""])
        return lines

    def _build_db_overview(self, db_info):
        """Simplified data overview for business report (no SQL, no field details)."""
        if not db_info or not any([
            db_info.get("tables"), db_info.get("databases"),
        ]):
            return []

        lines = ["## Data Overview", ""]

        if db_info.get("databases"):
            lines.append("### Databases Used")
            lines.append("")
            for db in db_info["databases"]:
                lines.append(f"- {db['database']}")
            lines.append("")

        if db_info.get("tables"):
            lines.append("### Data Tables")
            lines.append("")
            lines.append("| Table | Used In |")
            lines.append("|-------|---------|")
            for name, info in sorted(db_info["tables"].items()):
                files = ", ".join(sorted(info["files_used_in"]))
                lines.append(f"| {name} | {files} |")
            lines.append("")

        if db_info.get("relations"):
            lines.append("### Data Relationships")
            lines.append("")
            for rel in db_info["relations"]:
                lines.append(f"- {rel['expression']} links to {rel['target']}")
            lines.append("")

        lines.extend(["---", ""])
        return lines

    # ----------------------------------------------------------------
    # UI section — full (technical) and overview (business)
    # ----------------------------------------------------------------
    def _build_ui_section(self, ui_info):
        """Full UI Controls & Navigation section (technical report)."""
        if not ui_info or not any([
            ui_info.get("menus"), ui_info.get("controls"),
            ui_info.get("event_handlers"), ui_info.get("form_calls"),
            ui_info.get("dialogs"), ui_info.get("wait_messages"),
        ]):
            return []

        lines = ["## UI Controls & Navigation", ""]

        if ui_info.get("menus"):
            lines.append("### Menu Structure")
            lines.append("")
            for menu_name, menu_data in sorted(ui_info["menus"].items()):
                lines.append(f"**{menu_name}** (in {menu_data['file']})")
                lines.append("")
                if menu_data["pads"]:
                    lines.append("Menu Bar Pads:")
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

        if ui_info.get("controls"):
            lines.append("### Form Controls")
            lines.append("")
            lines.append("| Control | Type | Caption | File |")
            lines.append("|---------|------|---------|------|")
            for ctrl in ui_info["controls"]:
                caption = ctrl["caption"] if ctrl["caption"] else "-"
                lines.append(f"| {ctrl['name']} | {ctrl['class']} | {caption} | {ctrl['file']} |")
            lines.append("")

        if ui_info.get("event_handlers"):
            lines.append("### Event Handlers (Button Clicks & Interactions)")
            lines.append("")
            for handler in ui_info["event_handlers"]:
                lines.append(f"**{handler['control']}.{handler['event']}** (in {handler['file']})")
                lines.append("")
                if handler["code"]:
                    lines.append(f"```foxpro\n{handler['code']}\n```")
                else:
                    lines.append("*(empty handler)*")
                lines.append("")

        if ui_info.get("form_calls"):
            lines.append("### Form Navigation (DO FORM)")
            lines.append("")
            lines.append("| Form | Parameters | Called From |")
            lines.append("|------|-----------|-------------|")
            for fc in ui_info["form_calls"]:
                params = fc["params"] if fc["params"] else "-"
                lines.append(f"| {fc['form']} | {params} | {fc['file']} |")
            lines.append("")

        if ui_info.get("dialogs"):
            lines.append("### User Dialogs")
            lines.append("")
            for dlg in ui_info["dialogs"]:
                lines.append(f"- **{dlg['type']}** in {dlg['file']}: \"{dlg['message']}\"")
            lines.append("")

        if ui_info.get("wait_messages"):
            lines.append("### Status Messages (WAIT WINDOW)")
            lines.append("")
            for wm in ui_info["wait_messages"]:
                lines.append(f"- {wm['file']}: \"{wm['message']}\"")
            lines.append("")

        lines.extend(["---", ""])
        return lines

    def _build_ui_overview(self, ui_info):
        """Simplified UI overview for business report (menus, forms, dialogs — no code)."""
        if not ui_info or not any([
            ui_info.get("menus"), ui_info.get("form_calls"),
            ui_info.get("dialogs"),
        ]):
            return []

        lines = ["## Application Navigation & User Interface", ""]

        if ui_info.get("menus"):
            lines.append("### Menu Structure")
            lines.append("")
            for menu_name, menu_data in sorted(ui_info["menus"].items()):
                if menu_data["pads"]:
                    lines.append(f"**{menu_name}**")
                    lines.append("")
                    lines.append("Menu bar:")
                    for pad in menu_data["pads"]:
                        lines.append(f"- {pad['prompt']}")
                    lines.append("")
                if menu_data["bars"]:
                    lines.append("| Menu Item | Action |")
                    lines.append("|-----------|--------|")
                    for bar in menu_data["bars"]:
                        action = bar["action"] if bar["action"] else "-"
                        lines.append(f"| {bar['prompt']} | {action} |")
                    lines.append("")

        if ui_info.get("form_calls"):
            lines.append("### Forms / Screens")
            lines.append("")
            lines.append("| Screen | Opened From |")
            lines.append("|--------|-------------|")
            for fc in ui_info["form_calls"]:
                lines.append(f"| {fc['form']} | {fc['file']} |")
            lines.append("")

        if ui_info.get("dialogs"):
            lines.append("### User Messages & Prompts")
            lines.append("")
            for dlg in ui_info["dialogs"]:
                lines.append(f"- \"{dlg['message']}\" ({dlg['file']})")
            lines.append("")

        if ui_info.get("wait_messages"):
            lines.append("### Status Messages")
            lines.append("")
            for wm in ui_info["wait_messages"]:
                lines.append(f"- \"{wm['message']}\"")
            lines.append("")

        lines.extend(["---", ""])
        return lines

    def _make_anchor(self, source_file, unit_name):
        """Create a markdown-safe anchor ID."""
        text = f"{source_file}-{unit_name}"
        return "".join(c if c.isalnum() else "-" for c in text.lower())

    def _type_icon(self, unit_type):
        """Return a text marker for the unit type."""
        icons = {
            "MODULE": "[PRG]",
            "PROCEDURE": "[PROC]",
            "FUNCTION": "[FUNC]",
            "CLASS": "[CLS]",
            "MENU": "[MNU]",
            "FORM": "[FRM]",
            "PROJECT": "[PJX]",
        }
        return icons.get(unit_type, f"[{unit_type}]")
