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
