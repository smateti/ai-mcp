#!/usr/bin/env python3
"""
generate_helm_values.py — Merges base Helm values with application-level and
environment-level overrides from app-metadata.json, producing values-final.yaml.

Expects:
  - app-metadata.json in the current directory (produced by lookup_registry.py)
  - A base values.yaml path passed as the first CLI argument (or defaulting to
    sample-service/helm/values.yaml)

Writes:
  - values-final.yaml  (merged values consumed by `helm upgrade --install`)
"""

import json
import os
import sys
from pathlib import Path

# PyYAML is available in the runner image; keep the import simple.
try:
    import yaml
except ImportError:
    print("ERROR: PyYAML is required. Install with: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


def deep_merge(base: dict, override: dict) -> dict:
    """Recursively merge *override* into *base*, returning a new dict."""
    merged = dict(base)
    for key, value in override.items():
        if key in merged and isinstance(merged[key], dict) and isinstance(value, dict):
            merged[key] = deep_merge(merged[key], value)
        else:
            merged[key] = value
    return merged


def expand_dotted_keys(flat: dict) -> dict:
    """
    Convert dotted keys like {"resources.limits.memory": "1Gi"} into nested
    dicts: {"resources": {"limits": {"memory": "1Gi"}}}.
    """
    result = {}
    for dotted_key, value in flat.items():
        parts = dotted_key.split(".")
        current = result
        for part in parts[:-1]:
            current = current.setdefault(part, {})
        current[parts[-1]] = value
    return result


def main():
    # ── Load app metadata ────────────────────────────────────────────────
    meta_path = Path("app-metadata.json")
    if not meta_path.exists():
        print("ERROR: app-metadata.json not found. Run lookup_registry.py first.", file=sys.stderr)
        sys.exit(1)

    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)

    environment = meta.get("environment", "dev")
    app_name = meta.get("appName", "unknown")

    # ── Load base values.yaml ────────────────────────────────────────────
    base_values_path = sys.argv[1] if len(sys.argv) > 1 else "sample-service/helm/values.yaml"
    base_values_path = Path(base_values_path)

    if not base_values_path.exists():
        print(f"WARNING: Base values file not found at {base_values_path}; using empty base.")
        base_values = {}
    else:
        with open(base_values_path, "r", encoding="utf-8") as f:
            base_values = yaml.safe_load(f) or {}

    # ── Load environment-specific override file if it exists ─────────────
    env_override_path = base_values_path.parent / f"values-{environment}.yaml"
    if env_override_path.exists():
        print(f"Loading environment override file: {env_override_path}")
        with open(env_override_path, "r", encoding="utf-8") as f:
            env_file_overrides = yaml.safe_load(f) or {}
    else:
        print(f"No environment override file at {env_override_path}; skipping.")
        env_file_overrides = {}

    # ── Apply registry overrides (dotted-key format) ─────────────────────
    helm_section = meta.get("helm", {})
    registry_overrides = helm_section.get("overrides", {}).get(environment, {})
    registry_nested = expand_dotted_keys(registry_overrides)

    # ── Merge: base → env-file → registry ────────────────────────────────
    merged = deep_merge(base_values, env_file_overrides)
    merged = deep_merge(merged, registry_nested)

    # Inject standard labels
    merged.setdefault("labels", {})
    merged["labels"]["app.kubernetes.io/name"] = app_name
    merged["labels"]["app.kubernetes.io/environment"] = environment
    merged["labels"]["app.kubernetes.io/managed-by"] = "gitlab-ci"

    # ── Write values-final.yaml ──────────────────────────────────────────
    output_path = Path("values-final.yaml")
    with open(output_path, "w", encoding="utf-8") as f:
        yaml.dump(merged, f, default_flow_style=False, sort_keys=False)
    print(f"Wrote {output_path} for app={app_name} env={environment}")


if __name__ == "__main__":
    main()
