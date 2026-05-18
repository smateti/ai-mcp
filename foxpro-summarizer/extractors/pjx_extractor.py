"""
Extractor for FoxPro .pjx/.pjt Project files.
Uses vfp2py's read_vfp_project() to extract project metadata.
"""
import logging
import os

from extractors.base_extractor import BaseExtractor
from models.extraction import ExtractedProject

logger = logging.getLogger(__name__)


class PjxExtractor(BaseExtractor):
    """Extracts project metadata from .pjx/.pjt binary files."""

    def extract_directory(self, directory):
        """Scan directory for .pjx project files."""
        projects = []
        for root, _dirs, files in os.walk(directory):
            for fname in sorted(files):
                if fname.lower().endswith(".pjx"):
                    fpath = os.path.join(root, fname)
                    try:
                        project = self.extract_pjx(fpath)
                        if project:
                            projects.append(project)
                            logger.info(
                                "Extracted PJX: %s (%d files)",
                                fname, len(project.files),
                            )
                    except Exception as e:
                        logger.error("Failed to extract PJX %s: %s", fpath, e)
        return projects

    def extract_pjx(self, pjx_path):
        """Extract project metadata from a .pjx file."""
        fname = os.path.basename(pjx_path)

        project = ExtractedProject(
            project_name=os.path.splitext(fname)[0],
            project_path=pjx_path,
        )

        # Primary: use vfp2py's project reader
        self._extract_via_vfp2py(pjx_path, project)

        # Fallback: direct DBF read
        if not project.files:
            self._extract_via_dbf(pjx_path, project)

        return project if project.files else None

    def _extract_via_vfp2py(self, pjx_path, project):
        """Use vfp2py's read_vfp_project()."""
        try:
            from vfp2py.vfp2py import read_vfp_project
            project_data = read_vfp_project(pjx_path)
            if project_data:
                if isinstance(project_data, dict):
                    project.main_program = project_data.get("main", "")
                    project.files = project_data.get("files", [])
                    project.description = project_data.get("description", "")
                elif isinstance(project_data, (list, tuple)):
                    project.files = list(project_data)
        except Exception as e:
            logger.debug("vfp2py PJX extraction failed for %s: %s", pjx_path, e)

    def _extract_via_dbf(self, pjx_path, project):
        """Fallback: read PJX DBF table directly."""
        try:
            import dbf
            table = dbf.Table(pjx_path)
            table.open(dbf.READ_ONLY)

            for record in table:
                name = str(record.name).strip() if hasattr(record, "name") else ""
                rec_type = str(record.type).strip() if hasattr(record, "type") else ""
                mainprog = bool(record.mainprog) if hasattr(record, "mainprog") else False

                if name:
                    file_info = {
                        "name": name,
                        "type": self._file_type_description(rec_type),
                        "is_main": mainprog,
                    }
                    project.files.append(file_info)
                    if mainprog:
                        project.main_program = name

            table.close()

        except Exception as e:
            logger.warning("DBF fallback failed for %s: %s", pjx_path, e)

    def _file_type_description(self, type_code):
        """Map PJX type codes to descriptions."""
        type_map = {
            "H": "Header",
            "P": "Program",
            "K": "Form",
            "V": "Class Library",
            "M": "Menu",
            "R": "Report",
            "L": "Label",
            "D": "Database",
            "d": "Table (free)",
            "T": "Text",
            "x": "Other",
        }
        return type_map.get(type_code, f"Unknown({type_code})")
