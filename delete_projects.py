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

Freeing the path for re-import
-------------------------------
GitLab's delayed-deletion keeps soft-deleted projects API-visible for up
to 30 days.  A second DELETE on a pending-delete project returns 400, and
setting deletion_adjourned_period=0 is also rejected (must be >= 1).

For the resync workflow the real requirement is not that the project is
permanently deleted — it is that the PATH is free so transfer_repos.py
can re-import at the same path.  This script achieves that reliably on
all GitLab versions using a two-stage strategy:

  Stage 1 — DELETE with ?permanently_delete=true (GitLab 15.11+, admin
             token).  If the project disappears from the API, done.

  Stage 2 — Fallback for older GitLab or non-admin token (also used when
             the project is already in soft-deleted state on entry):
             a. POST /projects/:id/restore  — bring the project back to
                live state so it can be renamed.
             b. PUT  /projects/:id  { "path": "<slug>_deleting_<ts>" }
                — rename the slug; the ORIGINAL path is immediately free.
             c. DELETE /projects/:id  — soft-delete the renamed copy.
                GitLab will permanently purge it after the adjourned
                period; the original path is already available.

No admin application-settings changes are needed.

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


def _poll_until_gone(session, base_url: str, path: str,
                     deadline: float, poll_interval: int = 5) -> bool:
    """Poll GET /projects/:path until it returns 404 or the deadline passes.

    Returns:
        True if the project is gone, False if it is still visible at deadline.
    """
    while time.time() < deadline:
        if get_project(session, base_url, path) is None:
            return True
        time.sleep(poll_interval)
    return get_project(session, base_url, path) is None


def _restore_project(session, base_url: str, project_id: int, log) -> None:
    """Restore a soft-deleted project to live state so it can be renamed.

    A project in pending-deletion state rejects PUT (rename) and DELETE
    with 400; restoring it first returns it to a normal live state.
    """
    resp = session.post(
        f"{base_url}/api/v4/projects/{project_id}/restore", timeout=60)
    if resp.status_code not in (200, 201):
        log.warning("    restore returned HTTP %s (continuing anyway)",
                    resp.status_code)


def _rename_to_free_path(session, base_url: str, project: dict, log) -> bool:
    """Rename the project slug to free its original path for re-import.

    Appends _deleting_<epoch> to the slug so the original path is
    immediately available without waiting for permanent deletion.

    Returns True if the rename succeeded.
    """
    temp_slug = f"{project['path']}_deleting_{int(time.time())}"
    resp = session.put(
        f"{base_url}/api/v4/projects/{project['id']}",
        json={"path": temp_slug},
        timeout=60)
    if resp.status_code in (200, 201):
        log.info("    renamed slug to '%s'; original path is now free",
                 temp_slug)
        return True
    log.warning("    slug rename returned HTTP %s: %s",
                resp.status_code, resp.text[:100])
    return False


def delete_project(session, base_url: str, path: str,
                   delete_timeout: int, log) -> None:
    """Free a project's path on the destination for re-import.

    Stage 1: DELETE ?permanently_delete=true (GitLab 15.11+, admin token).
             Polls for up to 15 s; if the project vanishes, done.

    Stage 2: Fallback when stage 1 leaves the project in soft-deleted
             state, OR when the project was already soft-deleted on entry
             (deletion_adjourned_period=0 is invalid; a second DELETE
             returns 400):
             a. POST /restore  — bring the project back to live state.
             b. PUT  /projects/:id { path: <slug>_deleting_<ts> }
                — rename the slug; the original path is FREE immediately.
             c. DELETE /projects/:id — soft-delete the renamed copy
                (GitLab purges it after the adjourned period; we do not
                need to wait).

    Args:
        session: Destination API session (admin token for stage 1).
        base_url: Destination base URL.
        path: Full path_with_namespace of the project.
        delete_timeout: Max seconds to wait for stage-1 poll.
        log: Logger.

    Raises:
        RuntimeError: if neither stage can free the path.
    """
    project = get_project(session, base_url, path)
    if project is None:
        log.info("    already absent on destination, nothing to delete")
        return

    project_id = project["id"]
    already_soft_deleted = bool(project.get("marked_for_deletion_at"))

    if not already_soft_deleted:
        # --- Stage 1: try permanently_delete=true (GitLab 15.11+) ---
        resp = session.delete(
            f"{base_url}/api/v4/projects/{project_id}",
            params={"permanently_delete": True},
            timeout=60)
        if resp.status_code not in (200, 202, 204):
            raise RuntimeError(f"delete returned HTTP {resp.status_code}: "
                               f"{resp.text[:200]}")
        log.info("    delete issued (id=%s, permanently_delete=true)",
                 project_id)
        time.sleep(2)
        deadline = time.time() + delete_timeout
        if _poll_until_gone(session, base_url, path,
                            min(deadline, time.time() + 15)):
            return  # Stage 1 worked — done.
        log.info("    project still soft-deleted after stage 1; "
                 "falling back to rename strategy ...")
    else:
        log.info("    project already in soft-deleted state (id=%s); "
                 "using rename strategy directly ...", project_id)

    # --- Stage 2: restore → rename → soft-delete ---
    # The original path is freed the moment the rename succeeds.
    # The renamed copy is left for GitLab's scheduled purge.
    _restore_project(session, base_url, project_id, log)

    if not _rename_to_free_path(session, base_url, project, log):
        raise RuntimeError(
            "could not free project path: permanently_delete=true had no "
            "effect and slug rename failed; the path may still be occupied")

    # Soft-delete the now-renamed copy (best-effort; failure is non-fatal
    # because the original path is already freed by the rename).
    resp2 = session.delete(
        f"{base_url}/api/v4/projects/{project_id}", timeout=60)
    if resp2.status_code not in (200, 202, 204, 404):
        log.warning("    soft-delete of renamed copy returned HTTP %s "
                    "(path is still free; ignoring)", resp2.status_code)


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
