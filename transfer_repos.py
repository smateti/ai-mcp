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
    """Convert source records into bulk-import `entities`.

    Each entry is recreated at the SAME full path on the destination:
    destination_namespace = everything before the last '/',
    destination_slug      = the last path segment.

    Records may carry an optional "entity_type" key ("project" default,
    or "group" for subgroup paths supplied via --projects-file). A group
    entity imports the subgroup AND everything nested inside it
    (migrate_projects=true), so its destination_namespace only needs the
    PARENT chain to exist on the destination. Note: an empty namespace
    (top-level group) is only valid on self-managed/Dedicated targets.

    Args:
        batch: List of dicts with at least path_with_namespace.

    Returns:
        List of entity dicts for the POST /bulk_imports payload.
    """
    entities = []
    for rec in batch:
        full = rec["path_with_namespace"]
        namespace, _, slug = full.rpartition("/")
        entity = {
            "source_full_path": full,
            "destination_slug": slug,
            "destination_namespace": namespace,
        }
        if rec.get("entity_type") == "group":
            entity["source_type"] = "group_entity"
            entity["migrate_projects"] = True
        else:
            entity["source_type"] = "project_entity"
        entities.append(entity)
    return entities


def classify_path(src_session, base_url: str, path: str) -> str | None:
    """Determine whether a source path is a group or a project.

    Asks the source API: a 200 on /groups/:path means group (subgroup),
    otherwise a 200 on /projects/:path means project.

    Args:
        src_session: Source-instance session.
        base_url: Source base URL.
        path: Full path, e.g. 'g1/sg1/sg2/sg3' or 'g1/sg1/repo'.

    Returns:
        'group', 'project', or None if the path exists as neither.
    """
    if src_session.get(f"{base_url}/api/v4/groups/{encode_path(path)}",
                       params={"with_projects": False}, timeout=60).status_code == 200:
        return "group"
    if src_session.get(f"{base_url}/api/v4/projects/{encode_path(path)}",
                       timeout=60).status_code == 200:
        return "project"
    return None


