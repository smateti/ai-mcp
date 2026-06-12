# GitLab Migration Toolkit — all files in one document

Copy each section into a file with the name shown in the heading.

## gitlab_common.py

```python
"""
gitlab_common.py
================
Shared utilities for the GitLab server-to-server migration toolkit.

This module is imported by:
    1. transfer_repos.py  - throttled bulk import (direct transfer) of projects
    2. verify_sync.py     - source vs. target repository comparison
    3. resync_repos.py    - git-level re-sync of out-of-sync repositories

Configuration
-------------
All scripts read a single JSON config file (default: ./migration_config.json).
Example:

    {
      "source": {
        "url": "https://gitlab-old.example.com",
        "token": "glpat-source-XXXX"
      },
      "destination": {
        "url": "https://gitlab-new.example.com",
        "token": "glpat-dest-XXXX"
      },
      "top_level_group": "myorg",
      "batch_size": 10,
      "sleep_between_batches_seconds": 300,
      "poll_interval_seconds": 30,
      "batch_timeout_seconds": 3600,
      "state_file": "transfer_state.json",
      "reports_dir": "reports"
    }

Tokens:
    * Destination token: needs `api` scope; the user must be allowed to
      create groups/projects under the destination namespace.
    * Source token: needs `api` + `read_repository` scope on everything
      being migrated. Both are sent to the *destination* instance, which
      pulls from the source (that is how direct transfer works).

Design notes
------------
* Synchronous `requests` only - easy to read, easy to debug, no async.
* Every API call goes through one Session with automatic retry/backoff
  on 429/5xx so a brief hiccup never kills a 10k-repo run.
* Pagination uses GitLab's `X-Next-Page` header (keyset not required at
  per_page=100 for this workload).
"""

import csv
import json
import logging
import os
import sys
import time
import urllib.parse
from datetime import datetime, timezone

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

LOG_FORMAT = "%(asctime)s %(levelname)-7s %(message)s"


def setup_logging(log_file: str) -> logging.Logger:
    """Configure logging to both console and a file.

    Args:
        log_file: Path of the log file (appended, never truncated, so a
            resumed run keeps one continuous history).

    Returns:
        The root logger, configured.
    """
    logging.basicConfig(
        level=logging.INFO,
        format=LOG_FORMAT,
        handlers=[
            logging.StreamHandler(sys.stdout),
            logging.FileHandler(log_file, encoding="utf-8"),
        ],
    )
    return logging.getLogger()


def load_config(path: str = "migration_config.json") -> dict:
    """Load and minimally validate the shared JSON configuration file.

    Args:
        path: Path to the JSON config file.

    Returns:
        Parsed configuration dictionary with defaults filled in.

    Raises:
        SystemExit: if the file is missing or required keys are absent.
    """
    if not os.path.exists(path):
        sys.exit(f"Config file not found: {path} - see gitlab_common.py docstring for format.")
    with open(path, "r", encoding="utf-8") as fh:
        cfg = json.load(fh)

    for side in ("source", "destination"):
        if side not in cfg or "url" not in cfg[side] or "token" not in cfg[side]:
            sys.exit(f"Config error: '{side}.url' and '{side}.token' are required.")
        cfg[side]["url"] = cfg[side]["url"].rstrip("/")

    cfg.setdefault("batch_size", 10)
    cfg.setdefault("sleep_between_batches_seconds", 300)
    cfg.setdefault("poll_interval_seconds", 30)
    cfg.setdefault("batch_timeout_seconds", 3600)
    cfg.setdefault("state_file", "transfer_state.json")
    cfg.setdefault("reports_dir", "reports")
    os.makedirs(cfg["reports_dir"], exist_ok=True)
    return cfg


def make_session(token: str) -> requests.Session:
    """Build a requests Session with the PRIVATE-TOKEN header and a
    retry policy (5 attempts, exponential backoff) for 429 and 5xx.

    Args:
        token: GitLab personal/group access token.

    Returns:
        Configured requests.Session.
    """
    session = requests.Session()
    session.headers.update({"PRIVATE-TOKEN": token})
    retry = Retry(
        total=5,
        backoff_factor=2,  # 2s, 4s, 8s, 16s, 32s
        status_forcelist=(429, 500, 502, 503, 504),
        allowed_methods=("GET", "POST", "DELETE"),
        respect_retry_after_header=True,
    )
    adapter = HTTPAdapter(max_retries=retry)
    session.mount("https://", adapter)
    session.mount("http://", adapter)
    return session


def api_get_paginated(session: requests.Session, base_url: str, path: str,
                      params: dict | None = None) -> list:
    """GET every page of a paginated GitLab API collection.

    Follows the `X-Next-Page` response header until exhausted.

    Args:
        session: Session created by make_session().
        base_url: Instance base URL, e.g. https://gitlab.example.com
        path: API path beginning with '/', e.g. '/api/v4/projects'.
        params: Extra query parameters (per_page/page are managed here).

    Returns:
        Concatenated list of all JSON items across pages.

    Raises:
        requests.HTTPError: on a non-2xx response after retries.
    """
    items, page = [], "1"
    params = dict(params or {})
    params["per_page"] = 100
    while page:
        params["page"] = page
        resp = session.get(f"{base_url}{path}", params=params, timeout=60)
        resp.raise_for_status()
        items.extend(resp.json())
        page = resp.headers.get("X-Next-Page") or None
    return items


def encode_path(full_path: str) -> str:
    """URL-encode a project/group full path for use as an API id,
    e.g. 'group/sub/repo' -> 'group%2Fsub%2Frepo'."""
    return urllib.parse.quote(full_path, safe="")


def list_all_projects(session: requests.Session, base_url: str,
                      top_level_group: str | None) -> list[dict]:
    """Enumerate every project to migrate from the source instance.

    If `top_level_group` is set, lists that group's projects including all
    subgroups (the normal case). If it is None/empty, lists every project
    on the instance (requires an admin token).

    Args:
        session: Source-instance session.
        base_url: Source instance base URL.
        top_level_group: Full path of the top-level group, or None.

    Returns:
        List of dicts: {id, path_with_namespace, name, archived,
        default_branch}.
    """
    if top_level_group:
        path = f"/api/v4/groups/{encode_path(top_level_group)}/projects"
        params = {"include_subgroups": True, "archived": False, "simple": False,
                  "order_by": "path", "sort": "asc"}
    else:
        path = "/api/v4/projects"
        params = {"archived": False, "simple": False, "order_by": "path", "sort": "asc"}
    raw = api_get_paginated(session, base_url, path, params)
    return [
        {
            "id": p["id"],
            "path_with_namespace": p["path_with_namespace"],
            "name": p["name"],
            "default_branch": p.get("default_branch"),
            "archived": p.get("archived", False),
        }
        for p in raw
    ]


def utc_now() -> str:
    """Current UTC timestamp in ISO-8601, used in all reports."""
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class FailureReport:
    """Append-only CSV failure report shared by all three scripts.

    Every phase writes failures in the same shape so they can be
    concatenated, grepped, or loaded into a spreadsheet:

        timestamp, phase, project_path, reason

    The file is opened in append mode and flushed per row, so even if a
    script dies mid-run, everything reported so far is on disk.
    """

    HEADER = ["timestamp", "phase", "project_path", "reason"]

    def __init__(self, reports_dir: str, filename: str):
        """Create/open the report file, writing the header if new.

        Args:
            reports_dir: Directory for reports (created by load_config).
            filename: e.g. 'transfer_failures.csv'.
        """
        self.path = os.path.join(reports_dir, filename)
        new_file = not os.path.exists(self.path)
        self._fh = open(self.path, "a", newline="", encoding="utf-8")
        self._writer = csv.writer(self._fh)
        if new_file:
            self._writer.writerow(self.HEADER)
            self._fh.flush()

    def add(self, phase: str, project_path: str, reason: str) -> None:
        """Record one failure row and flush immediately."""
        self._writer.writerow([utc_now(), phase, project_path, reason])
        self._fh.flush()

    def close(self) -> None:
        self._fh.close()
```

