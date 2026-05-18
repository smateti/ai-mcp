"""
Extractor for FoxPro .vcx/.vct Visual Class Library files.
Uses vfp2py's convert_vcx_to_vfp_code() to read binary DBF/FPT structures.
"""
import logging
import os

from extractors.base_extractor import BaseExtractor
from models.extraction import ExtractedClass

logger = logging.getLogger(__name__)


class VcxExtractor(BaseExtractor):
    """Extracts class definitions from .vcx/.vct binary class library files."""

    def extract_directory(self, directory):
        """Scan directory recursively for .vcx files."""
        libraries = []
        for root, _dirs, files in os.walk(directory):
            for fname in sorted(files):
                if fname.lower().endswith(".vcx"):
                    fpath = os.path.join(root, fname)
                    try:
                        classes = self.extract_vcx(fpath)
                        libraries.extend(classes)
                        logger.info("Extracted VCX: %s (%d classes)", fname, len(classes))
                    except Exception as e:
                        logger.error("Failed to extract VCX %s: %s", fpath, e)
        return libraries

    def extract_vcx(self, vcx_path):
        """
        Extract all class definitions from a .vcx file.
        Uses vfp2py's convert_vcx_to_vfp_code() which reads the DBF/FPT binary
        and reconstructs the FoxPro DEFINE CLASS source code.
        """
        classes = []
        fname = os.path.basename(vcx_path)

        # Primary: use vfp2py's converter
        vfp_code_blocks = self._extract_via_vfp2py(vcx_path)

        if vfp_code_blocks:
            for block_name, block_code in vfp_code_blocks:
                # Parse class details from the generated code
                class_defs = self.split_classes(block_code)
                if class_defs:
                    for class_name, base_class, body in class_defs:
                        cls = ExtractedClass(
                            class_name=class_name,
                            base_class=base_class,
                            parent_class="",
                            library_file=fname,
                            methods_code=body,
                        )
                        cls.converted_python = self.try_convert_to_python(body, f"{fname}::{class_name}")
                        cls.properties = self._extract_properties(body)
                        cls.contained_objects = self._extract_objects(body)
                        classes.append(cls)
                else:
                    # The block itself is one logical unit
                    cls = ExtractedClass(
                        class_name=block_name,
                        base_class="unknown",
                        parent_class="",
                        library_file=fname,
                        methods_code=block_code,
                    )
                    cls.converted_python = self.try_convert_to_python(block_code, f"{fname}::{block_name}")
                    classes.append(cls)
        else:
            # Fallback: try reading the DBF directly with the dbf library
            classes = self._extract_via_dbf(vcx_path)

        return classes

    def _extract_via_vfp2py(self, vcx_path):
        """Use vfp2py's built-in VCX converter."""
        try:
            from vfp2py.vfp2py import convert_vcx_to_vfp_code
            code_blocks = convert_vcx_to_vfp_code(vcx_path)
            if code_blocks:
                return code_blocks  # list of (name, code) tuples
        except Exception as e:
            logger.warning("vfp2py VCX extraction failed for %s: %s", vcx_path, e)
        return None

    def _extract_via_dbf(self, vcx_path):
        """Fallback: read VCX DBF table directly using the dbf library."""
        classes = []
        fname = os.path.basename(vcx_path)

        try:
            import dbf
            table = dbf.Table(vcx_path)
            table.open(dbf.READ_ONLY)

            current_class = None
            for record in table:
                objname = str(record.objname).strip() if hasattr(record, "objname") else ""
                baseclass = str(record.baseclass).strip() if hasattr(record, "baseclass") else ""
                parent = str(record.parent).strip() if hasattr(record, "parent") else ""
                methods = str(record.methods).strip() if hasattr(record, "methods") else ""
                properties = str(record.properties).strip() if hasattr(record, "properties") else ""

                if objname and baseclass and not parent:
                    # This is a class header record
                    current_class = ExtractedClass(
                        class_name=objname,
                        base_class=baseclass,
                        parent_class=str(getattr(record, "class", "")).strip(),
                        library_file=fname,
                        methods_code=methods,
                        properties=properties,
                    )
                    classes.append(current_class)
                elif objname and parent and current_class:
                    # This is an object within the current class
                    current_class.contained_objects.append({
                        "name": objname,
                        "base_class": baseclass,
                        "parent": parent,
                        "methods": methods,
                        "properties": properties,
                    })
                    if methods:
                        current_class.methods_code += f"\n\n* --- {objname} methods ---\n{methods}"

            table.close()

        except Exception as e:
            logger.warning("DBF fallback failed for %s: %s", vcx_path, e)

        return classes

    def _extract_properties(self, class_body):
        """Extract property assignments from a DEFINE CLASS body."""
        import re
        props = []
        # Match lines like: property_name = value (before any PROCEDURE/FUNCTION)
        in_procs = False
        for line in class_body.split("\n"):
            stripped = line.strip()
            if stripped.upper().startswith("PROCEDURE") or stripped.upper().startswith("FUNCTION"):
                in_procs = True
            if not in_procs:
                match = re.match(r"^(\w+)\s*=\s*(.+)$", stripped)
                if match and not stripped.upper().startswith("DEFINE"):
                    props.append(f"{match.group(1)} = {match.group(2)}")
        return "\n".join(props)

    def _extract_objects(self, class_body):
        """Extract ADD OBJECT statements from a class body."""
        import re
        objects = []
        pattern = re.compile(
            r"ADD\s+OBJECT\s+(\w+)\s+AS\s+(\w+)",
            re.IGNORECASE,
        )
        for match in pattern.finditer(class_body):
            objects.append({"name": match.group(1), "class": match.group(2)})
        return objects