def ensure_namespace(dst_session, dest_url: str, namespace: str,
                     cache: dict, log) -> bool:
    """Make sure a destination group chain exists, creating missing levels.

    The bulk import API requires `destination_namespace` to be an
    EXISTING group for project entities - it never creates it. This
    walks the chain top-down (g1, then g1/sg1, then g1/sg1/sg2, ...);
    each level is checked with GET /groups/:path and created with
    POST /groups (name=path=segment, parent_id=<level above>) if absent.

    Groups created here are plain private groups carrying only the path -
    no labels/milestones/settings from the source. If you need full group
    metadata, run a group_entity import with migrate_projects=false
    instead; this function is the convenience fallback so project
    transfers never fail on a missing namespace.

    Args:
        dst_session: Destination API session.
        dest_url: Destination base URL.
        namespace: Full group path, e.g. 'g1/sg1/sg2/sg3' ('' allowed -
            returns True, nothing to ensure).
        cache: Dict path->group_id shared across calls so each level is
            checked/created at most once per run.
        log: Logger.

    Returns:
        True if the namespace exists (or was created), False if a level
        could not be created (recorded by the caller as a failure).
    """
    if not namespace:
        return True
    parent_id = None
    current = ""
    for segment in namespace.split("/"):
        current = f"{current}/{segment}" if current else segment
        if current in cache:
            parent_id = cache[current]
            continue
        resp = dst_session.get(
            f"{dest_url}/api/v4/groups/{encode_path(current)}",
            params={"with_projects": False}, timeout=60)
        if resp.status_code == 200:
            parent_id = resp.json()["id"]
            cache[current] = parent_id
            continue
        if resp.status_code != 404:
            log.error("Namespace check failed for '%s': HTTP %s",
                      current, resp.status_code)
            return False
        payload = {"name": segment, "path": segment, "visibility": "private"}
        if parent_id is not None:
            payload["parent_id"] = parent_id
        create = dst_session.post(f"{dest_url}/api/v4/groups",
                                  json=payload, timeout=60)
        if create.status_code not in (200, 201):
            log.error("Could not create group '%s': HTTP %s %s",
                      current, create.status_code, create.text[:300])
            return False
        parent_id = create.json()["id"]
        cache[current] = parent_id
        log.info("  created missing destination group: %s (id=%s)",
                 current, parent_id)
    return True


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
    parser.add_argument("--skip-existing", action="store_true",
                        help="Before transferring, check the DESTINATION for each "
                             "pending path and skip any project that already "
                             "exists there (marking it finished in the state "
                             "file). Protects against duplicate imports when the "
                             "state file is missing/stale or projects were "
                             "migrated outside this script. Costs one GET per "
                             "pending project.")
    args = parser.parse_args()

    cfg = load_config(args.config)
    log = setup_logging(os.path.join(cfg["reports_dir"], "transfer.log"))
    failures = FailureReport(cfg["reports_dir"], "transfer_failures.csv")

    src = make_session(cfg["source"]["token"])
    dst = make_session(cfg["destination"]["token"])

    # ---- enumerate -------------------------------------------------------
    if args.projects_file:
        with open(args.projects_file, encoding="utf-8") as fh:
            wanted = [ln.strip() for ln in fh if ln.strip()
                      and not ln.strip().startswith("#")]
        projects = []
        for p in wanted:
            kind = classify_path(src, cfg["source"]["url"], p)
            if kind is None:
                log.error("Path '%s' is neither a group nor a project on "
                          "source - skipping (recorded as failure).", p)
                failures.add(PHASE, p, "path not found on source as group or project")
                continue
            if kind == "group":
                log.warning("Path '%s' is a SUBGROUP: it will be imported as a "
                            "group_entity including ALL nested projects in one "
                            "entity - batch throttling does not apply inside it.", p)
            projects.append({"path_with_namespace": p, "entity_type": kind})
        log.info("Loaded %d valid paths from %s", len(projects), args.projects_file)
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

    # ---- destination existence check (--skip-existing) --------------------
    # The bulk import API is NOT idempotent: re-importing an existing path
    # creates a duplicate project. This optional pass asks the destination
    # whether each pending PROJECT path already exists and, if so, marks it
    # finished instead of re-submitting it. (Group entities are not skipped
    # this way: an existing group may legitimately need its projects.)
    if args.skip_existing and pending:
        log.info("Checking destination for %d pending paths (--skip-existing) ...",
                 len(pending))
        still_pending = []
        for n, p in enumerate(pending, 1):
            path = p["path_with_namespace"]
            if p.get("entity_type") == "group":
                still_pending.append(p)
                continue
            resp = dst.get(
                f"{cfg['destination']['url']}/api/v4/projects/{encode_path(path)}",
                timeout=60)
            if resp.status_code == 200:
                state[path] = {"status": "finished",
                               "note": "already existed on destination",
                               "updated_at": utc_now()}
                log.info("  [%d/%d] exists on destination, skipping: %s",
                         n, len(pending), path)
            else:
                still_pending.append(p)
        save_state(cfg["state_file"], state)
        log.info("%d skipped (already on destination), %d remain to transfer.",
                 len(pending) - len(still_pending), len(still_pending))
        pending = still_pending

    batch_size = cfg["batch_size"]
    batches = [pending[i:i + batch_size] for i in range(0, len(pending), batch_size)]

    if args.dry_run:
        for i, b in enumerate(batches, 1):
            log.info("Batch %d/%d: %s", i, len(batches),
                     ", ".join(p["path_with_namespace"] for p in b))
        log.info("Dry run complete - nothing submitted.")
        return

    # ---- transfer loop ---------------------------------------------------
    ns_cache: dict = {}  # destination group path -> group id (shared, per run)
    for i, batch in enumerate(batches, 1):
        log.info("=== Batch %d/%d (%d projects) at %s ===",
                 i, len(batches), len(batch), utc_now())

        # The bulk import API never creates destination namespaces for
        # project entities - make sure each group chain exists first.
        ready = []
        for p in batch:
            namespace = p["path_with_namespace"].rpartition("/")[0]
            if ensure_namespace(dst, cfg["destination"]["url"], namespace,
                                ns_cache, log):
                ready.append(p)
            else:
                failures.add(PHASE, p["path_with_namespace"],
                             f"destination namespace '{namespace}' missing and "
                             f"could not be created (see transfer.log)")
                state[p["path_with_namespace"]] = {"status": "failed",
                                                   "updated_at": utc_now()}
        if not ready:
            save_state(cfg["state_file"], state)
            continue
        batch = ready

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
