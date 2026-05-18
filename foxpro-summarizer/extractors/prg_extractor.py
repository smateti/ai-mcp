"""
Extractor for FoxPro .prg, .mpr, .spr text-based source files.
Uses vfp2py's ANTLR parser for deep AST analysis and Python conversion.
"""
import logging
import os

from extractors.base_extractor import BaseExtractor
from models.extraction import ExtractedModule, ExtractedProcedure, ExtractedClass

logger = logging.getLogger(__name__)


class PrgExtractor(BaseExtractor):
    """Extracts detailed info from .prg/.mpr/.spr FoxPro source files."""

    def extract_directory(self, directory):
        """Scan directory recursively for .prg/.mpr/.spr files and extract all."""
        modules = []
        for root, _dirs, files in os.walk(directory):
            for fname in sorted(files):
                ext = os.path.splitext(fname)[1].lower()
                if ext in (".prg", ".mpr", ".spr"):
                    fpath = os.path.join(root, fname)
                    try:
                        module = self.extract_file(fpath)
                        modules.append(module)
                        logger.info(
                            "Extracted PRG: %s (%d procs, %d classes, %d lines)",
                            fname,
                            len(module.procedures),
                            len(module.classes),
                            module.line_count,
                        )
                    except Exception as e:
                        logger.error("Failed to extract %s: %s", fpath, e)
        return modules

    def extract_file(self, file_path):
        """Extract detailed information from a single .prg file."""
        source = self.read_file(file_path)
        fname = os.path.basename(file_path)
        ext = os.path.splitext(fname)[1].lower()

        file_type_map = {".prg": "program", ".mpr": "menu_program", ".spr": "screen_program"}
        file_type = file_type_map.get(ext, "program")

        module = ExtractedModule(
            file_name=fname,
            file_path=file_path,
            file_type=file_type,
            raw_source=source,
            header_comment=self.extract_header_comment(source),
            line_count=source.count("\n") + 1,
            preprocessor_defines=self.extract_preprocessor_defines(source),
        )

        # Attempt Python conversion of the full file via vfp2py
        module.converted_python = self.try_convert_to_python(source, fname)

        # Extract DEFINE CLASS blocks
        class_blocks = self.split_classes(source)
        for class_name, base_class, body in class_blocks:
            cls = ExtractedClass(
                class_name=class_name,
                base_class=base_class,
                parent_class="",
                library_file=fname,
                methods_code=body,
            )
            cls.converted_python = self.try_convert_to_python(body, f"{fname}::{class_name}")
            module.classes.append(cls)

        # Extract PROCEDURE/FUNCTION blocks (outside class definitions)
        source_without_classes = self._remove_class_blocks(source)
        proc_blocks = self.split_procedures(source_without_classes)
        for proc_name, proc_type, body in proc_blocks:
            proc = ExtractedProcedure(
                name=proc_name,
                proc_type=proc_type,
                source_code=body,
                line_count=body.count("\n") + 1,
                comment=self._extract_proc_comment(body),
                parameters=self._extract_parameters(body),
            )
            proc.converted_python = self.try_convert_to_python(body, f"{fname}::{proc_name}")
            module.procedures.append(proc)

        # Try ANTLR-based deep extraction for additional details
        self._antlr_extract(module, source)

        return module

    def _remove_class_blocks(self, source):
        """Remove DEFINE CLASS...ENDDEFINE blocks from source so we don't double-extract procedures."""
        import re
        # Non-greedy match of class blocks
        pattern = re.compile(
            r"^\s*DEFINE\s+CLASS\s+.*?^\s*ENDDEFINE\s*$",
            re.IGNORECASE | re.MULTILINE | re.DOTALL,
        )
        return pattern.sub("", source)

    def _extract_proc_comment(self, body):
        """Extract the comment block right after a PROCEDURE/FUNCTION declaration."""
        lines = body.split("\n")
        comments = []
        past_decl = False
        for line in lines:
            stripped = line.strip()
            if not past_decl:
                upper = stripped.upper()
                if upper.startswith("PROCEDURE") or upper.startswith("FUNCTION"):
                    past_decl = True
                continue
            if stripped.startswith("*") or stripped.startswith("&&"):
                comments.append(stripped)
            elif stripped:
                break
        return "\n".join(comments) if comments else None

    def _extract_parameters(self, body):
        """Extract LPARAMETERS/PARAMETERS line from a procedure body."""
        import re
        match = re.search(
            r"^\s*(L?PARAMETERS)\s+(.+)$",
            body,
            re.IGNORECASE | re.MULTILINE,
        )
        return match.group(2).strip() if match else None

    def _antlr_extract(self, module, source):
        """Use vfp2py's ANTLR parser to extract additional structural info."""
        try:
            from vfp2py.vfp2py import preprocess_code, run_parser
            from antlr4 import CommonTokenStream, InputStream
            from vfp2py.VisualFoxpro9Lexer import VisualFoxpro9Lexer
            from vfp2py.VisualFoxpro9Parser import VisualFoxpro9Parser

            processed = preprocess_code(source, self.encoding)
            input_stream = InputStream(processed)
            lexer = VisualFoxpro9Lexer(input_stream)
            stream = CommonTokenStream(lexer)
            parser = VisualFoxpro9Parser(stream)

            # Suppress parser error output during extraction
            parser.removeErrorListeners()

            tree = parser.prg()

            # Walk the tree to count constructs
            self._count_constructs(tree, module, parser)

        except Exception as e:
            logger.debug("ANTLR extraction skipped for %s: %s", module.file_name, e)

    def _count_constructs(self, tree, module, parser):
        """Walk ANTLR parse tree to extract structural metadata."""
        try:
            from antlr4 import ParserRuleContext

            def walk(node, depth=0):
                if isinstance(node, ParserRuleContext):
                    rule_name = parser.ruleNames[node.getRuleIndex()]
                    # Track SQL queries, SET commands, etc. as metadata
                    if rule_name in ("selectCmd", "sqlSelect"):
                        if not hasattr(module, "sql_queries"):
                            module.sql_queries = []
                        module.sql_queries = getattr(module, "sql_queries", [])
                        module.sql_queries.append(node.getText()[:200])
                    for child in (node.children or []):
                        walk(child, depth + 1)

            walk(tree)
        except Exception:
            pass
