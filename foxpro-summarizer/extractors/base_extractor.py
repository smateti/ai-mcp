"""
Base extractor with shared utilities for all FoxPro file types.
"""
import logging
import re
import sys
import os

# Add vfp2py to the Python path
VFP2PY_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "..", "vfp2py-master")
if VFP2PY_PATH not in sys.path:
    sys.path.insert(0, VFP2PY_PATH)

from config import VFP_ENCODING, MAX_CODE_LENGTH

logger = logging.getLogger(__name__)


class BaseExtractor:
    """Base class with shared parsing utilities."""

    def __init__(self, encoding=None):
        self.encoding = encoding or VFP_ENCODING

    def read_file(self, file_path):
        """Read a text file with FoxPro encoding."""
        encodings = [self.encoding, "utf-8", "latin-1"]
        for enc in encodings:
            try:
                with open(file_path, "r", encoding=enc) as f:
                    return f.read()
            except (UnicodeDecodeError, UnicodeError):
                continue
        # Last resort: binary read with error replacement
        with open(file_path, "r", encoding="latin-1", errors="replace") as f:
            return f.read()

    def extract_header_comment(self, source):
        """Extract the leading comment block from FoxPro source."""
        lines = source.split("\n")
        comment_lines = []
        for line in lines:
            stripped = line.strip()
            if stripped.startswith("*") or stripped.startswith("&&") or stripped == "":
                comment_lines.append(stripped)
            else:
                break
        return "\n".join(comment_lines).strip()

    def extract_preprocessor_defines(self, source):
        """Extract #DEFINE directives from source."""
        defines = []
        for line in source.split("\n"):
            stripped = line.strip()
            if stripped.upper().startswith("#DEFINE"):
                defines.append(stripped)
        return defines

    def split_procedures(self, source):
        """Split source into procedure/function blocks."""
        pattern = re.compile(
            r"^\s*(PROCEDURE|FUNCTION)\s+(\w+)",
            re.IGNORECASE | re.MULTILINE,
        )
        matches = list(pattern.finditer(source))
        blocks = []

        if not matches:
            return [("__main__", "MODULE", source)]

        # Main code before first procedure
        if matches[0].start() > 0:
            main_code = source[: matches[0].start()].strip()
            if main_code:
                blocks.append(("__main__", "MODULE", main_code))

        # Each procedure/function block
        for i, match in enumerate(matches):
            end = matches[i + 1].start() if i + 1 < len(matches) else len(source)
            body = source[match.start() : end].strip()
            blocks.append((match.group(2), match.group(1).upper(), body))

        return blocks

    def split_classes(self, source):
        """Split source into DEFINE CLASS blocks."""
        class_pattern = re.compile(
            r"^\s*DEFINE\s+CLASS\s+(\w+)\s+AS\s+(\w+)",
            re.IGNORECASE | re.MULTILINE,
        )
        enddefine_pattern = re.compile(
            r"^\s*ENDDEFINE",
            re.IGNORECASE | re.MULTILINE,
        )
        classes = []
        for match in class_pattern.finditer(source):
            class_name = match.group(1)
            base_class = match.group(2)
            end_match = enddefine_pattern.search(source, match.end())
            if end_match:
                body = source[match.start() : end_match.end()].strip()
            else:
                body = source[match.start() :].strip()
            classes.append((class_name, base_class, body))
        return classes

    def truncate_code(self, code, max_length=None):
        """Truncate code if it exceeds max length for LLM."""
        max_len = max_length or MAX_CODE_LENGTH
        if len(code) > max_len:
            return code[:max_len] + "\n\n... [truncated, total {} chars]".format(len(code))
        return code

    def try_convert_to_python(self, source, filename=""):
        """Attempt to convert FoxPro source to Python using vfp2py."""
        try:
            from vfp2py import vfp2py
            python_code = vfp2py.prg2py(source, self.encoding, input_filename=filename)
            return python_code
        except Exception as e:
            logger.debug("Python conversion failed for %s: %s", filename, e)
            return None