## transfer_repos.py

```python
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
```

## verify_sync.py

```python
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
```

## resync_repos.py

```python
#!/usr/bin/env python3
"""
resync_repos.py  (Phase 3 - re-sync repositories that drifted)
==============================================================
Brings out-of-sync TARGET repositories back in line with SOURCE.

Why git-level sync (and not the bulk import API again)?
-------------------------------------------------------
GitLab's direct transfer / bulk import API performs a one-time COPY. It
has no incremental mode: pointing it at an already-imported path makes a
duplicate copy instead of updating the existing project. So for drift
that happened after the initial migration (developers kept pushing to
the old server), this script syncs at the git level:

    git clone --mirror   <source repo>     # all refs, bare
    git push  --mirror   <target repo>     # make target refs identical

`push --mirror` force-updates every branch/tag on the target and deletes
refs that no longer exist on the source - afterwards the two repos are
ref-for-ref identical, which is exactly what verify_sync.py checks.

What this does NOT sync: issues, merge requests, wiki, CI variables -
git data only. If a project also needs its metadata refreshed, use
`--reimport`, which DELETES the target project and re-runs a fresh bulk
import for it (full copy again). Use that mode deliberately: deletion is
destructive to anything created on the target after migration.

Protected branches
------------------
`push --mirror` is a force-push, which protected branches on the target
reject. By default this script temporarily lifts target-side branch
protections before pushing and RESTORES them afterwards (records the
existing protection rules first). Disable with --keep-protections if
your policy forbids touching them (those repos will then likely fail
and be reported).

Throttling, input, failures
---------------------------
* Input: --projects-file, normally the out_of_sync_<ts>.txt produced by
  verify_sync.py (one path_with_namespace per line).
* Processes one repo at a time with a configurable --sleep between
  repos (default 10s) - clones put real I/O load on the source.
* Every failed repo is appended to reports/resync_failures.csv and the
  run continues.
* Tokens are passed to git via a one-shot credential helper on the
  command line, NOT embedded in remote URLs, so they don't leak into
  `git remote -v`, process listings of the URL, or on-disk config.

Usage
-----
    python resync_repos.py --projects-file reports/out_of_sync_XXXX.txt
    python resync_repos.py --projects-file f.txt --reimport
    python resync_repos.py --projects-file f.txt --sleep 30 --keep-protections
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import time

import requests

from gitlab_common import (FailureReport, encode_path, load_config,
                           make_session, setup_logging, utc_now)

PHASE = "resync"


def run_git(args: list[str], token: str, cwd: str | None = None) -> subprocess.CompletedProcess:
    """Run a git command with token auth injected via a credential helper.

    A one-shot `credential.helper` is configured on the command line so
    the token never appears in the remote URL or stored config.

    Args:
        args: git arguments, e.g. ["clone", "--mirror", url, dir].
        token: GitLab token used as the password (username 'oauth2').
        cwd: Working directory for the command.

    Returns:
        CompletedProcess with captured stdout/stderr (check=False; the
        caller inspects returncode).
    """
    helper = ("!f() { echo username=oauth2; echo password=" + token + "; }; f")
    cmd = ["git", "-c", f"credential.helper={helper}",
           "-c", "credential.useHttpPath=true"] + args
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                          timeout=3600, check=False)


def get_project(session, base_url: str, path: str) -> dict | None:
    """GET a project by full path; returns its JSON or None on 404."""
    resp = session.get(f"{base_url}/api/v4/projects/{encode_path(path)}",
                       timeout=60)
    if resp.status_code == 404:
        return None
    resp.raise_for_status()
    return resp.json()


def lift_protections(session, base_url: str, project_id: int) -> list[dict]:
    """Remove all protected-branch rules from a target project, returning
    the rules so they can be restored after the mirror push.

    Args:
        session: Destination session.
        base_url: Destination base URL.
        project_id: Numeric project id on the destination.

    Returns:
        The list of protection rules that were removed (possibly empty).
    """
    resp = session.get(
        f"{base_url}/api/v4/projects/{project_id}/protected_branches",
        params={"per_page": 100}, timeout=60)
    resp.raise_for_status()
    rules = resp.json()
    for rule in rules:
        session.delete(
            f"{base_url}/api/v4/projects/{project_id}/protected_branches/"
            f"{encode_path(rule['name'])}", timeout=60)
    return rules


def restore_protections(session, base_url: str, project_id: int,
                        rules: list[dict], log) -> None:
    """Re-create protected-branch rules removed by lift_protections().

    Only the core attributes (name + push/merge access levels) are
    restored; exotic per-user rules should be re-checked manually.
    """
    for rule in rules:
        payload = {"name": rule["name"]}
        if rule.get("push_access_levels"):
            payload["push_access_level"] = rule["push_access_levels"][0]["access_level"]
        if rule.get("merge_access_levels"):
            payload["merge_access_level"] = rule["merge_access_levels"][0]["access_level"]
        resp = session.post(
            f"{base_url}/api/v4/projects/{project_id}/protected_branches",
            json=payload, timeout=60)
        if resp.status_code not in (200, 201):
            log.warning("    could not restore protection on '%s': HTTP %s",
                        rule["name"], resp.status_code)


def mirror_sync(path: str, cfg: dict, dst_session, keep_protections: bool,
                log) -> None:
    """Mirror one repository from source to target.

    Steps: verify target exists -> optionally lift protections ->
    `git clone --mirror` from source -> `git push --mirror` to target ->
    restore protections -> clean up temp clone.

    Args:
        path: path_with_namespace, identical on both instances.
        cfg: Loaded configuration.
        dst_session: Destination API session.
        keep_protections: If True, never touch protected-branch rules.
        log: Logger.

    Raises:
        RuntimeError: with a concise reason on any failed step (caller
            records it in the failure report).
    """
    target = get_project(dst_session, cfg["destination"]["url"], path)
    if target is None:
        raise RuntimeError("target project missing - run transfer_repos.py "
                           "with --projects-file for it instead")

    src_http = f"{cfg['source']['url']}/{path}.git"
    dst_http = f"{cfg['destination']['url']}/{path}.git"
    tmp = tempfile.mkdtemp(prefix="resync_")
    clone_dir = os.path.join(tmp, "repo.git")
    saved_rules: list[dict] = []
    try:
        res = run_git(["clone", "--mirror", src_http, clone_dir],
                      cfg["source"]["token"])
        if res.returncode != 0:
            raise RuntimeError(f"mirror clone failed: {res.stderr.strip()[:300]}")

        if not keep_protections:
            saved_rules = lift_protections(dst_session,
                                           cfg["destination"]["url"],
                                           target["id"])
            if saved_rules:
                log.info("    lifted %d protected-branch rule(s)", len(saved_rules))

        res = run_git(["push", "--mirror", dst_http],
                      cfg["destination"]["token"], cwd=clone_dir)
        if res.returncode != 0:
            raise RuntimeError(f"mirror push failed: {res.stderr.strip()[:300]}")
    finally:
        if saved_rules:
            restore_protections(dst_session, cfg["destination"]["url"],
                                target["id"], saved_rules, log)
        shutil.rmtree(tmp, ignore_errors=True)


def reimport(path: str, cfg: dict, dst_session, log) -> None:
    """DESTRUCTIVE full refresh: delete the target project, then run a
    fresh single-project bulk import (direct transfer) for it.

    Restores git data AND metadata (issues, MRs, ...), at the cost of
    destroying anything created on the target since migration. The bulk
    import is submitted and polled to completion (simple inline poll;
    these are single-project imports).

    Raises:
        RuntimeError: on delete or import failure.
    """
    target = get_project(dst_session, cfg["destination"]["url"], path)
    if target is not None:
        resp = dst_session.delete(
            f"{cfg['destination']['url']}/api/v4/projects/{target['id']}",
            timeout=60)
        if resp.status_code not in (200, 202, 204):
            raise RuntimeError(f"delete failed: HTTP {resp.status_code}")
        log.info("    deleted target project (id=%s); waiting for removal ...",
                 target["id"])
        # Deletion is asynchronous; wait until the path is actually free.
        for _ in range(30):
            if get_project(dst_session, cfg["destination"]["url"], path) is None:
                break
            time.sleep(10)
        else:
            raise RuntimeError("target project still present after delete "
                               "(delayed-deletion setting?) - reimport aborted")

    namespace, _, slug = path.rpartition("/")
    payload = {
        "configuration": {"url": cfg["source"]["url"],
                          "access_token": cfg["source"]["token"]},
        "entities": [{"source_type": "project_entity",
                      "source_full_path": path,
                      "destination_slug": slug,
                      "destination_namespace": namespace}],
    }
    resp = dst_session.post(f"{cfg['destination']['url']}/api/v4/bulk_imports",
                            json=payload, timeout=120)
    if resp.status_code not in (200, 201):
        raise RuntimeError(f"bulk import submit failed: HTTP {resp.status_code} "
                           f"{resp.text[:200]}")
    bulk_id = resp.json()["id"]
    deadline = time.time() + cfg["batch_timeout_seconds"]
    while time.time() < deadline:
        st = dst_session.get(
            f"{cfg['destination']['url']}/api/v4/bulk_imports/{bulk_id}",
            timeout=60).json()["status"]
        if st == "finished":
            return
        if st in ("failed", "timeout"):
            raise RuntimeError(f"re-import ended with status '{st}'")
        time.sleep(cfg["poll_interval_seconds"])
    raise RuntimeError("re-import polling timed out")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="migration_config.json")
    parser.add_argument("--projects-file", required=True,
                        help="File with one path_with_namespace per line "
                             "(use out_of_sync_<ts>.txt from verify_sync.py).")
    parser.add_argument("--reimport", action="store_true",
                        help="DESTRUCTIVE: delete target project and re-run a "
                             "fresh direct transfer (refreshes metadata too).")
    parser.add_argument("--keep-protections", action="store_true",
                        help="Never modify protected branches on target "
                             "(mirror pushes to protected refs will fail).")
    parser.add_argument("--sleep", type=int, default=10,
                        help="Seconds to sleep between repos (default 10).")
    args = parser.parse_args()

    cfg = load_config(args.config)
    log = setup_logging(os.path.join(cfg["reports_dir"], "resync.log"))
    failures = FailureReport(cfg["reports_dir"], "resync_failures.csv")
    dst = make_session(cfg["destination"]["token"])

    with open(args.projects_file, encoding="utf-8") as fh:
        paths = [ln.strip() for ln in fh if ln.strip()]
    mode = "reimport (delete + fresh direct transfer)" if args.reimport \
        else "git mirror sync"
    log.info("Re-syncing %d repositories using %s ...", len(paths), mode)

    ok = 0
    for n, path in enumerate(paths, 1):
        log.info("[%d/%d] %s", n, len(paths), path)
        try:
            if args.reimport:
                reimport(path, cfg, dst, log)
            else:
                mirror_sync(path, cfg, dst, args.keep_protections, log)
            ok += 1
            log.info("    OK")
        except (RuntimeError, requests.RequestException,
                subprocess.TimeoutExpired) as exc:
            log.error("    FAIL: %s", exc)
            failures.add(PHASE, path, str(exc)[:500])
        if n < len(paths):
            time.sleep(args.sleep)

    log.info("Re-sync complete at %s: %d/%d succeeded. Failures (if any) in %s",
             utc_now(), ok, len(paths), failures.path)
    log.info("Tip: re-run verify_sync.py --projects-file %s to confirm.",
             args.projects_file)
    failures.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\nInterrupted - failures recorded so far are on disk.")
```

