#!/usr/bin/env python -u
"""
FoxPro Application Summarizer
==============================
Uses vfp2py to deeply parse FoxPro source code (.prg, .vcx, .scx, .mnx, .pjx)
and sends extracted logic to Llama 3.1 (via Ollama) for human-readable summaries.

Usage:
    python main.py <foxpro-source-dir> [--url URL] [--model MODEL] [--output FILE]

Examples:
    python main.py ../sample-foxpro-app
    python main.py /path/to/real/foxpro/app --model llama3.1:70b
    python main.py ./myapp --url http://localhost:11434 --output report.md
"""
import argparse
import logging
import os
import sys
import time

# Ensure project root and vfp2py are on the path
PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
VFP2PY_PATH = os.path.join(os.path.dirname(PROJECT_ROOT), "vfp2py-master")
sys.path.insert(0, PROJECT_ROOT)
if os.path.isdir(VFP2PY_PATH):
    sys.path.insert(0, VFP2PY_PATH)

from config import OLLAMA_BASE_URL, OLLAMA_MODEL
from extractors.prg_extractor import PrgExtractor
from extractors.vcx_extractor import VcxExtractor
from extractors.scx_extractor import ScxExtractor
from extractors.mnx_extractor import MnxExtractor
from extractors.pjx_extractor import PjxExtractor
from extractors.db_extractor import DbExtractor
from llm.ollama_client import OllamaClient
from models.extraction import AnalysisResult
from report.generator import ReportGenerator

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("foxpro-summarizer")


def parse_args():
    parser = argparse.ArgumentParser(
        description="FoxPro Application Summarizer using vfp2py + Llama 3.1"
    )
    parser.add_argument("source_dir", help="Path to FoxPro source directory")
    parser.add_argument("--url", default=OLLAMA_BASE_URL, help="Ollama API URL")
    parser.add_argument("--model", default=OLLAMA_MODEL, help="LLM model name")
    parser.add_argument("--output", default=None, help="Output report file path")
    parser.add_argument("--skip-llm", action="store_true", help="Skip LLM analysis (extraction only)")
    parser.add_argument("--verbose", action="store_true", help="Enable debug logging")
    return parser.parse_args()


