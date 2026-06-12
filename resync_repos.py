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