## migration_config.json.example

```json
{
  "source": {
    "url": "https://gitlab-old.example.com",
    "token": "glpat-SOURCE-TOKEN"
  },
  "destination": {
    "url": "https://gitlab-new.example.com",
    "token": "glpat-DEST-TOKEN"
  },
  "top_level_group": "myorg",
  "batch_size": 10,
  "sleep_between_batches_seconds": 300,
  "poll_interval_seconds": 30,
  "batch_timeout_seconds": 3600,
  "state_file": "transfer_state.json",
  "reports_dir": "reports"
}
```

## README.md

# GitLab Server-to-Server Migration Toolkit (~10k repos)

Three throttled, resumable Python scripts built around GitLab's **direct
transfer (bulk import) API**, plus git-level re-sync for drift — because the
bulk import API is one-shot: it has **no incremental/delta mode**, and
re-importing an existing path creates a duplicate copy instead of updating it.

## Workflow

```
Day 0          transfer_repos.py     batches of 10, sleep between batches,
                                     resumable via transfer_state.json
Week 1-2       (teams verify on the new server; old server still receives pushes)
Cutover prep   verify_sync.py        compares every repo's branch/tag SHAs
                                     -> in_sync / out_of_sync / missing lists
Cutover        resync_repos.py       git clone --mirror + push --mirror for
                                     out_of_sync repos (git data only), or
                                     --reimport for a destructive full refresh
Confirm        verify_sync.py --projects-file reports/out_of_sync_<ts>.txt
```

