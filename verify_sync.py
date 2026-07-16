#!/usr/bin/env python3
"""
verify_sync.py  (Phase 2 - verification, run 1-2 weeks after transfer)
======================================================================
Compares every migrated repository on the SOURCE server against the
TARGET server and classifies each project as:

    in_sync       - identical branches and tags (name AND commit SHA)
    out_of_sync   - exists on both, but branches/tags differ (the source
                    received new pushes after migration, or the import
                    was partial)
    missing       - exists on source but not on target at the expected
                    path (never imported, or import failed)

Why this script exists
----------------------
GitLab's direct transfer (bulk import) API has NO incremental/delta
mode: re-importing an existing path creates a new copy rather than
updating the existing project. There is also no "compare two instances"
API. So sync detection is done here, cheaply, via the REST API:

    GET /projects/:id/repository/branches   (name -> head commit SHA)
    GET /projects/:id/repository/tags       (name -> commit SHA)

Comparing branch/tag tip SHAs is sufficient: two repos whose every ref
points at the same SHA have identical reachable git history. This costs
only a handful of paginated GETs per project on each side - light
enough for 10k repos and far cheaper than cloning.

Outputs (in reports/, all timestamped per run)
----------------------------------------------
    sync_report_<ts>.csv   - one row per project with classification and
                             a short diff summary
    out_of_sync_<ts>.txt   - just the project paths, one per line; feed
                             this file directly to resync_repos.py
    missing_<ts>.txt       - paths missing on target; feed this file to
                             transfer_repos.py --projects-file
    verify_failures.csv    - projects that could not be verified (API
                             errors on either side)

Usage
-----
    python verify_sync.py                      # full comparison
    python verify_sync.py --projects-file paths.txt   # verify a subset
"""

import argparse
import csv
import os
import sys

import requests

from gitlab_common import (FailureReport, api_get_paginated, encode_path,
                           list_all_projects, load_config, make_session,
                           setup_logging, utc_now)

PHASE = "verify"


def get_subgroups(session, base_url: str, group_path: str) -> list[str]:
    """Get all immediate subgroups under a given group path (paginated).

    Args:
        session: API session for the instance.
        base_url: Instance base URL.
        group_path: Path to the parent group.

    Returns:
        List of subgroup full_path strings.
    """
    try:
        raw = api_get_paginated(
            session, base_url,
            f"/api/v4/groups/{encode_path(group_path)}/subgroups")
        return [sg["full_path"] for sg in raw]
    except requests.RequestException:
        return []


def get_direct_projects(session, base_url: str, group_path: str) -> list[str]:
    """Fetch only the direct (non-recursive) projects of a single group.

    Args:
        session: API session for the instance.
        base_url: Instance base URL.
        group_path: Full path of the group.

    Returns:
        List of path_with_namespace strings for projects directly in this group.
    """
    raw = api_get_paginated(
        session, base_url,
        f"/api/v4/groups/{encode_path(group_path)}/projects",
        params={"include_subgroups": False, "archived": False,
                "simple": True, "order_by": "path", "sort": "asc"})
    return [p["path_with_namespace"] for p in raw]


def get_refs(session, base_url: str, project_path: str) -> dict | None:
    """Fetch all branch and tag tips for one project.

    Args:
        session: API session for the instance.
        base_url: Instance base URL.
        project_path: Full path_with_namespace of the project.

    Returns:
        {"branches": {name: sha}, "tags": {name: sha},
         "default_branch": str|None}
        or None if the project does not exist (HTTP 404).

    Raises:
        requests.HTTPError: for non-404 API errors (caller reports them).
    """
    pid = encode_path(project_path)
    resp = session.get(f"{base_url}/api/v4/projects/{pid}", timeout=60)
    if resp.status_code == 404:
        return None
    resp.raise_for_status()
    default_branch = resp.json().get("default_branch")

    branches = api_get_paginated(
        session, base_url, f"/api/v4/projects/{pid}/repository/branches")
    tags = api_get_paginated(
        session, base_url, f"/api/v4/projects/{pid}/repository/tags")

    return {
        "branches": {b["name"]: b["commit"]["id"] for b in branches},
        "tags": {t["name"]: t["commit"]["id"] for t in tags},
        "default_branch": default_branch,
    }


# Metadata collections compared by --check-metadata: API subpath + params.
METADATA_KINDS = {
    "issues": ("issues", {"state": "all", "scope": "all"}),
    "merge_requests": ("merge_requests", {"state": "all", "scope": "all"}),
    "milestones": ("milestones", {"state": "all"}),
}


