# GitLab Server-to-Server Migration Toolkit (Spring Boot)

A Spring Boot CLI application for bulk-migrating ~10,000+ repositories between
GitLab instances using the **direct transfer (bulk import) API**, plus git-level
re-sync for drift.

The bulk import API is one-shot: it has **no incremental/delta mode**, and
re-importing an existing path creates a duplicate instead of updating it. This
toolkit handles the full migration lifecycle in three phases.

## Workflow

```
Day 0          transfer        batches of 10, sleep between batches,
                                resumable via transfer_state.json
Week 1-2       (teams verify on the new server; old server still receives pushes)
Cutover prep   verify          compares every repo's branch/tag SHAs
                                -> in_sync / out_of_sync / missing lists
Cutover        resync           git clone --mirror + push --mirror for
                                out_of_sync repos (git data only), or
                                --reimport for a destructive full refresh
Confirm        verify --projects-file reports/out_of_sync_<ts>.txt
```

Failures in **every** phase append to `reports/*_failures.csv`
(`timestamp,phase,project_path,reason`) and the run continues.

## Prerequisites

1. **Java 17+** and **Maven 3.8+**
2. **Direct transfer enabled on both GitLab instances**: Admin Area > Settings >
   General > *Import and export settings* > "Migrate groups and projects by
   direct transfer". Both instances should be on recent, compatible versions.
3. **Tokens**: destination token (`api` scope, rights to create projects in
   the destination namespaces); source token (`api`, `read_repository`).
4. **Group tree exists on destination**: projects are recreated at the same
   full path. Either run one group-level direct transfer first (with
   `"migrate_projects": false`) to copy the empty group hierarchy, or
   pre-create the groups.
5. **git** on PATH (required for the `resync` phase only).

## Build

```bash
mvn clean package -DskipTests
```

Produces `target/gitlab-migration-1.0.0.jar`.

## Configuration

Edit `src/main/resources/application.yml` before building, or override
properties via environment variables or command-line flags at runtime.

```yaml
migration:
  source:
    url: https://gitlab-old.example.com
    token: ${GITLAB_SOURCE_TOKEN}
  destination:
    url: https://gitlab-new.example.com
    token: ${GITLAB_DEST_TOKEN}
  top-level-group: myorg
  batch-size: 10
  sleep-between-batches-seconds: 300
  poll-interval-seconds: 30
  batch-timeout-seconds: 3600
  state-file: transfer_state.json
  reports-dir: reports
  ssl:
    truststore-path: /path/to/custom-truststore.jks
    truststore-password: ${TRUSTSTORE_PASSWORD:changeit}
    truststore-type: JKS   # or PKCS12
```

### Tokens via environment variables (recommended)

```bash
export GITLAB_SOURCE_TOKEN=glpat-xxxx
export GITLAB_DEST_TOKEN=glpat-yyyy
export TRUSTSTORE_PASSWORD=mysecret
```

### Runtime property overrides

Any property can be overridden on the command line:

```bash
java -jar gitlab-migration-1.0.0.jar transfer \
  --migration.batch-size=5 \
  --migration.source.url=https://other-gitlab.example.com
```

## SSL Truststore

For GitLab instances using self-signed or internal CA certificates, configure
a custom JKS or PKCS12 truststore:

```yaml
migration:
  ssl:
    truststore-path: /etc/ssl/gitlab-trust.jks
    truststore-password: changeit
    truststore-type: JKS
```

When `truststore-path` is empty (the default), the JVM's default trust store
is used.

## Usage

```bash
JAR=target/gitlab-migration-1.0.0.jar

# Show available commands
java -jar $JAR --help

# Phase 1: initial bulk import (Ctrl-C safe; re-run resumes)
java -jar $JAR transfer --dry-run                    # review the batch plan
java -jar $JAR transfer                              # run the migration
java -jar $JAR transfer --projects-file paths.txt    # import specific projects
java -jar $JAR transfer --skip-existing              # skip already-migrated projects

# Phase 2: drift detection (1-2 weeks later)
java -jar $JAR verify                                # full comparison
java -jar $JAR verify --check-metadata               # also compare issue/MR/label/milestone counts
java -jar $JAR verify --projects-file subset.txt     # verify specific projects

# Phase 3: re-sync drifted repos
java -jar $JAR resync --projects-file reports/out_of_sync_<ts>.txt
java -jar $JAR resync --projects-file f.txt --reimport          # destructive full refresh
java -jar $JAR resync --projects-file f.txt --keep-protections  # don't touch branch protections
java -jar $JAR resync --projects-file f.txt --sleep 30          # 30s pause between repos

# Re-transfer missing projects
java -jar $JAR transfer --projects-file reports/missing_<ts>.txt
```

## Throttling knobs (protecting the source server)

| Property | Default | Description |
|----------|---------|-------------|
| `migration.batch-size` | 10 | Projects per bulk-import request. One batch at a time. |
| `migration.sleep-between-batches-seconds` | 300 | Cool-down between batches. |
| `migration.poll-interval-seconds` | 30 | How often to check bulk import status. |
| `migration.batch-timeout-seconds` | 3600 | Give up waiting for a batch after this. |
| `resync --sleep` | 10 | Seconds between mirror clones in Phase 3. |

Server-side, you can additionally cap `bulk_import_concurrent_pipeline_batch_limit`
and tune Sidekiq on both instances per GitLab's import documentation.

## Reports and state files

| File | Phase | Description |
|------|-------|-------------|
| `reports/transfer_failures.csv` | 1 | Failed project imports |
| `reports/verify_failures.csv` | 2 | Projects that could not be verified |
| `reports/resync_failures.csv` | 3 | Failed re-sync attempts |
| `reports/sync_report_<ts>.csv` | 2 | Full comparison (all projects) |
| `reports/out_of_sync_<ts>.txt` | 2 | Drifted project paths (input for Phase 3) |
| `reports/missing_<ts>.txt` | 2 | Missing project paths (input for Phase 1 retry) |
| `reports/migration.log` | All | Combined log file |
| `transfer_state.json` | 1 | Resume state (per-project status) |

## Important caveats

* **Drift re-sync is git-only.** `push --mirror` makes branches/tags identical
  but does not touch issues, MRs, wikis, or CI variables. For full metadata
  refresh use `resync --reimport`, which **deletes** the target project
  first — destructive to anything created on the new server since migration.
* **Protected branches**: mirror pushes are force-pushes; the tool lifts and
  restores target-side protections by default (`--keep-protections` to opt out).
* **Delayed deletion**: if the destination has delayed project deletion
  enabled, `--reimport` waits for the path to free up and aborts if it doesn't.
* **What direct transfer migrates** (issues, MRs, pipelines history, etc.)
  varies by GitLab version — check the "migrated items" matrix in the docs for
  your versions and spot-check a pilot group first.
* **Pilot first**: run the whole three-phase cycle on one small subgroup
  before pointing it at all 10k repos.