Failures in **every** phase append to `reports/*_failures.csv`
(`timestamp,phase,project_path,reason`) and the run continues.

## Files

| File                | Purpose                                                        |
|---------------------|----------------------------------------------------------------|
| `gitlab_common.py`  | config, API session w/ retry+backoff, pagination, reports      |
| `transfer_repos.py` | phase 1: throttled bulk import, batch+sleep, resume state      |
| `verify_sync.py`    | phase 2: ref-level comparison, produces actionable lists       |
| `resync_repos.py`   | phase 3: mirror-sync drifted repos (or delete+reimport)        |
| `migration_config.json` | shared config (URLs, tokens, throttle settings)            |

## Prerequisites

1. **Direct transfer enabled on both instances**: Admin Area → Settings →
   General → *Import and export settings* → "Migrate groups and projects by
   direct transfer". Both instances should be on recent, compatible versions.
2. **Tokens**: destination token (`api` scope, rights to create projects in
   the destination namespaces); source token (`api`, `read_repository`).
3. **Group tree exists on destination**: projects are recreated at the *same*
   full path. Either run one group-level direct transfer first (with
   `"migrate_projects": false`) to copy the empty group hierarchy, or
   pre-create the groups.
4. `pip install requests` (only external dependency) and `git` on PATH
   (resync only).

