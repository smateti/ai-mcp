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
