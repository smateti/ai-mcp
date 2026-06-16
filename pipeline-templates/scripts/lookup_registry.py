#!/usr/bin/env python3
"""
lookup_registry.py — Reads APP_NAME from the environment, looks up application
metadata in registry.json, and writes:
  - app-metadata.json  (full metadata for downstream jobs)
  - build.properties   (key=value pairs consumed by Maven / ConfigMaps)

Exit codes:
  0  success
  1  APP_NAME not set or not found in registry
"""

import json
import os
import sys
from pathlib import Path


def main():
    app_name = os.environ.get("APP_NAME", "").strip()
    environment = os.environ.get("ENVIRONMENT", "dev").strip()

    if not app_name:
        print("ERROR: APP_NAME environment variable is not set or empty.", file=sys.stderr)
        sys.exit(1)

    # Locate registry.json relative to this script (lives in the same repo)
    script_dir = Path(__file__).resolve().parent
    registry_path = script_dir.parent / "registry.json"

    if not registry_path.exists():
        print(f"ERROR: Registry file not found at {registry_path}", file=sys.stderr)
        sys.exit(1)

    with open(registry_path, "r", encoding="utf-8") as f:
        registry = json.load(f)

    apps = registry.get("applications", {})
    if app_name not in apps:
        print(f"ERROR: Application '{app_name}' not found in registry.", file=sys.stderr)
        print(f"Available applications: {', '.join(apps.keys())}", file=sys.stderr)
        sys.exit(1)

    app_meta = apps[app_name]
    print(f"Found application '{app_name}': {app_meta['description']}")
    print(f"  Type       : {app_meta['type']}")
    print(f"  Services   : {len(app_meta['services'])}")
    print(f"  Environment: {environment}")

    # ── Write app-metadata.json ──────────────────────────────────────────
    # Contains the full metadata block plus the resolved environment.
    output_meta = {
        "appName": app_name,
        "environment": environment,
        **app_meta,
    }
    meta_path = Path("app-metadata.json")
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(output_meta, f, indent=2)
    print(f"Wrote {meta_path}")

    # ── Write build.properties ───────────────────────────────────────────
    # Flat key=value file usable by Maven resource filtering, shell `source`,
    # and Kubernetes ConfigMaps.
    first_service = app_meta["services"][0]
    props = {
        "app.name": app_name,
        "app.description": app_meta["description"],
        "app.environment": environment,
        "app.version": first_service["version"],
        "service.groupId": first_service["groupId"],
        "service.artifactId": first_service["artifactId"],
        "service.gitRepo": first_service["gitRepo"],
        "service.gitRef": first_service["gitRef"],
        "build.timestamp": os.environ.get("CI_PIPELINE_CREATED_AT", "local"),
        "build.pipelineId": os.environ.get("CI_PIPELINE_ID", "0"),
        "build.commitSha": os.environ.get("CI_COMMIT_SHA", "local"),
    }
    props_path = Path("build.properties")
    with open(props_path, "w", encoding="utf-8") as f:
        for key, value in props.items():
            f.write(f"{key}={value}\n")
    print(f"Wrote {props_path}")


if __name__ == "__main__":
    main()