## Quick start

```bash
cp migration_config.json.example migration_config.json   # edit URLs/tokens
python transfer_repos.py --dry-run        # review the batch plan
python transfer_repos.py                  # phase 1 (Ctrl-C safe; re-run resumes)
# ... 1-2 weeks later ...
python verify_sync.py                     # phase 2
python resync_repos.py --projects-file reports/out_of_sync_<ts>.txt   # phase 3
python transfer_repos.py --projects-file reports/missing_<ts>.txt     # stragglers
```

## Throttling knobs (protecting the source server)

* `batch_size` (default **10**) — projects per bulk-import request; the script
  waits for the whole batch to finish before submitting the next, so at most
  one batch is exporting on the source at any time.
* `sleep_between_batches_seconds` (default **300**) — cool-down between batches.
* `resync_repos.py --sleep` (default **10s**) — pause between mirror clones.
* Server-side, you can additionally cap `bulk_import_concurrent_pipeline_batch_limit`
  and tune Sidekiq on both instances per GitLab's import documentation.

## Important caveats

* **Drift re-sync is git-only.** `push --mirror` makes branches/tags identical
  but does not touch issues, MRs, wikis, or CI variables. For full metadata
  refresh use `resync_repos.py --reimport`, which **deletes** the target
  project first — destructive to anything created on the new server since
  migration.
* **Protected branches**: mirror pushes are force-pushes; the script lifts and
  restores target-side protections by default (`--keep-protections` to opt out).
* **Delayed deletion**: if the destination has delayed project deletion
  enabled, `--reimport` waits for the path to free up and aborts if it doesn't.
* **What direct transfer migrates** (issues, MRs, pipelines history, etc.)
  varies by GitLab version — check the "migrated items" matrix in the docs for
  your versions and spot-check a pilot group first.
* **Pilot first**: run the whole three-phase cycle on one small subgroup
  before pointing it at all 10k repos.