def main():
    args = parse_args()

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    source_dir = os.path.abspath(args.source_dir)
    if not os.path.isdir(source_dir):
        print(f"ERROR: Directory not found: {source_dir}")
        sys.exit(1)

    print("=" * 60)
    print("  FoxPro Application Summarizer")
    print("  vfp2py + Llama 3.1 (Ollama)")
    print("=" * 60)
    print(f"  Source:  {source_dir}")
    print(f"  Ollama:  {args.url}")
    print(f"  Model:   {args.model}")
    print()

    # Initialize
    ollama = OllamaClient(args.url, args.model)
    prg_ext = PrgExtractor()
    vcx_ext = VcxExtractor()
    scx_ext = ScxExtractor()
    mnx_ext = MnxExtractor()
    pjx_ext = PjxExtractor()
    db_ext = DbExtractor()
    report_gen = ReportGenerator()

    # ---- Step 1: Test Ollama connection ----
    if not args.skip_llm:
        print("[1/8] Testing Ollama connection...")
        if not ollama.test_connection():
            print(f"  ERROR: Cannot reach Ollama or model '{args.model}' not found.")
            print(f"  Make sure: ollama serve && ollama pull {args.model}")
            sys.exit(1)
        print(f"  -> Connected to {args.url} with model {args.model}")
        print()
    else:
        print("[1/8] Skipping LLM connection (--skip-llm mode)")
        print()

    # ---- Step 2: Extract .prg files ----
    print("[2/8] Extracting .prg / .mpr / .spr program files...")
    modules = prg_ext.extract_directory(source_dir)
    total_procs = sum(len(m.procedures) for m in modules)
    total_classes_in_prg = sum(len(m.classes) for m in modules)
    print(f"  -> {len(modules)} files, {total_procs} procedures, {total_classes_in_prg} classes")
    for m in modules:
        py_status = "+ Python" if m.converted_python else ""
        print(f"     {m.file_name}: {m.line_count} lines, {len(m.procedures)} procs {py_status}")
    print()

    # ---- Step 3: Extract .vcx/.vct class libraries ----
    print("[3/8] Extracting .vcx/.vct Visual Class Libraries...")
    vcx_classes = vcx_ext.extract_directory(source_dir)
    print(f"  -> {len(vcx_classes)} classes extracted")
    for cls in vcx_classes:
        print(f"     {cls.library_file}::{cls.class_name} (base: {cls.base_class})")
    print()

    # ---- Step 4: Extract .scx/.sct forms ----
    print("[4/8] Extracting .scx/.sct Screen/Form files...")
    forms = scx_ext.extract_directory(source_dir)
    print(f"  -> {len(forms)} forms extracted")
    for form in forms:
        print(f"     {form.source_file}: {len(form.controls)} controls")
    print()

    # ---- Step 5: Extract .mnx/.mnt menus ----
    print("[5/8] Extracting .mnx/.mnt Menu definitions...")
    menus = mnx_ext.extract_directory(source_dir)
    print(f"  -> {len(menus)} menus extracted")
    for menu in menus:
        print(f"     {menu.source_file}: {len(menu.pads)} pads, {len(menu.items)} items")
    print()

    # ---- Step 6: Extract .pjx projects ----
    print("[6/8] Extracting .pjx Project files...")
    projects = pjx_ext.extract_directory(source_dir)
    print(f"  -> {len(projects)} projects extracted")
    for proj in projects:
        print(f"     {proj.project_name}: {len(proj.files)} files, main={proj.main_program}")
    print()

    # ---- Step 7: Extract SQL & DB schema ----
    print("[7/8] Extracting SQL statements, table references & DB schema...")
    db_info = db_ext.extract_directory(source_dir)
    print(f"  -> {len(db_info['tables'])} tables referenced")
    print(f"  -> {len(db_info['sql_statements'])} SQL statements found")
    print(f"  -> {len(db_info['databases'])} database references")
    print(f"  -> {len(db_info['indexes'])} index definitions")
    print(f"  -> {len(db_info['relations'])} table relations")
    for tname, tinfo in sorted(db_info["tables"].items()):
        fields_w = len(tinfo["fields_written"])
        fields_r = len(tinfo["fields_read"])
        files = ", ".join(sorted(tinfo["files_used_in"]))
        print(f"     {tname}: {fields_w} fields written, {fields_r} fields read ({files})")
    print()

    # ---- Step 8: Analyze with LLM ----
    results = []
    analysis_units = build_analysis_units(modules, vcx_classes, forms, menus, projects, db_info)
    total = len(analysis_units)

    if args.skip_llm:
        print(f"[8/8] Skipping LLM analysis (--skip-llm). {total} units extracted.")
        # Create results with just the extraction data
        for unit in analysis_units:
            results.append(AnalysisResult(
                source_file=unit["source_file"],
                unit_name=unit["name"],
                unit_type=unit["type"],
                summary="*(LLM analysis skipped)*\n\n" + unit.get("context", ""),
                original_code_snippet=unit.get("code", "")[:2000],
                python_equivalent=unit.get("python", ""),
            ))
    else:
        print(f"[8/8] Sending {total} code units to Llama 3.1 for analysis...")
        print()

        for i, unit in enumerate(analysis_units, 1):
            print(f"  [{i}/{total}] {unit['type']}: {unit['source_file']}::{unit['name']}")
            start_time = time.time()

            try:
                summary = ollama.summarize_foxpro(
                    code=unit["code"],
                    context=unit["context"],
                    include_python=unit.get("python"),
                )
                elapsed = time.time() - start_time
                print(f"    -> Done ({elapsed:.1f}s)")

                results.append(AnalysisResult(
                    source_file=unit["source_file"],
                    unit_name=unit["name"],
                    unit_type=unit["type"],
                    summary=summary,
                    original_code_snippet=unit.get("code", "")[:2000],
                    python_equivalent=unit.get("python", ""),
                ))
            except Exception as e:
                print(f"    -> ERROR: {e}")
                results.append(AnalysisResult(
                    source_file=unit["source_file"],
                    unit_name=unit["name"],
                    unit_type=unit["type"],
                    summary=f"*Analysis failed: {e}*",
                ))

    print()

    # ---- Generate report ----
    stats = {
        "PRG/MPR Modules": len(modules),
        "Procedures/Functions": total_procs,
        "Classes (in PRG)": total_classes_in_prg,
        "VCX Classes": len(vcx_classes),
        "SCX Forms": len(forms),
        "MNX Menus": len(menus),
        "PJX Projects": len(projects),
        "DB Tables Referenced": len(db_info["tables"]),
        "SQL Statements": len(db_info["sql_statements"]),
    }

    report_path = report_gen.generate(results, stats, source_dir, args.output)

    print("=" * 60)
    print("  Analysis Complete!")
    print(f"  Report: {report_path}")
    print(f"  Units analyzed: {len(results)}")
    print("=" * 60)


