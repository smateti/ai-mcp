#!/usr/bin/env python3
"""
transfer_repos.py  (Phase 1 - initial migration)
================================================
Throttled migration of projects from an old GitLab server to a new one
using the *direct transfer* (bulk import) API:

    POST {destination}/api/v4/bulk_imports

How it protects the source server
---------------------------------
Direct transfer is pull-based: the DESTINATION instance asks the SOURCE
instance to export each entity, which costs the source CPU/Sidekiq.
To keep that load controlled, this script:

    1. Enumerates all projects under `top_level_group` on the source.
    2. Submits them in small batches (default 10 projects per
       bulk-import request - `batch_size` in config).
    3. POLLS the bulk import until it reaches a terminal state
       (finished / failed / timeout) before doing anything else, so at
       most one batch is ever exporting on the source.
    4. SLEEPS `sleep_between_batches_seconds` (default 300) between
       batches to let the source breathe.
    5. Persists progress to `state_file` after every project, so the
       script can be stopped (Ctrl-C) and re-run; already-completed
       projects are skipped on resume.

Failures
--------
Any project whose bulk-import entity ends in `failed`/`timeout`, or any
batch that cannot be submitted at all, is appended to
    reports/transfer_failures.csv
with a reason (including GitLab's per-entity failure details when
available). The run continues with the next batch.

Prerequisites
-------------
* Direct transfer must be enabled on BOTH instances
  (Admin Area > Settings > General > Import and export settings).
* Destination token: `api` scope, permission to create projects in the
  destination namespace. Source token: `api` + `read_repository`.
* The destination namespace (group/subgroup tree) is derived from each
  project's source path: by default the project is recreated under the
  SAME full path on the destination. The group hierarchy must already
  exist there - migrate groups first, or pre-create them. (You can also
  run one group-level bulk import with "migrate_projects": false to
  copy the empty group tree, then use this script for projects.)

Usage
-----
    python transfer_repos.py                       # uses migration_config.json
    python transfer_repos.py --config other.json
    python transfer_repos.py --dry-run             # list batches, no API writes
    python transfer_repos.py --projects-file paths.txt
        # paths.txt = one source path_with_namespace per line, overrides
        # group enumeration (useful for retrying the failures report)
"""

import argparse
import json
import os
import sys
import time

from gitlab_common import (FailureReport, encode_path, list_all_projects,
                           load_config, make_session, setup_logging, utc_now)

PHASE = "transfer"
TERMINAL_IMPORT_STATES = {"finished", "failed", "timeout"}


def load_state(state_file: str) -> dict:
    """Load resume state from disk.

    The state maps source `path_with_namespace` to a record:
        {"status": "finished"|"failed"|"timeout"|"submitted",
         "bulk_import_id": int, "updated_at": iso8601}

    Args:
        state_file: Path to the JSON state file.

    Returns:
        State dict ({} if the file does not exist yet).
    """
    if os.path.exists(state_file):
        with open(state_file, "r", encoding="utf-8") as fh:
            return json.load(fh)
    return {}


def save_state(state_file: str, state: dict) -> None:
    """Atomically persist resume state (write tmp file, then rename)."""
    tmp = state_file + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(state, fh, indent=2)
    os.replace(tmp, state_file)


def build_entities(batch: list[dict]) -> list[dict]:
    """Convert source project records into bulk-import `entities`.

    Each project is recreated at the SAME full path on the destination:
    destination_namespace = everything before the last '/',
    destination_slug      = the last path segment.

    Args:
        batch: List of project dicts from list_all_projects().

    Returns:
        List of entity dicts for the POST /bulk_imports payload.
    """
    entities = []
    for proj in batch:
        full = proj["path_with_namespace"]
        namespace, _, slug = full.rpartition("/")
        entities.append({
            "source_type": "project_entity",
            "source_full_path": full,
            "destination_slug": slug,
            "destination_namespace": namespace,
        })
    return entities


def submit_batch(dest_session, dest_url: str, source_url: str,
                 source_token: str, batch: list[dict], log) -> int | None:
    """Submit one batch of projects as a single bulk import.

    Args:
        dest_session: Destination-instance session (auth header set).
        dest_url: Destination base URL.
        source_url: Source base URL (sent in the payload `configuration`).
        source_token: Source token (sent in the payload `configuration`).
        batch: Project records for this batch.
        log: Logger.

    Returns:
        The created bulk import id, or None if submission failed.
    """
    payload = {
        "configuration": {"url": source_url, "access_token": source_token},
        "entities": build_entities(batch),
    }
    resp = dest_session.post(f"{dest_url}/api/v4/bulk_imports",
                             json=payload, timeout=120)
    if resp.status_code not in (200, 201):
        log.error("Batch submission failed: HTTP %s %s",
                  resp.status_code, resp.text[:500])
        return None
    bulk_id = resp.json()["id"]
    log.info("Submitted bulk import id=%s with %d projects", bulk_id, len(batch))
    return bulk_id


