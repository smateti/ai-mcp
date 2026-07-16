#!/usr/bin/env python3
"""
delete_projects.py  - Delete target projects from a path list
=============================================================
Reads a file of project paths (one path_with_namespace per line) and
deletes each from the DESTINATION GitLab instance.  Designed to consume
the out_of_sync_<ts>.txt or missing_<ts>.txt files produced by
verify_sync.py as the first step of a two-step resync workflow:

    Step 1: python delete_projects.py --projects-file reports/out_of_sync_XXXX.txt
    Step 2: python transfer_repos.py  --projects-file reports/out_of_sync_XXXX.txt

Deletion is asynchronous on GitLab: the API returns 202 and the project
enters a "pending delete" state.  This script polls until the project is
actually gone (or times out after --delete-timeout seconds per project).

Usage
-----
    python delete_projects.py --projects-file reports/out_of_sync_XXXX.txt
    python delete_projects.py --projects-file paths.txt --dry-run
    python delete_projects.py --projects-file paths.txt --sleep 20
"""

import argparse
import os
import sys
import time

import requests

from gitlab_common import (FailureReport, encode_path, load_config,
                           make_session, setup_logging, utc_now)

PHASE = "delete"


def get_project(session, base_url: str, path: str) -> dict | None:
    """GET a project by full path; returns its JSON or None on 404."""
    resp = session.get(f"{base_url}/api/v4/projects/{encode_path(path)}",
                       timeout=60)
    if resp.status_code == 404:
        return None
    resp.raise_for_status()
    return resp.json()


def delete_project(session, base_url: str, path: str,
                   delete_timeout: int, log) -> None:
    """Delete a single project from the destination and wait for removal.

    Args:
        session: Destination API session.
        base_url: Destination base URL.
        path: Full path_with_namespace of the project.
        delete_timeout: Max seconds to wait for deletion to complete.
        log: Logger.

    Raises:
        RuntimeError: if the project cannot be deleted or does not
            disappear within the timeout.
    """
    project = get_project(session, base_url, path)
    if project is None:
        log.info("    already absent on destination, nothing to delete")
        return

    project_id = project["id"]
    resp = session.delete(
        f"{base_url}/api/v4/projects/{project_id}", timeout=60)
    if resp.status_code not in (200, 202, 204):
        raise RuntimeError(f"delete API returned HTTP {resp.status_code}: "
                           f"{resp.text[:200]}")

    log.info("    delete accepted (id=%s), waiting for removal ...",
             project_id)
    deadline = time.time() + delete_timeout
    while time.time() < deadline:
        if get_project(session, base_url, path) is None:
            return
        time.sleep(10)
    raise RuntimeError(f"project still present after {delete_timeout}s "
                       "(delayed-deletion setting?)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="migration_config.json")
    parser.add_argument("--projects-file", required=True,
                        help="File with one path_with_namespace per line "
                             "(e.g. out_of_sync_<ts>.txt from verify_sync.py).")
    parser.add_argument("--dry-run", action="store_true",
                        help="List projects that would be deleted without "
                             "actually deleting anything.")
    parser.add_argument("--sleep", type=int, default=10,
                        help="Seconds to sleep between deletions (default 10).")
    parser.add_argument("--delete-timeout", type=int, default=300,
                        help="Max seconds to wait per project for GitLab to "
                             "complete the async deletion (default 300).")
    args = parser.parse_args()

    cfg = load_config(args.config)
    log = setup_logging(os.path.join(cfg["reports_dir"], "delete.log"))
    failures = FailureReport(cfg["reports_dir"], "delete_failures.csv")
    dst = make_session(cfg["destination"]["token"])
    dst_url = cfg["destination"]["url"]

    with open(args.projects_file, encoding="utf-8") as fh:
        paths = [ln.strip() for ln in fh if ln.strip()]
    log.info("Loaded %d project paths from %s", len(paths), args.projects_file)

    if args.dry_run:
        log.info("DRY RUN — no projects will be deleted")
        for n, path in enumerate(paths, 1):
            project = get_project(dst, dst_url, path)
            status = f"exists (id={project['id']})" if project else "not found"
            log.info("[%d/%d] %s — %s", n, len(paths), path, status)
        log.info("Dry run complete. %d projects would be processed.", len(paths))
        failures.close()
        return

    ok = 0
    for n, path in enumerate(paths, 1):
        log.info("[%d/%d] %s", n, len(paths), path)
        try:
            delete_project(dst, dst_url, path, args.delete_timeout, log)
            ok += 1
            log.info("    OK")
        except (RuntimeError, requests.RequestException) as exc:
            log.error("    FAIL: %s", exc)
            failures.add(PHASE, path, str(exc)[:500])
        if n < len(paths):
            time.sleep(args.sleep)

    log.info("Deletion complete at %s: %d/%d succeeded. Failures (if any) in %s",
             utc_now(), ok, len(paths), failures.path)
    log.info("Next step: re-import with  python transfer_repos.py "
             "--projects-file %s", args.projects_file)
    failures.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\nInterrupted - failures recorded so far are on disk.")
