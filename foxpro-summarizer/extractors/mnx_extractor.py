"""
Extractor for FoxPro .mnx/.mnt Menu definition files.
Reads binary DBF/FPT and extracts menu structure with code snippets.
"""
import logging
import os

from extractors.base_extractor import BaseExtractor
from models.extraction import ExtractedMenu

logger = logging.getLogger(__name__)


class MnxExtractor(BaseExtractor):
    """Extracts menu definitions from .mnx/.mnt binary files."""

    def extract_directory(self, directory):
        """Scan directory recursively for .mnx files."""
        menus = []
        for root, _dirs, files in os.walk(directory):
            for fname in sorted(files):
                if fname.lower().endswith(".mnx"):
                    fpath = os.path.join(root, fname)
                    try:
                        menu = self.extract_mnx(fpath)
                        if menu:
                            menus.append(menu)
                            logger.info(
                                "Extracted MNX: %s (%d pads, %d items)",
                                fname, len(menu.pads), len(menu.items),
                            )
                    except Exception as e:
                        logger.error("Failed to extract MNX %s: %s", fpath, e)
        return menus

    def extract_mnx(self, mnx_path):
        """Extract menu definition from an .mnx file."""
        fname = os.path.basename(mnx_path)

        menu = ExtractedMenu(
            menu_name=os.path.splitext(fname)[0],
            source_file=fname,
        )

        # Try vfp2py's VCX converter (MNX has similar DBF structure)
        code = self._extract_via_vfp2py(mnx_path)
        if code:
            menu.full_code = code
            menu.converted_python = self.try_convert_to_python(code, fname)
            self._parse_menu_structure(code, menu)
            return menu

        # Fallback: read DBF directly
        self._extract_via_dbf(mnx_path, menu)

        if menu.full_code or menu.pads or menu.items:
            return menu
        return None

    def _extract_via_vfp2py(self, mnx_path):
        """Try using vfp2py's converter (works for some MNX files)."""
        try:
            from vfp2py.vfp2py import convert_vcx_to_vfp_code
            code_blocks = convert_vcx_to_vfp_code(mnx_path)
            if code_blocks:
                return "\n\n".join(code for _, code in code_blocks)
        except Exception as e:
            logger.debug("vfp2py MNX extraction failed for %s: %s", mnx_path, e)
        return None

    def _extract_via_dbf(self, mnx_path, menu):
        """Read MNX DBF table directly using the dbf library."""
        try:
            import dbf
            table = dbf.Table(mnx_path)
            table.open(dbf.READ_ONLY)

            all_code_parts = []

            for record in table:
                objtype = int(record.objtype) if hasattr(record, "objtype") else 0
                objcode = int(record.objcode) if hasattr(record, "objcode") else 0
                name = str(record.name).strip() if hasattr(record, "name") else ""
                prompt = str(record.prompt).strip() if hasattr(record, "prompt") else ""
                command = str(record.command).strip() if hasattr(record, "command") else ""
                procedure = str(record.procedure).strip() if hasattr(record, "procedure") else ""
                setup = str(record.setup).strip() if hasattr(record, "setup") else ""
                cleanup = str(record.cleanup).strip() if hasattr(record, "cleanup") else ""
                keyname = str(record.keyname).strip() if hasattr(record, "keyname") else ""
                skipfor = str(record.skipfor).strip() if hasattr(record, "skipfor") else ""
                message = str(record.message).strip() if hasattr(record, "message") else ""

                if objtype == 1:
                    # Menu system header
                    menu.setup_code = setup
                    menu.cleanup_code = cleanup
                    if setup:
                        all_code_parts.append(f"* === MENU SETUP ===\n{setup}")
                    if cleanup:
                        all_code_parts.append(f"* === MENU CLEANUP ===\n{cleanup}")

                elif objtype == 3:
                    # Menu pad (top-level bar item)
                    pad_info = {
                        "name": name,
                        "prompt": prompt,
                        "skip_for": skipfor,
                    }
                    menu.pads.append(pad_info)
                    all_code_parts.append(f"* PAD: {prompt}")

                elif objtype == 4:
                    # Menu item
                    item_info = {
                        "name": name,
                        "prompt": prompt,
                        "command": command,
                        "procedure": procedure,
                        "key": keyname,
                        "skip_for": skipfor,
                        "message": message,
                        "type": self._objcode_description(objcode),
                    }
                    menu.items.append(item_info)

                    if command:
                        all_code_parts.append(
                            f"* MENU ITEM: {prompt} [{keyname}]\n"
                            f"* Command: {command}"
                        )
                    if procedure:
                        all_code_parts.append(
                            f"* MENU ITEM: {prompt} [{keyname}]\n"
                            f"* Procedure:\n{procedure}"
                        )

            menu.full_code = "\n\n".join(all_code_parts)
            table.close()

        except Exception as e:
            logger.warning("DBF extraction failed for %s: %s", mnx_path, e)
            # Last fallback: try reading .mnt as raw text
            self._extract_from_mnt(mnx_path, menu)

    def _extract_from_mnt(self, mnx_path, menu):
        """Last resort: read .mnt memo file as raw text."""
        mnt_path = mnx_path.replace(".mnx", ".mnt").replace(".MNX", ".MNT")
        if not os.path.exists(mnt_path):
            return

        try:
            content = self.read_file(mnt_path)
            # Extract recognizable code blocks
            code_parts = []
            for block in content.split("\x00"):
                block = block.strip()
                if len(block) > 5 and self._looks_like_code(block):
                    code_parts.append(block)
            if code_parts:
                menu.full_code = "\n\n".join(code_parts)
        except Exception as e:
            logger.debug("MNT raw read failed for %s: %s", mnt_path, e)

    def _parse_menu_structure(self, code, menu):
        """Parse DEFINE PAD/BAR/POPUP commands from generated menu code."""
        import re

        # Extract DEFINE PAD
        for match in re.finditer(r"DEFINE\s+PAD\s+(\w+)\s+.*?PROMPT\s+\"([^\"]+)\"", code, re.IGNORECASE):
            menu.pads.append({"name": match.group(1), "prompt": match.group(2)})

        # Extract DEFINE BAR + ON SELECTION BAR
        bar_pattern = re.compile(
            r"DEFINE\s+BAR\s+(\d+)\s+OF\s+(\w+)\s+PROMPT\s+\"([^\"]+)\"",
            re.IGNORECASE,
        )
        sel_pattern = re.compile(
            r"ON\s+SELECTION\s+BAR\s+(\d+)\s+OF\s+(\w+)\s+(.*)",
            re.IGNORECASE,
        )
        bars = {}
        for match in bar_pattern.finditer(code):
            key = f"{match.group(2)}:{match.group(1)}"
            bars[key] = {"prompt": match.group(3), "popup": match.group(2)}

        for match in sel_pattern.finditer(code):
            key = f"{match.group(2)}:{match.group(1)}"
            if key in bars:
                bars[key]["command"] = match.group(3).strip()

        for bar in bars.values():
            menu.items.append(bar)

    def _objcode_description(self, objcode):
        """Return human-readable description of MNX OBJCODE."""
        descriptions = {
            0: "Command",
            67: "Submenu",
            78: "Procedure",
            80: "Pad",
        }
        return descriptions.get(objcode, f"Unknown({objcode})")

    def _looks_like_code(self, text):
        """Heuristic: does this text block look like FoxPro code?"""
        upper = text.upper()
        keywords = ["DO ", "SET ", "IF ", "SELECT", "THISFORM", "USE ", "PROCEDURE",
                     "PUBLIC", "LOCAL", "MESSAGEBOX", "DEFINE", "ON SELECTION"]
        return any(kw in upper for kw in keywords)
