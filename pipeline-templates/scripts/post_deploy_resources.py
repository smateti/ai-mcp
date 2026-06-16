#!/usr/bin/env python3
"""
post_deploy_resources.py — Simulates creating post-deployment resources
(message queues, event listeners, etc.) based on app-metadata.json.

In production this would call real APIs (e.g., IBM MQ REST, AMQ Broker).
Here it logs the actions and writes resources-created.json as a receipt.
"""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def main():
    meta_path = Path("app-metadata.json")
    if not meta_path.exists():
        print("ERROR: app-metadata.json not found.", file=sys.stderr)
        sys.exit(1)

    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)

    app_name = meta.get("appName", "unknown")
    environment = meta.get("environment", "dev")
    post_deploy = meta.get("postDeploy", {})
    queues = post_deploy.get("queues", [])
    listeners = post_deploy.get("listeners", [])

    print(f"Post-deploy resource provisioning for {app_name} ({environment})")
    print(f"  Queues to create   : {len(queues)}")
    print(f"  Listeners to create: {len(listeners)}")

    created = {"timestamp": datetime.now(timezone.utc).isoformat(), "appName": app_name, "environment": environment, "resources": []}

    # ── Simulate queue creation ──────────────────────────────────────────
    for queue in queues:
        fqn = f"{environment}.{app_name}.{queue}"
        print(f"  [MOCK] Creating queue: {fqn}")
        created["resources"].append({"type": "queue", "name": fqn, "status": "created"})

    # ── Simulate listener registration ───────────────────────────────────
    for listener in listeners:
        print(f"  [MOCK] Registering listener: {listener}")
        created["resources"].append({"type": "listener", "name": listener, "status": "registered"})

    if not queues and not listeners:
        print("  No post-deploy resources defined; nothing to do.")

    # ── Write receipt ────────────────────────────────────────────────────
    output_path = Path("resources-created.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(created, f, indent=2)
    print(f"Wrote {output_path}")


if __name__ == "__main__":
    main()
