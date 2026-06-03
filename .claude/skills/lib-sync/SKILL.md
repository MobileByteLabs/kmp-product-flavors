---
name: lib-sync
description: One-command consumer-side migration. Detects the active project (kmp-product-flavors library), identifies the registered consumer (samples/kmp-project-template by default), runs a dry-run plan, asks the user to confirm, and applies the migration with drift-gate enforcement. No flags required for the common case.
allowed-tools: Bash, Read, Edit, Write, Grep, Glob, AskUserQuestion
user_invocable: true
scope: project
---

# /lib-sync

**One command. Zero flags to remember.** Just type `/lib-sync` and the skill walks the full migration loop end-to-end with a confirmation prompt before any commit.

## What this skill does

Default behavior when invoked with NO arguments:

1. **Detect active project.** Confirm cwd is the kmp-product-flavors library repo (presence of `gradle.properties` + `scripts/adoption-doc-verify.py`). If not, surface an error and exit.
2. **Identify the consumer.** Default is `samples/kmp-project-template` (the first-party canonical consumer). If multiple consumers exist in the future, use AskUserQuestion to pick one.
3. **Compute the plan.** Invoke `./scripts/lib-sync.sh --dry-run` against the chosen consumer. The script:
   - Detects the library's current version from gradle.properties
   - Pulls the consumer's default branch (probes dev / development / main / master)
   - Computes the diff (version bump + Tier 2 doc section append)
   - Runs the drift gate against the post-migration state
4. **Report status to user.** Three possible outcomes:
   - **Already current** — consumer's pin matches library version + gate is green. No action needed.
   - **Bump applicable, gate green** — consumer is behind; would apply N changes; gate is green after the migration. Show the diff summary.
   - **Bump applicable, gate red** — consumer is behind BUT the new version's structural delta isn't captured in the Tier 2 doc. Surface the offending verify block + remediation guidance.
5. **Confirm before commit.** If a bump applies AND the gate is green, use AskUserQuestion to ask "Apply now / Show full diff / Cancel". On "Apply now", re-invoke `./scripts/lib-sync.sh` WITHOUT `--dry-run` to commit the migration.
6. **Print push instructions** at the end. NEVER auto-push.

## Workflow detail

```
User types: /lib-sync
     │
     ▼
Skill: detect cwd is library repo? ─── no ──→ surface error + exit
     │ yes
     ▼
Skill: resolve consumer (default samples/kmp-project-template)
     │
     ▼
Skill: run `./scripts/lib-sync.sh --dry-run <consumer>`
     │
     ▼
Skill: parse the script output, classify outcome
     │
     ├── "no changes — already at X.Y.Z" ──→ report "✓ already current" + exit
     │
     ├── "bump A.B.C → X.Y.Z, gate green" ──→ show summary + AskUserQuestion
     │       │
     │       ├── User: "Apply now" ──→ run `./scripts/lib-sync.sh <consumer>` (no --dry-run)
     │       │                          → print push instructions + exit
     │       │
     │       ├── User: "Show full diff" ──→ `git -C <consumer> diff` + re-ask
     │       │
     │       └── User: "Cancel" ──→ clean up working tree + exit
     │
     └── "gate red" ──→ surface failing verify block + remediation guidance + exit
                        (DO NOT commit; the working-tree changes stay for manual inspection)
```

## Argument forms

For the rare cases where the smart default isn't what you want:

| Form | Meaning |
|---|---|
| `/lib-sync` | Smart default — described above |
| `/lib-sync <path>` | Same flow, against a non-default consumer path |
| `/lib-sync --target-version X.Y.Z` | Override target version (default: parse from gradle.properties) |
| `/lib-sync --yes` | Skip the confirmation prompt (CI / non-interactive use) |
| `/lib-sync --dry-run` | Plan only; never commit. Same as the first half of the smart default. |

The runtime spec at [`layers/lib/commands/lib-sync.md`](../../../layers/lib/commands/lib-sync.md) documents the full 8-phase script behavior. This skill is the human-facing wrapper.

## Output style

Terse. Quote the script's headers verbatim. End with the commit SHA + push instructions block.

Failure modes (drift gate red, target version malformed, consumer dirty) get a single-paragraph explanation + the exact one-liner to fix. Never auto-fix gate failures — the contributor's call.

## Reference

- Bash driver: [`scripts/lib-sync.sh`](../../../scripts/lib-sync.sh)
- Runtime spec: [`layers/lib/commands/lib-sync.md`](../../../layers/lib/commands/lib-sync.md)
- Adoption-doc pattern: [`docs/adoption/README.md`](../../../docs/adoption/README.md) — "/lib-sync — automated per-release Tier 2 migration" section
- Tier 2 record: [`samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md`](../../../samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md)