def wait_for_batch(dest_session, dest_url: str, bulk_id: int,
                   poll_interval: int, timeout: int, log) -> dict:
    """Poll a bulk import until it reaches a terminal state, then fetch
    per-entity outcomes.

    Args:
        dest_session: Destination session.
        dest_url: Destination base URL.
        bulk_id: Bulk import id returned by submit_batch().
        poll_interval: Seconds between status polls.
        timeout: Give up after this many seconds (the import keeps
            running server-side; we just stop waiting and mark unknown
            entities as 'timeout' so they land in the failure report
            for manual review).
        log: Logger.

    Returns:
        Mapping of source_full_path -> {"status": str, "failures": list}.
    """
    deadline = time.time() + timeout
    status = "created"
    while time.time() < deadline:
        resp = dest_session.get(f"{dest_url}/api/v4/bulk_imports/{bulk_id}",
                                timeout=60)
        resp.raise_for_status()
        status = resp.json()["status"]
        if status in TERMINAL_IMPORT_STATES:
            break
        log.info("  bulk_import %s status=%s ... waiting %ss",
                 bulk_id, status, poll_interval)
        time.sleep(poll_interval)
    else:
        log.warning("  bulk_import %s did not finish within %ss (last=%s)",
                    bulk_id, timeout, status)

    # Per-entity results (paginated; batches are small so one page).
    resp = dest_session.get(
        f"{dest_url}/api/v4/bulk_imports/{bulk_id}/entities",
        params={"per_page": 100}, timeout=60)
    resp.raise_for_status()
    results = {}
    for ent in resp.json():
        results[ent["source_full_path"]] = {
            "status": ent["status"],
            "failures": ent.get("failures", []),
        }
    return results


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="migration_config.json")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show the batch plan without calling any write API.")
    parser.add_argument("--projects-file",
                        help="File with one source path_with_namespace per line; "
                             "overrides group enumeration.")
    args = parser.parse_args()

    cfg = load_config(args.config)
    log = setup_logging(os.path.join(cfg["reports_dir"], "transfer.log"))
    failures = FailureReport(cfg["reports_dir"], "transfer_failures.csv")

    src = make_session(cfg["source"]["token"])
    dst = make_session(cfg["destination"]["token"])

    # ---- enumerate -------------------------------------------------------
    if args.projects_file:
        with open(args.projects_file, encoding="utf-8") as fh:
            wanted = [ln.strip() for ln in fh if ln.strip()]
        projects = [{"path_with_namespace": p} for p in wanted]
        log.info("Loaded %d project paths from %s", len(projects), args.projects_file)
    else:
        log.info("Enumerating projects under group '%s' on %s ...",
                 cfg.get("top_level_group"), cfg["source"]["url"])
        projects = list_all_projects(src, cfg["source"]["url"],
                                     cfg.get("top_level_group"))
        log.info("Found %d projects on source.", len(projects))

    # ---- resume filter ---------------------------------------------------
    state = load_state(cfg["state_file"])
    pending = [p for p in projects
               if state.get(p["path_with_namespace"], {}).get("status") != "finished"]
    log.info("%d already finished (state file), %d pending.",
             len(projects) - len(pending), len(pending))

    batch_size = cfg["batch_size"]
    batches = [pending[i:i + batch_size] for i in range(0, len(pending), batch_size)]

    if args.dry_run:
        for i, b in enumerate(batches, 1):
            log.info("Batch %d/%d: %s", i, len(batches),
                     ", ".join(p["path_with_namespace"] for p in b))
        log.info("Dry run complete - nothing submitted.")
        return

    # ---- transfer loop ---------------------------------------------------
    for i, batch in enumerate(batches, 1):
        log.info("=== Batch %d/%d (%d projects) at %s ===",
                 i, len(batches), len(batch), utc_now())
        bulk_id = submit_batch(dst, cfg["destination"]["url"],
                               cfg["source"]["url"], cfg["source"]["token"],
                               batch, log)
        if bulk_id is None:
            for p in batch:
                failures.add(PHASE, p["path_with_namespace"],
                             "bulk import submission failed (see transfer.log)")
                state[p["path_with_namespace"]] = {"status": "failed",
                                                   "updated_at": utc_now()}
            save_state(cfg["state_file"], state)
            time.sleep(cfg["sleep_between_batches_seconds"])
            continue

        results = wait_for_batch(dst, cfg["destination"]["url"], bulk_id,
                                 cfg["poll_interval_seconds"],
                                 cfg["batch_timeout_seconds"], log)

        for p in batch:
            path = p["path_with_namespace"]
            res = results.get(path, {"status": "timeout", "failures": []})
            state[path] = {"status": res["status"], "bulk_import_id": bulk_id,
                           "updated_at": utc_now()}
            if res["status"] == "finished":
                log.info("  OK  %s", path)
            else:
                reason = res["status"]
                if res["failures"]:
                    f0 = res["failures"][0]
                    reason += f" | {f0.get('pipeline_class', '')}: " \
                              f"{f0.get('exception_message', '')[:200]}"
                log.error("  FAIL %s -> %s", path, reason)
                failures.add(PHASE, path, reason)
        save_state(cfg["state_file"], state)

        if i < len(batches):
            log.info("Sleeping %ss before next batch ...",
                     cfg["sleep_between_batches_seconds"])
            time.sleep(cfg["sleep_between_batches_seconds"])

    done = sum(1 for v in state.values() if v.get("status") == "finished")
    log.info("Transfer pass complete: %d/%d finished. Failures (if any) in %s",
             done, len(projects), failures.path)
    failures.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\nInterrupted - state saved after last completed batch; "
                 "re-run to resume.")
