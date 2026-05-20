"""
Extractor for SQL statements, database schema, table references, and
data-access patterns from FoxPro source code.

Scans all source files and extracts:
- SQL SELECT/INSERT/UPDATE/DELETE statements
- USE <table> commands (table opens)
- CREATE TABLE / ALTER TABLE statements
- INDEX ON definitions
- REPLACE commands (field-level writes)
- SET RELATION commands (table relationships)
- Database (.dbc) references
"""
import logging
import os
import re

from extractors.base_extractor import BaseExtractor

logger = logging.getLogger(__name__)


class DbExtractor(BaseExtractor):
    """Extracts database schema and SQL patterns from FoxPro source files."""

    # Regex patterns for data-access constructs
    PATTERNS = {
        "sql_select": re.compile(
            r"^\s*SELECT\s+.+?\s+FROM\s+.+?(?:INTO\s+.+?)?$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "use_table": re.compile(
            r"^[^\S\n]*USE[^\S\n]+(.+?)(?:[^\S\n]+ALIAS[^\S\n]+(\w+))?(?:[^\S\n]+IN[^\S\n]+\d+)?(?:[^\S\n]+(?:SHARED|EXCLUSIVE|NOUPDATE))?[^\S\n]*$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "create_table": re.compile(
            r"^\s*CREATE\s+TABLE\s+(\S+)\s*\((.+?)\)",
            re.IGNORECASE | re.MULTILINE | re.DOTALL,
        ),
        "alter_table": re.compile(
            r"^\s*ALTER\s+TABLE\s+(\S+)\s+(.+?)$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "index_on": re.compile(
            r"^\s*INDEX\s+ON\s+(.+?)\s+TAG\s+(\w+)",
            re.IGNORECASE | re.MULTILINE,
        ),
        "replace_cmd": re.compile(
            r"^\s*REPLACE\s+(.+?)$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "open_database": re.compile(
            r"^\s*OPEN\s+DATABASE\s+(.+?)$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "set_relation": re.compile(
            r"^\s*SET\s+RELATION\s+TO\s+(.+?)\s+INTO\s+(\w+)",
            re.IGNORECASE | re.MULTILINE,
        ),
        "append_from": re.compile(
            r"^\s*APPEND\s+FROM\s+(\S+)",
            re.IGNORECASE | re.MULTILINE,
        ),
        "copy_to": re.compile(
            r"^\s*COPY\s+TO\s+(\S+)",
            re.IGNORECASE | re.MULTILINE,
        ),
        "locate_for": re.compile(
            r"^\s*LOCATE\s+FOR\s+(.+?)$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "scan_for": re.compile(
            r"^\s*SCAN\s+(?:FOR\s+(.+?))?$",
            re.IGNORECASE | re.MULTILINE,
        ),
        "count_sum": re.compile(
            r"^\s*(COUNT|SUM|AVERAGE|CALCULATE)\s+(.+?)$",
            re.IGNORECASE | re.MULTILINE,
        ),
    }

    def extract_directory(self, directory):
        """Scan all source files and extract database access patterns."""
        db_info = {
            "tables": {},        # table_name -> {aliases, fields, files_used_in}
            "databases": [],     # .dbc references
            "sql_statements": [],
            "indexes": [],
            "relations": [],
            "create_tables": [],
            "summary_text": "",
        }

        for root, _dirs, files in os.walk(directory):
            for fname in sorted(files):
                ext = os.path.splitext(fname)[1].lower()
                if ext in (".prg", ".mpr", ".spr"):
                    fpath = os.path.join(root, fname)
                    try:
                        source = self.read_file(fpath)
                        self._extract_from_source(source, fname, db_info)
                    except Exception as e:
                        logger.debug("DB extraction failed for %s: %s", fname, e)

        db_info["summary_text"] = self._build_summary(db_info)
        return db_info

    def _extract_from_source(self, source, filename, db_info):
        """Extract all data-access patterns from a source string."""

        # SQL SELECT statements
        for match in self.PATTERNS["sql_select"].finditer(source):
            stmt = match.group(0).strip()
            # Skip FoxPro's SELECT <workarea> which is just a number
            if re.match(r"^\s*SELECT\s+\d+\s*$", stmt, re.IGNORECASE):
                continue
            if re.match(r"^\s*SELECT\s+\w+\s*$", stmt, re.IGNORECASE) and "FROM" not in stmt.upper():
                continue
            db_info["sql_statements"].append({"sql": stmt, "file": filename})

        # USE <table> commands
        for match in self.PATTERNS["use_table"].finditer(source):
            table_ref = match.group(1).strip().strip("()'\"")
            alias = match.group(2) if match.group(2) else ""
            # Extract the .dbf filename from path expressions like: gcAppPath + "data\products.dbf"
            dbf_match = re.search(r"(\w+)\.dbf", table_ref, re.IGNORECASE)
            if dbf_match:
                table_name = dbf_match.group(1).lower()
            else:
                # Try extracting from the full USE line context
                line_text = match.group(0)
                dbf_in_line = re.search(r"(\w+)\.dbf", line_text, re.IGNORECASE)
                if dbf_in_line:
                    table_name = dbf_in_line.group(1).lower()
                else:
                    table_name = os.path.splitext(os.path.basename(table_ref))[0].lower()
            # Skip false positives (variables, keywords, USE IN <alias> close commands)
            skip_names = {"in", "", "use", "wait", "set", "do", "if", "and", "or", "not", "clear"}
            if table_name in skip_names or table_name.startswith("gc") or table_name.startswith("lc"):
                continue
            # Skip "USE IN <alias>" which closes a table, not opens one
            if re.match(r"^\s*USE\s+IN\s+", match.group(0), re.IGNORECASE):
                continue

            if table_name not in db_info["tables"]:
                db_info["tables"][table_name] = {
                    "full_path": table_ref,
                    "aliases": set(),
                    "files_used_in": set(),
                    "fields_written": set(),
                    "fields_read": set(),
                }
            info = db_info["tables"][table_name]
            info["files_used_in"].add(filename)
            if alias:
                info["aliases"].add(alias)

        # OPEN DATABASE
        for match in self.PATTERNS["open_database"].finditer(source):
            raw_ref = match.group(0)  # full matched line
            db_ref = match.group(1).strip().strip("()'\"")
            # Extract actual .dbc filename from path expressions like: gcAppPath + "data\inventory.dbc"
            dbc_match = re.search(r'[\\/]?(\w+)\.dbc', raw_ref, re.IGNORECASE)
            if dbc_match:
                db_ref = dbc_match.group(1) + ".dbc"
            elif db_ref.startswith("gc") or db_ref.startswith("lc"):
                # Variable reference - try to extract from string literal in the line
                str_match = re.search(r'"([^"]*\.dbc)"', raw_ref, re.IGNORECASE)
                if str_match:
                    db_ref = os.path.basename(str_match.group(1))
                else:
                    db_ref = f"{db_ref} (variable)"
            db_info["databases"].append({"database": db_ref, "file": filename})

        # REPLACE commands -> extract field names
        for match in self.PATTERNS["replace_cmd"].finditer(source):
            replace_text = match.group(1)
            field_pattern = re.compile(r"(\w+(?:\.\w+)?)\s+WITH\s+", re.IGNORECASE)
            for field_match in field_pattern.finditer(replace_text):
                field_ref = field_match.group(1)
                # Try to associate with a table
                self._track_field(field_ref, db_info, filename, is_write=True)

        # LOCATE FOR -> extract field references
        for match in self.PATTERNS["locate_for"].finditer(source):
            condition = match.group(1) if match.group(1) else ""
            self._extract_field_refs(condition, db_info, filename)

        # INDEX ON
        for match in self.PATTERNS["index_on"].finditer(source):
            db_info["indexes"].append({
                "expression": match.group(1).strip(),
                "tag": match.group(2),
                "file": filename,
            })

        # CREATE TABLE
        for match in self.PATTERNS["create_table"].finditer(source):
            db_info["create_tables"].append({
                "table": match.group(1).strip(),
                "definition": match.group(2).strip(),
                "file": filename,
            })

        # SET RELATION
        for match in self.PATTERNS["set_relation"].finditer(source):
            db_info["relations"].append({
                "expression": match.group(1).strip(),
                "target": match.group(2).strip(),
                "file": filename,
            })

    def _track_field(self, field_ref, db_info, filename, is_write=False):
        """Track a field reference, optionally with table prefix."""
        parts = field_ref.split(".")
        if len(parts) == 2:
            table = parts[0].lower()
            field = parts[1].lower()
            if table in db_info["tables"]:
                if is_write:
                    db_info["tables"][table]["fields_written"].add(field)
                else:
                    db_info["tables"][table]["fields_read"].add(field)

    def _extract_field_refs(self, expression, db_info, filename):
        """Extract table.field references from an expression."""
        pattern = re.compile(r"(\w+)\.(\w+)")
        for match in pattern.finditer(expression):
            table = match.group(1).lower()
            field = match.group(2).lower()
            if table in db_info["tables"]:
                db_info["tables"][table]["fields_read"].add(field)

    def _build_summary(self, db_info):
        """Build a human-readable summary of database usage."""
        lines = []
        lines.append("# Database Schema & Data Access Summary")
        lines.append("")

        # Databases
        if db_info["databases"]:
            lines.append("## Databases")
            for db in db_info["databases"]:
                lines.append(f"- `{db['database']}` (referenced in {db['file']})")
            lines.append("")

        # Tables
        if db_info["tables"]:
            lines.append("## Tables")
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

        # SQL Statements
        if db_info["sql_statements"]:
            lines.append("## SQL Statements")
            for i, stmt in enumerate(db_info["sql_statements"], 1):
                lines.append(f"\n### Query {i} (in {stmt['file']})")
                lines.append(f"```sql\n{stmt['sql']}\n```")
            lines.append("")

        # CREATE TABLE
        if db_info["create_tables"]:
            lines.append("## Table Definitions")
            for ct in db_info["create_tables"]:
                lines.append(f"\n### {ct['table']} (in {ct['file']})")
                lines.append(f"```sql\nCREATE TABLE {ct['table']} ({ct['definition']})\n```")
            lines.append("")

        # Indexes
        if db_info["indexes"]:
            lines.append("## Indexes")
            for idx in db_info["indexes"]:
                lines.append(f"- TAG `{idx['tag']}` ON `{idx['expression']}` (in {idx['file']})")
            lines.append("")

        # Relations
        if db_info["relations"]:
            lines.append("## Table Relations")
            for rel in db_info["relations"]:
                lines.append(f"- `{rel['expression']}` INTO `{rel['target']}` (in {rel['file']})")
            lines.append("")

        return "\n".join(lines)
