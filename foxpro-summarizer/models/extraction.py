"""
Data models for extracted FoxPro source information.
"""
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class ExtractedProcedure:
    """A procedure or function extracted from a FoxPro source file."""
    name: str
    proc_type: str  # "PROCEDURE", "FUNCTION", "CLASS", "METHOD"
    source_code: str
    converted_python: Optional[str] = None
    comment: Optional[str] = None
    parameters: Optional[str] = None
    line_count: int = 0


@dataclass
class ExtractedClass:
    """A class definition extracted from a VCX or PRG file."""
    class_name: str
    base_class: str
    parent_class: str
    library_file: str
    properties: str = ""
    methods_code: str = ""
    contained_objects: list = field(default_factory=list)
    converted_python: Optional[str] = None
    description: Optional[str] = None


@dataclass
class ExtractedMenu:
    """A menu definition extracted from an MNX file."""
    menu_name: str
    source_file: str
    pads: list = field(default_factory=list)  # top-level menu bar items
    items: list = field(default_factory=list)  # individual menu items
    setup_code: str = ""
    cleanup_code: str = ""
    full_code: str = ""
    converted_python: Optional[str] = None


@dataclass
class ExtractedForm:
    """A screen/form extracted from an SCX file."""
    form_name: str
    source_file: str
    controls: list = field(default_factory=list)
    methods_code: str = ""
    properties: str = ""
    data_environment: str = ""
    full_code: str = ""
    converted_python: Optional[str] = None


@dataclass
class ExtractedModule:
    """A complete parsed module (.prg, .mpr, .spr file)."""
    file_name: str
    file_path: str
    file_type: str  # "program", "menu_program", "screen_program"
    raw_source: str = ""
    converted_python: Optional[str] = None
    header_comment: str = ""
    procedures: list = field(default_factory=list)
    classes: list = field(default_factory=list)
    line_count: int = 0
    preprocessor_defines: list = field(default_factory=list)


@dataclass
class ExtractedProject:
    """A FoxPro project extracted from a PJX file."""
    project_name: str
    project_path: str
    main_program: str = ""
    files: list = field(default_factory=list)
    description: str = ""


@dataclass
class AnalysisResult:
    """The LLM-generated summary for one code unit."""
    source_file: str
    unit_name: str
    unit_type: str  # MODULE, PROCEDURE, CLASS, MENU, FORM
    summary: str
    original_code_snippet: str = ""
    python_equivalent: str = ""