def get_metadata_counts(session, base_url: str, project_path: str) -> dict:
    """Fetch item COUNTS for issues, MRs,  and milestones.

    Uses per_page=1 requests and reads the `X-Total` response header, so
    each collection costs a single tiny GET regardless of its size.
    This is a count comparison, not a content comparison: equal counts
    do not guarantee identical items, but unequal counts reliably flag
    partial imports or post-migration activity on the source.

    Args:
        session: API session for the instance.
        base_url: Instance base URL.
        project_path: Full path_with_namespace.

    Returns:
        {"issues": int|-1, "merge_requests": int|-1, ...} (-1 = the
        instance did not return X-Total; shown as '?' in reports and
        excluded from the comparison rather than producing false diffs).
    """
    pid = encode_path(project_path)
    counts = {}
    for kind, (subpath, params) in METADATA_KINDS.items():
        p = dict(params)
        p["per_page"] = 1
        resp = session.get(f"{base_url}/api/v4/projects/{pid}/{subpath}",
                           params=p, timeout=60)
        resp.raise_for_status()
        total = resp.headers.get("X-Total")
        counts[kind] = int(total) if total is not None else -1
    return counts


def diff_metadata(src_counts: dict, dst_counts: dict) -> list[str]:
    """Compare metadata counts source vs target.

    Args:
        src_counts: get_metadata_counts() for the source project.
        dst_counts: get_metadata_counts() for the target project.

    Returns:
        Difference strings, e.g. "issues count differs (src 42 != dst 40)";
        kinds with unknown counts (-1) on either side are skipped.
    """
    diffs = []
    for kind in METADATA_KINDS:
        s, d = src_counts.get(kind, -1), dst_counts.get(kind, -1)
        if s == -1 or d == -1:
            continue
        if s != d:
            diffs.append(f"{kind} count differs (src {s} != dst {d})")
    return diffs


def diff_refs(src: dict, dst: dict) -> list[str]:
    """Produce a human-readable list of ref differences source vs target.

    Args:
        src: get_refs() result for the source project.
        dst: get_refs() result for the target project.

    Returns:
        List of difference strings; empty list means in sync.
    """
    diffs = []
    for kind in ("branches", "tags"):
        s, d = src[kind], dst[kind]
        for name in sorted(set(s) - set(d)):
            diffs.append(f"{kind[:-1]} '{name}' missing on target")
        for name in sorted(set(d) - set(s)):
            diffs.append(f"{kind[:-1]} '{name}' only on target")
        for name in sorted(set(s) & set(d)):
            if s[name] != d[name]:
                diffs.append(f"{kind[:-1]} '{name}' SHA differs "
                             f"(src {s[name][:8]} != dst {d[name][:8]})")
    if src["default_branch"] != dst["default_branch"]:
        diffs.append(f"default branch differs "
                     f"(src '{src['default_branch']}' != dst '{dst['default_branch']}')")
    return diffs


def _meta_cells(meta: dict | None) -> list:
    """Return metadata cells (issues/mrs/milestones) for one side.

    '-' when --check-metadata is off, '?' when the instance did not
    report a count (X-Total returned -1).
    """
    if meta is None:
        return ["-"] * 3
    order = ("issues", "merge_requests", "milestones")
    return [("?" if meta.get(k, -1) == -1 else meta[k]) for k in order]


def _meta_columns(src_meta: dict | None, dst_meta: dict | None) -> list:
    """Interleave src/dst metadata cells to match the CSV header order
    (issues_src, issues_dst, mrs_src, mrs_dst, milestones_src, milestones_dst).
    """
    s, d = _meta_cells(src_meta), _meta_cells(dst_meta)
    return [v for pair in zip(s, d) for v in pair]


