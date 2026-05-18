"""
Extractor for FoxPro .scx/.sct Screen/Form files.
Uses vfp2py's convert_scx_to_vfp_code() to read binary DBF/FPT structures.
"""
import logging
import os

from extractors.base_extractor import BaseExtractor
from models.extraction import ExtractedForm

logger = logging.getLogger(__name__)


class ScxExtractor(BaseExtractor):
    """Extracts form/screen definitions from .scx/.sct binary files."""

    def extract_directory(self, directory):
        """Scan directory recursively for .scx files."""
        forms = []
        for root, _dirs, files in os.walk(directory):
            for fname in sorted(files):
                if fname.lower().endswith(".scx"):
                    fpath = os.path.join(root, fname)
                    try:
                        form = self.extract_scx(fpath)
                        if form:
                            forms.append(form)
                            logger.info(
                                "Extracted SCX: %s (%d controls)",
                                fname, len(form.controls),
                            )
                    except Exception as e:
                        logger.error("Failed to extract SCX %s: %s", fpath, e)
        return forms

    def extract_scx(self, scx_path):
        """Extract form definition from an .scx file."""
        fname = os.path.basename(scx_path)

        form = ExtractedForm(
            form_name=os.path.splitext(fname)[0],
            source_file=fname,
        )

        # Primary: use vfp2py's converter
        code = self._extract_via_vfp2py(scx_path)
        if code:
            form.full_code = code
            form.converted_python = self.try_convert_to_python(code, fname)
            form.controls = self._extract_controls_from_code(code)
            form.methods_code = self._extract_methods_from_code(code)
            form.properties = self._extract_form_properties(code)
            return form

        # Fallback: read DBF directly
        return self._extract_via_dbf(scx_path, form)

    def _extract_via_vfp2py(self, scx_path):
        """Use vfp2py's SCX converter."""
        try:
            from vfp2py.vfp2py import convert_scx_to_vfp_code
            code_blocks = convert_scx_to_vfp_code(scx_path)
            if code_blocks:
                # code_blocks is a list of (name, code) tuples
                all_code = "\n\n".join(code for _, code in code_blocks)
                return all_code
        except Exception as e:
            logger.warning("vfp2py SCX extraction failed for %s: %s", scx_path, e)
        return None

    def _extract_via_dbf(self, scx_path, form):
        """Fallback: read SCX DBF table directly."""
        try:
            import dbf
            table = dbf.Table(scx_path)
            table.open(dbf.READ_ONLY)

            all_methods = []
            for record in table:
                objname = str(record.objname).strip() if hasattr(record, "objname") else ""
                baseclass = str(record.baseclass).strip() if hasattr(record, "baseclass") else ""
                parent = str(record.parent).strip() if hasattr(record, "parent") else ""
                methods = str(record.methods).strip() if hasattr(record, "methods") else ""
                properties = str(record.properties).strip() if hasattr(record, "properties") else ""

                if objname:
                    form.controls.append({
                        "name": objname,
                        "base_class": baseclass,
                        "parent": parent,
                        "has_methods": bool(methods),
                        "has_properties": bool(properties),
                    })
                    if methods:
                        all_methods.append(f"* === {objname} ({baseclass}) ===\n{methods}")
                    if not parent and properties:
                        form.properties = properties

            form.methods_code = "\n\n".join(all_methods)
            form.full_code = form.methods_code
            table.close()

        except Exception as e:
            logger.warning("DBF fallback failed for %s: %s", scx_path, e)

        return form if (form.full_code or form.controls) else None

    def _extract_controls_from_code(self, code):
        """Extract ADD OBJECT and control definitions from generated code."""
        import re
        controls = []

        # ADD OBJECT pattern
        pattern = re.compile(
            r"ADD\s+OBJECT\s+(\w+)\s+AS\s+(\w+)",
            re.IGNORECASE,
        )
        for match in pattern.finditer(code):
            controls.append({"name": match.group(1), "base_class": match.group(2)})

        # DEFINE CLASS pattern (for dataenvironment, cursors, etc.)
        class_pattern = re.compile(
            r"DEFINE\s+CLASS\s+(\w+)\s+AS\s+(\w+)",
            re.IGNORECASE,
        )
        for match in class_pattern.finditer(code):
            if match.group(1).lower() not in [c["name"].lower() for c in controls]:
                controls.append({"name": match.group(1), "base_class": match.group(2)})

        return controls

    def _extract_methods_from_code(self, code):
        """Extract PROCEDURE/FUNCTION blocks from generated code."""
        import re
        methods = []
        pattern = re.compile(
            r"^\s*(PROCEDURE|FUNCTION)\s+(\w+).*?(?=^\s*(?:PROCEDURE|FUNCTION|ENDDEFINE|DEFINE\s+CLASS)|\Z)",
            re.IGNORECASE | re.MULTILINE | re.DOTALL,
        )
        for match in pattern.finditer(code):
            methods.append(f"{match.group(1)} {match.group(2)}")
        return ", ".join(methods)

    def _extract_form_properties(self, code):
        """Extract property assignments from the main form class."""
        import re
        props = []
        in_class = False
        in_method = False
        for line in code.split("\n"):
            stripped = line.strip()
            if stripped.upper().startswith("DEFINE CLASS"):
                in_class = True
                continue
            if stripped.upper().startswith("ENDDEFINE"):
                in_class = False
                continue
            if stripped.upper().startswith(("PROCEDURE", "FUNCTION")):
                in_method = True
                continue
            if stripped.upper().startswith(("ENDPROC", "ENDFUNC")):
                in_method = False
                continue

            if in_class and not in_method:
                match = re.match(r"^(\w+)\s*=\s*(.+)$", stripped)
                if match:
                    props.append(f"{match.group(1)} = {match.group(2)}")
        return "\n".join(props[:50])  # Limit to first 50 properties
