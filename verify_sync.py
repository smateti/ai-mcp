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
    "labels": ("labels", {}),
    "milestones": ("milestones", {"state": "all"}),
}


def get_metadata_counts(session, base_url: str, project_path: str) -> dict:
    """Fetch item COUNTS for issues, MRs, labels, and milestones.

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


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="migration_config.json")
    parser.add_argument("--projects-file",
                        help="Verify only these paths (one per line) instead "
                             "of enumerating the whole group.")
    parser.add_argument("--check-metadata", action="store_true",
                        help="Also compare COUNTS of issues, merge requests, "
                             "labels, and milestones (git refs alone cannot "
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

    src = make_session(cfg["source"]["token"])
    dst = make_session(cfg["destination"]["token"])

    if args.projects_file:
        with open(args.projects_file, encoding="utf-8") as fh:
            paths = [ln.strip() for ln in fh if ln.strip()]
    else:
        log.info("Enumerating source projects ...")
        paths = [p["path_with_namespace"]
                 for p in list_all_projects(src, cfg["source"]["url"],
                                            cfg.get("top_level_group"))]
    log.info("Verifying %d projects ...", len(paths))

    ts = utc_now().replace(":", "")
    report_path = os.path.join(cfg["reports_dir"], f"sync_report_{ts}.csv")
    oos_path = os.path.join(cfg["reports_dir"], f"out_of_sync_{ts}.txt")
    missing_path = os.path.join(cfg["reports_dir"], f"missing_{ts}.txt")

    counts = {"in_sync": 0, "out_of_sync": 0, "missing": 0, "error": 0}

    with open(report_path, "w", newline="", encoding="utf-8") as rep_fh, \
         open(oos_path, "w", encoding="utf-8") as oos_fh, \
         open(missing_path, "w", encoding="utf-8") as miss_fh:

        writer = csv.writer(rep_fh)
        writer.writerow(["project_path", "status", "branches_src",
                         "branches_dst", "tags_src", "tags_dst", "differences"])

        for n, path in enumerate(paths, 1):
            try:
                src_refs = get_refs(src, cfg["source"]["url"], path)
                if src_refs is None:
                    # Gone from source (deleted since enumeration) - skip.
                    log.warning("[%d/%d] %s vanished from source, skipping",
                                n, len(paths), path)
                    continue
                dst_refs = get_refs(dst, cfg["destination"]["url"], path)
                src_meta = dst_meta = None
                if args.check_metadata and dst_refs is not None:
                    src_meta = get_metadata_counts(src, cfg["source"]["url"], path)
                    dst_meta = get_metadata_counts(dst, cfg["destination"]["url"], path)
            except requests.RequestException as exc:
                counts["error"] += 1
                log.error("[%d/%d] %s API error: %s", n, len(paths), path, exc)
                failures.add(PHASE, path, f"verification API error: {exc}")
                continue

            if dst_refs is None:
                counts["missing"] += 1
                writer.writerow([path, "missing", len(src_refs["branches"]),
                                 "-", len(src_refs["tags"]), "-",
                                 "project not found on target"])
                miss_fh.write(path + "\n")
                log.warning("[%d/%d] MISSING     %s", n, len(paths), path)
                continue

            diffs = diff_refs(src_refs, dst_refs)
            if src_meta is not None:
                diffs.extend(diff_metadata(src_meta, dst_meta))
            if diffs:
                counts["out_of_sync"] += 1
                writer.writerow([path, "out_of_sync",
                                 len(src_refs["branches"]), len(dst_refs["branches"]),
                                 len(src_refs["tags"]), len(dst_refs["tags"]),
                                 "; ".join(diffs[:10])])
                oos_fh.write(path + "\n")
                log.warning("[%d/%d] OUT_OF_SYNC %s (%d diffs)",
                            n, len(paths), path, len(diffs))
            else:
                counts["in_sync"] += 1
                writer.writerow([path, "in_sync",
                                 len(src_refs["branches"]), len(dst_refs["branches"]),
                                 len(src_refs["tags"]), len(dst_refs["tags"]), ""])
                if n % 100 == 0:
                    log.info("[%d/%d] progress ... last: %s in_sync",
                             n, len(paths), path)

    log.info("Done. in_sync=%(in_sync)d out_of_sync=%(out_of_sync)d "
             "missing=%(missing)d errors=%(error)d", counts)
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