def compare_project(path: str, src_session, dst_session,
                    src_url: str, dst_url: str, check_metadata: bool,
                    counter: list, writer, oos_fh, miss_fh,
                    counts: dict, failures, log) -> None:
    """Fetch refs (and optionally metadata) for one project on both sides,
    classify it, write the CSV row and output-file lines.

    Args:
        path: Full path_with_namespace of the project.
        src_session / dst_session: API sessions for source / destination.
        src_url / dst_url: Base URLs for source / destination.
        check_metadata: Whether to compare issue/MR/milestone counts.
        counter: Mutable ``[int]`` — incremented here, used for log lines.
        writer: csv.writer for the sync report.
        oos_fh / miss_fh: File handles for out-of-sync / missing lists.
        counts: Shared dict ``{in_sync, out_of_sync, missing, error}``.
        failures: FailureReport instance.
        log: Logger.
    """
    counter[0] += 1
    n = counter[0]
    try:
        src_refs = get_refs(src_session, src_url, path)
        if src_refs is None:
            log.warning("[%d] %s vanished from source, skipping", n, path)
            return
        dst_refs = get_refs(dst_session, dst_url, path)
        src_meta = dst_meta = None
        if check_metadata and dst_refs is not None:
            src_meta = get_metadata_counts(src_session, src_url, path)
            dst_meta = get_metadata_counts(dst_session, dst_url, path)
    except requests.RequestException as exc:
        counts["error"] += 1
        log.error("[%d] %s API error: %s", n, path, exc)
        failures.add(PHASE, path, f"verification API error: {exc}")
        return

    if dst_refs is None:
        counts["missing"] += 1
        writer.writerow([path, "missing", len(src_refs["branches"]),
                         "-", len(src_refs["tags"]), "-"]
                        + _meta_columns(None, None)
                        + ["project not found on target"])
        miss_fh.write(path + "\n")
        log.warning("[%d] MISSING     %s", n, path)
        return

    diffs = diff_refs(src_refs, dst_refs)
    if src_meta is not None and dst_meta is not None:
        diffs.extend(diff_metadata(src_meta, dst_meta))
    if diffs:
        counts["out_of_sync"] += 1
        writer.writerow([path, "out_of_sync",
                         len(src_refs["branches"]), len(dst_refs["branches"]),
                         len(src_refs["tags"]), len(dst_refs["tags"])]
                        + _meta_columns(src_meta, dst_meta)
                        + ["; ".join(diffs[:10])])
        oos_fh.write(path + "\n")
        log.warning("[%d] OUT_OF_SYNC %s (%d diffs)", n, path, len(diffs))
    else:
        counts["in_sync"] += 1
        writer.writerow([path, "in_sync",
                         len(src_refs["branches"]), len(dst_refs["branches"]),
                         len(src_refs["tags"]), len(dst_refs["tags"])]
                        + _meta_columns(src_meta, dst_meta)
                        + [""])
        if n % 100 == 0:
            log.info("[%d] in_sync %s", n, path)


def walk_and_compare(src_session, dst_session, src_url: str, dst_url: str,
                     group_path: str, check_metadata: bool,
                     counter: list, writer, oos_fh, miss_fh,
                     counts: dict, failures, log, depth: int = 0) -> None:
    """Recursively walk a group tree and compare each project inline.

    For every group visited:
      1. Fetch its direct projects and compare each immediately.
      2. Fetch its immediate subgroups and recurse into each.

    This avoids collecting all 10k+ paths upfront and lets comparison
    start as soon as the first projects are discovered.

    Args:
        src_session / dst_session: API sessions.
        src_url / dst_url: Base URLs.
        group_path: Full path of the group to process.
        check_metadata: Whether to compare metadata counts.
        counter: Mutable ``[int]`` shared across the entire walk.
        writer / oos_fh / miss_fh: Output handles (CSV + text files).
        counts: Shared classification counters.
        failures: FailureReport instance.
        log: Logger.
        depth: Current recursion depth (for indented log output).
    """
    indent = "  " * depth
    log.info("%sProcessing group: %s", indent, group_path)

    # --- direct projects in this group ---
    try:
        projects = get_direct_projects(src_session, src_url, group_path)
    except requests.RequestException as exc:
        log.error("%s  Failed to list projects in '%s': %s",
                  indent, group_path, exc)
        failures.add(PHASE, group_path, f"list projects error: {exc}")
        projects = []

    log.info("%s  %d direct projects", indent, len(projects))
    for path in projects:
        compare_project(path, src_session, dst_session, src_url, dst_url,
                        check_metadata, counter, writer, oos_fh, miss_fh,
                        counts, failures, log)

    # --- subgroups → recurse ---
    subgroups = get_subgroups(src_session, src_url, group_path)
    if subgroups:
        log.info("%s  %d subgroups", indent, len(subgroups))
    for sg in subgroups:
        walk_and_compare(src_session, dst_session, src_url, dst_url,
                         sg, check_metadata, counter, writer, oos_fh,
                         miss_fh, counts, failures, log, depth + 1)