def build_analysis_units(modules, vcx_classes, forms, menus, projects, db_info=None):
    """
    Build a flat list of code units to send to the LLM.
    Each unit has: source_file, name, type, code, context, python.
    """
    units = []

    # PRG modules - main code + each procedure
    for module in modules:
        # Module-level summary
        if module.raw_source:
            units.append({
                "source_file": module.file_name,
                "name": f"{module.file_name} (overview)",
                "type": "MODULE",
                "code": _truncate(module.raw_source),
                "context": _module_context(module),
                "python": module.converted_python or "",
            })

        # Individual procedures
        for proc in module.procedures:
            if proc.name == "__main__":
                continue  # already covered in module overview
            units.append({
                "source_file": module.file_name,
                "name": proc.name,
                "type": proc.proc_type,
                "code": _truncate(proc.source_code),
                "context": f"File: {module.file_name}, {proc.proc_type}: {proc.name}"
                           + (f"\nParameters: {proc.parameters}" if proc.parameters else "")
                           + (f"\nComment: {proc.comment}" if proc.comment else ""),
                "python": proc.converted_python or "",
            })

        # Classes defined in PRG
        for cls in module.classes:
            units.append({
                "source_file": module.file_name,
                "name": cls.class_name,
                "type": "CLASS",
                "code": _truncate(cls.methods_code),
                "context": f"File: {module.file_name}, Class: {cls.class_name} "
                           f"(base: {cls.base_class})",
                "python": cls.converted_python or "",
            })

    # VCX classes
    for cls in vcx_classes:
        units.append({
            "source_file": cls.library_file,
            "name": cls.class_name,
            "type": "CLASS",
            "code": _truncate(cls.methods_code),
            "context": f"Visual Class Library: {cls.library_file}\n"
                       f"Class: {cls.class_name}, Base: {cls.base_class}\n"
                       f"Objects: {len(cls.contained_objects)}"
                       + (f"\nProperties:\n{cls.properties[:500]}" if cls.properties else ""),
            "python": cls.converted_python or "",
        })

    # SCX forms
    for form in forms:
        units.append({
            "source_file": form.source_file,
            "name": form.form_name,
            "type": "FORM",
            "code": _truncate(form.full_code),
            "context": f"Screen/Form: {form.source_file}\n"
                       f"Controls: {len(form.controls)}\n"
                       f"Control list: {', '.join(c.get('name', '') + '(' + c.get('base_class', '') + ')' for c in form.controls[:20])}"
                       + (f"\nProperties:\n{form.properties[:500]}" if form.properties else ""),
            "python": form.converted_python or "",
        })

    # MNX menus
    for menu in menus:
        units.append({
            "source_file": menu.source_file,
            "name": menu.menu_name,
            "type": "MENU",
            "code": _truncate(menu.full_code),
            "context": f"Menu Definition: {menu.source_file}\n"
                       f"Pads: {len(menu.pads)}, Items: {len(menu.items)}\n"
                       f"Menu bar: {', '.join(p.get('prompt', '') for p in menu.pads)}",
            "python": menu.converted_python or "",
        })

    # PJX projects
    for proj in projects:
        file_list = "\n".join(
            f"  - {f.get('name', f) if isinstance(f, dict) else f}"
            for f in proj.files[:50]
        )
        units.append({
            "source_file": f"{proj.project_name}.pjx",
            "name": proj.project_name,
            "type": "PROJECT",
            "code": f"Project: {proj.project_name}\n"
                    f"Main Program: {proj.main_program}\n"
                    f"Files ({len(proj.files)}):\n{file_list}",
            "context": f"FoxPro Project: {proj.project_name}\n"
                       f"Main: {proj.main_program}, Files: {len(proj.files)}",
        })

    # DB Schema summary as a single analysis unit
    if db_info and db_info.get("tables"):
        units.append({
            "source_file": "(database schema)",
            "name": "Database Schema & Data Access",
            "type": "SCHEMA",
            "code": _truncate(db_info["summary_text"]),
            "context": f"Database schema extracted from all source files.\n"
                       f"Tables: {len(db_info['tables'])}, "
                       f"SQL statements: {len(db_info['sql_statements'])}, "
                       f"Indexes: {len(db_info['indexes'])}, "
                       f"Relations: {len(db_info['relations'])}",
        })

    return units


def _truncate(text, max_len=12000):
    """Truncate code to fit LLM context."""
    if not text:
        return ""
    if len(text) > max_len:
        return text[:max_len] + f"\n\n... [truncated, {len(text)} total chars]"
    return text


def _module_context(module):
    """Build context string for a module."""
    parts = [f"File: {module.file_name} ({module.file_type})"]
    parts.append(f"Lines: {module.line_count}")
    if module.header_comment:
        parts.append(f"Header: {module.header_comment[:300]}")
    if module.procedures:
        proc_names = [p.name for p in module.procedures if p.name != "__main__"]
        parts.append(f"Procedures: {', '.join(proc_names)}")
    if module.classes:
        class_names = [c.class_name for c in module.classes]
        parts.append(f"Classes: {', '.join(class_names)}")
    if module.preprocessor_defines:
        parts.append(f"Defines: {', '.join(module.preprocessor_defines[:10])}")
    return "\n".join(parts)


if __name__ == "__main__":
    main()
