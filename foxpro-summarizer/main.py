#!/usr/bin/env python -u
"""
FoxPro Application Summarizer
==============================
Uses vfp2py to deeply parse FoxPro source code (.prg, .vcx, .scx, .mnx, .pjx)
and sends extracted logic to a local LLM for human-readable summaries.

Supported LLM backends:
  - ollama   (default) — Ollama REST API at localhost:11434
  - llamacpp           — llama.cpp server (OpenAI-compatible) at localhost:8000

Usage:
    python main.py <foxpro-source-dir> [--backend ollama|llamacpp] [--url URL] [--model MODEL]

Examples:
    python main.py ../sample-foxpro-app
    python main.py ../sample-foxpro-app --backend llamacpp --url http://localhost:8000
    python main.py /path/to/real/foxpro/app --backend ollama --model llama3.1:70b
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

from config import (
    LLM_BACKEND,
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    LLAMACPP_BASE_URL,
    LLAMACPP_MODEL,
)
from extractors.prg_extractor import PrgExtractor
from extractors.vcx_extractor import VcxExtractor
from extractors.scx_extractor import ScxExtractor
from extractors.mnx_extractor import MnxExtractor
from extractors.pjx_extractor import PjxExtractor
from extractors.db_extractor import DbExtractor
from extractors.ui_extractor import UiExtractor
from llm.ollama_client import OllamaClient
from llm.llamacpp_client import LlamaCppClient
from llm.response_parser import split_response
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
        description="FoxPro Application Summarizer using vfp2py + local LLM"
    )
    parser.add_argument("source_dir", help="Path to FoxPro source directory")
    parser.add_argument(
        "--backend", default=LLM_BACKEND, choices=["ollama", "llamacpp"],
        help="LLM backend: 'ollama' (default) or 'llamacpp' (llama.cpp server)",
    )
    parser.add_argument("--url", default=None, help="LLM server URL (default depends on backend)")
    parser.add_argument("--model", default=None, help="LLM model name (default depends on backend)")
    parser.add_argument("--output", default=None, help="Output report file path")
    parser.add_argument(
        "--report-type", default="both", choices=["both", "business", "technical"],
        help="Report type: 'both' (default), 'business', or 'technical'",
    )
    parser.add_argument("--skip-llm", action="store_true", help="Skip LLM analysis (extraction only)")
    parser.add_argument("--verbose", action="store_true", help="Enable debug logging")

    args = parser.parse_args()

    # Resolve defaults based on backend
    if args.url is None:
        args.url = LLAMACPP_BASE_URL if args.backend == "llamacpp" else OLLAMA_BASE_URL
    if args.model is None:
        args.model = LLAMACPP_MODEL if args.backend == "llamacpp" else OLLAMA_MODEL

    return args


def main():
    args = parse_args()

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    source_dir = os.path.abspath(args.source_dir)
    if not os.path.isdir(source_dir):
        print(f"ERROR: Directory not found: {source_dir}")
        sys.exit(1)

    backend_label = "llama.cpp" if args.backend == "llamacpp" else "Ollama"
    model_label = args.model if args.model else "(server default)"

    print("=" * 60)
    print("  FoxPro Application Summarizer")
    print(f"  vfp2py + {backend_label}")
    print("=" * 60)
    print(f"  Source:   {source_dir}")
    print(f"  Backend:  {backend_label} @ {args.url}")
    print(f"  Model:    {model_label}")
    print()

    # Initialize LLM client
    if args.backend == "llamacpp":
        llm = LlamaCppClient(args.url, args.model)
    else:
        llm = OllamaClient(args.url, args.model)
    prg_ext = PrgExtractor()
    vcx_ext = VcxExtractor()
    scx_ext = ScxExtractor()
    mnx_ext = MnxExtractor()
    pjx_ext = PjxExtractor()
    db_ext = DbExtractor()
    ui_ext = UiExtractor()
    report_gen = ReportGenerator(report_type=args.report_type)

    # ---- Step 1: Test LLM connection ----
    if not args.skip_llm:
        print(f"[1/9] Testing {backend_label} connection...")
        if not llm.test_connection():
            print(f"  ERROR: Cannot reach {backend_label} at {args.url}")
            if args.backend == "llamacpp":
                print(f"  Make sure llama.cpp server is running: llama-server -m <model> --port 8000")
            else:
                print(f"  Make sure: ollama serve && ollama pull {args.model}")
            sys.exit(1)
        # Refresh model label in case test_connection() auto-selected one
        model_label = llm.model if hasattr(llm, "model") and llm.model else model_label
        print(f"  -> Connected to {args.url} with model {model_label}")
        print()
    else:
        print("[1/9] Skipping LLM connection (--skip-llm mode)")
        print()

    # ---- Step 2: Extract .prg files ----
    print("[2/9] Extracting .prg / .mpr / .spr program files...")
    modules = prg_ext.extract_directory(source_dir)
    total_procs = sum(len(m.procedures) for m in modules)
    total_classes_in_prg = sum(len(m.classes) for m in modules)
    print(f"  -> {len(modules)} files, {total_procs} procedures, {total_classes_in_prg} classes")
    for m in modules:
        py_status = "+ Python" if m.converted_python else ""
        print(f"     {m.file_name}: {m.line_count} lines, {len(m.procedures)} procs {py_status}")
    print()

    # ---- Step 3: Extract .vcx/.vct class libraries ----
    print("[3/9] Extracting .vcx/.vct Visual Class Libraries...")
    vcx_classes = vcx_ext.extract_directory(source_dir)
    print(f"  -> {len(vcx_classes)} classes extracted")
    for cls in vcx_classes:
        print(f"     {cls.library_file}::{cls.class_name} (base: {cls.base_class})")
    print()

    # ---- Step 4: Extract .scx/.sct forms ----
    print("[4/9] Extracting .scx/.sct Screen/Form files...")
    forms = scx_ext.extract_directory(source_dir)
    print(f"  -> {len(forms)} forms extracted")
    for form in forms:
        print(f"     {form.source_file}: {len(form.controls)} controls")
    print()

    # ---- Step 5: Extract .mnx/.mnt menus ----
    print("[5/9] Extracting .mnx/.mnt Menu definitions...")
    menus = mnx_ext.extract_directory(source_dir)
    print(f"  -> {len(menus)} menus extracted")
    for menu in menus:
        print(f"     {menu.source_file}: {len(menu.pads)} pads, {len(menu.items)} items")
    print()

    # ---- Step 6: Extract .pjx projects ----
    print("[6/9] Extracting .pjx Project files...")
    projects = pjx_ext.extract_directory(source_dir)
    print(f"  -> {len(projects)} projects extracted")
    for proj in projects:
        print(f"     {proj.project_name}: {len(proj.files)} files, main={proj.main_program}")
    print()

    # ---- Step 7: Extract SQL & DB schema ----
    print("[7/9] Extracting SQL statements, table references & DB schema...")
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

    # ---- Step 8: Extract UI controls, buttons & event handlers ----
    print("[8/9] Extracting UI controls, buttons & event handlers...")
    ui_info = ui_ext.extract_directory(source_dir)
    total_controls = len(ui_info["controls"])
    total_handlers = len(ui_info["event_handlers"])
    total_menus_ui = sum(len(m["bars"]) for m in ui_info["menus"].values())
    total_dialogs = len(ui_info["dialogs"])
    total_form_calls = len(ui_info["form_calls"])
    print(f"  -> {len(ui_info['menus'])} menu groups, {total_menus_ui} menu items")
    print(f"  -> {total_controls} form controls (buttons, textboxes, etc.)")
    print(f"  -> {total_handlers} event handlers (Click, DblClick, etc.)")
    print(f"  -> {total_form_calls} DO FORM calls")
    print(f"  -> {total_dialogs} user dialogs (MESSAGEBOX)")
    for menu_name, menu_data in sorted(ui_info["menus"].items()):
        items_with_action = [b for b in menu_data["bars"] if b["action"]]
        print(f"     {menu_name}: {len(menu_data['bars'])} items, "
              f"{len(items_with_action)} with actions")
    for handler in ui_info["event_handlers"]:
        print(f"     {handler['control']}.{handler['event']} ({handler['file']})")
    print()

    # ---- Step 9: Analyze with LLM ----
    results = []
    analysis_units = build_analysis_units(modules, vcx_classes, forms, menus, projects, db_info, ui_info)
    total = len(analysis_units)

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
        "UI Controls": total_controls,
        "Event Handlers": total_handlers,
        "Menu Items": total_menus_ui,
    }

    analyzer_label = f"vfp2py + {backend_label} ({model_label})"

    if args.skip_llm:
        print(f"[9/9] Skipping LLM analysis (--skip-llm). {total} units extracted.")
        # Create results with just the extraction data
        for unit in analysis_units:
            context_text = unit.get("context", "")
            results.append(AnalysisResult(
                source_file=unit["source_file"],
                unit_name=unit["name"],
                unit_type=unit["type"],
                summary="*(LLM analysis skipped)*\n\n" + context_text,
                original_code_snippet=unit.get("code", "")[:2000],
                python_equivalent=unit.get("python", ""),
                business_summary="*(LLM analysis skipped)*\n\n" + context_text,
                technical_summary="*(LLM analysis skipped)*\n\n" + context_text,
            ))
        # Batch mode for skip-llm (fast, no incremental needed)
        biz_path, tech_path = report_gen.generate(
            results, stats, source_dir, args.output,
            analyzer_label=analyzer_label, db_info=db_info, ui_info=ui_info)
    else:
        print(f"[9/9] Sending {total} code units to {backend_label} ({model_label}) for analysis...")
        print()

        # Start incremental report -- writes header immediately
        biz_path, tech_path = report_gen.start(
            stats, source_dir, total, args.output, analyzer_label=analyzer_label)
        if biz_path:
            print(f"  Business report (live): {biz_path}")
        if tech_path:
            print(f"  Technical report (live): {tech_path}")
        print()

        for i, unit in enumerate(analysis_units, 1):
            print(f"  [{i}/{total}] {unit['type']}: {unit['source_file']}::{unit['name']}")
            start_time = time.time()

            try:
                summary = llm.summarize_foxpro(
                    code=unit["code"],
                    context=unit["context"],
                    include_python=unit.get("python"),
                )
                elapsed = time.time() - start_time
                print(f"    -> Done ({elapsed:.1f}s)")

                # Split LLM response into business and technical parts
                biz_text, tech_text = split_response(summary)

                result = AnalysisResult(
                    source_file=unit["source_file"],
                    unit_name=unit["name"],
                    unit_type=unit["type"],
                    summary=summary,
                    original_code_snippet=unit.get("code", "")[:2000],
                    python_equivalent=unit.get("python", ""),
                    business_summary=biz_text,
                    technical_summary=tech_text,
                )
            except Exception as e:
                print(f"    -> ERROR: {e}")
                error_msg = f"*Analysis failed: {e}*"
                result = AnalysisResult(
                    source_file=unit["source_file"],
                    unit_name=unit["name"],
                    unit_type=unit["type"],
                    summary=error_msg,
                    business_summary=error_msg,
                    technical_summary=error_msg,
                )

            results.append(result)
            report_gen.write_result(result)

        # Finalize: append appendix sections and close files
        report_gen.finish(results, db_info=db_info, ui_info=ui_info)

    print()

    print("=" * 60)
    print("  Analysis Complete!")
    if biz_path:
        print(f"  Business Report:  {biz_path}")
    if tech_path:
        print(f"  Technical Report: {tech_path}")
    print(f"  Units analyzed: {len(results)}")
    print("=" * 60)


def build_analysis_units(modules, vcx_classes, forms, menus, projects, db_info=None, ui_info=None):
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

    # UI Controls & Navigation summary as a single analysis unit
    if ui_info and (ui_info.get("menus") or ui_info.get("event_handlers")
                    or ui_info.get("controls")):
        units.append({
            "source_file": "(ui controls)",
            "name": "UI Controls, Buttons & Navigation",
            "type": "UI",
            "code": _truncate(ui_info["summary_text"]),
            "context": f"UI elements extracted from all source files.\n"
                       f"Menus: {len(ui_info['menus'])}, "
                       f"Controls: {len(ui_info['controls'])}, "
                       f"Event handlers: {len(ui_info['event_handlers'])}, "
                       f"Form calls: {len(ui_info['form_calls'])}, "
                       f"Dialogs: {len(ui_info['dialogs'])}",
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