def _is_group(session, base_url: str, path: str) -> bool:
    """Return True if *path* resolves as a group on the instance."""
    try:
        resp = session.get(
            f"{base_url}/api/v4/groups/{encode_path(path)}", timeout=60)
        return resp.status_code == 200
    except requests.RequestException:
        return False


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="migration_config.json")
    parser.add_argument("--projects-file",
                        help="Verify only these paths (one per line) instead "
                             "of enumerating the whole group.")
    parser.add_argument("--check-metadata", action="store_true",
                        help="Also compare COUNTS of issues, merge requests, "
                             "and milestones (git refs alone cannot "
                             "detect metadata drift). Count-based: catches "
                             "partial imports and post-migration activity, "
                             "not item-level content edits. Adds 8 small API "
                             "calls per project. NOTE: metadata diffs are NOT "
                             "fixable by mirror re-sync - they need "
                             "resync_repos.py --reimport.")
    args = parser.parse_args()

    cfg = load_config(args.config)
    log = setup_logging(os.path.join(cfg["reports_dir"], "verify.log"))
    failures = FailureReport(cfg["reports_dir"], "verify_failures.csv")

    src_session = make_session(cfg["source"]["token"])
    dst_session = make_session(cfg["destination"]["token"])
    src_url = cfg["source"]["url"]
    dst_url = cfg["destination"]["url"]

    ts = utc_now().replace(":", "")
    report_path = os.path.join(cfg["reports_dir"], f"sync_report_{ts}.csv")
    oos_path = os.path.join(cfg["reports_dir"], f"out_of_sync_{ts}.txt")
    missing_path = os.path.join(cfg["reports_dir"], f"missing_{ts}.txt")

    counts = {"in_sync": 0, "out_of_sync": 0, "missing": 0, "error": 0}
    counter = [0]  # mutable int shared across recursive calls

    with open(report_path, "w", newline="", encoding="utf-8") as rep_fh, \
         open(oos_path, "w", encoding="utf-8") as oos_fh, \
         open(missing_path, "w", encoding="utf-8") as miss_fh:

        writer = csv.writer(rep_fh)
        writer.writerow(["project_path", "status", "branches_src",
                         "branches_dst", "tags_src", "tags_dst",
                         "issues_src", "issues_dst",
                         "mrs_src", "mrs_dst",
                         "milestones_src", "milestones_dst",
                         "differences"])

        if args.projects_file:
            # --- projects-file path: process each entry inline ---
            with open(args.projects_file, encoding="utf-8") as fh:
                input_paths = [ln.strip() for ln in fh if ln.strip()]
            log.info("Loaded %d paths from %s",
                     len(input_paths), args.projects_file)

            for path in input_paths:
                # Try as a project first (common case, avoids extra API call).
                try:
                    src_refs = get_refs(src_session, src_url, path)
                except requests.RequestException:
                    src_refs = None

                if src_refs is not None:
                    # It resolved as a project — compare directly.
                    compare_project(path, src_session, dst_session,
                                    src_url, dst_url, args.check_metadata,
                                    counter, writer, oos_fh, miss_fh,
                                    counts, failures, log)
                elif _is_group(src_session, src_url, path):
                    # It's a group — recurse into it.
                    log.info("'%s' is a group, walking recursively ...", path)
                    walk_and_compare(src_session, dst_session, src_url,
                                     dst_url, path, args.check_metadata,
                                     counter, writer, oos_fh, miss_fh,
                                     counts, failures, log)
                else:
                    log.warning("'%s' is neither a project nor a group on "
                                "source, skipping", path)
        else:
            # --- default path: recursive walk from top-level group ---
            top = cfg.get("top_level_group")
            if top:
                log.info("Walking group tree from '%s' ...", top)
                walk_and_compare(src_session, dst_session, src_url, dst_url,
                                 top, args.check_metadata, counter, writer,
                                 oos_fh, miss_fh, counts, failures, log)
            else:
                log.info("No top_level_group set — listing all projects ...")
                paths = [p["path_with_namespace"]
                         for p in list_all_projects(src_session, src_url, None)]
                log.info("Verifying %d projects ...", len(paths))
                for path in paths:
                    compare_project(path, src_session, dst_session,
                                    src_url, dst_url, args.check_metadata,
                                    counter, writer, oos_fh, miss_fh,
                                    counts, failures, log)

    log.info("Done. Verified %d projects: in_sync=%d out_of_sync=%d "
             "missing=%d errors=%d", counter[0],
             counts["in_sync"], counts["out_of_sync"],
             counts["missing"], counts["error"])
    log.info("Full report : %s", report_path)
    log.info("Out-of-sync list (input for resync_repos.py): %s", oos_path)
    log.info("Missing list (input for transfer_repos.py --projects-file): %s",
             missing_path)
    failures.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\nInterrupted.")
