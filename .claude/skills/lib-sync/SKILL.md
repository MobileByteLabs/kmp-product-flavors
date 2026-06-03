---
name: lib-sync
description: One-command consumer-side migration. Detects the active project (kmp-product-flavors library), identifies the registered consumer (samples/kmp-project-template by default), runs a dry-run plan, asks the user to confirm, and applies the migration with drift-gate enforcement. No flags required for the common case.
allowed-tools: Bash, Read, Edit, Write, Grep, Glob, AskUserQuestion
user_invocable: true
scope: project
---

# /lib-sync

**One command. Zero flags to remember.** Just type `/lib-sync` and the skill walks the full migration loop end-to-end — full audit, automatic safe fixes, manual-gap report, confirmation prompt, commit.

## What this skill does

Default behavior when invoked with NO arguments:

1. **Detect active project.** Confirm cwd is the kmp-product-flavors library repo (presence of `gradle.properties` + `scripts/adoption-doc-verify.py`). If not, surface an error and exit.
2. **Identify the consumer.** Default is `samples/kmp-project-template` (the first-party canonical consumer). If multiple consumers exist in the future, use AskUserQuestion to pick one.
3. **Auto-scaffold library-side adoption doc if missing.** If `docs/adoption/v{X.Y}/library.md` doesn't exist for the target version, the script bootstraps it from the most-recent previous minor — copy `v{prev}/{library,consumer}.md` → `v{X.Y}/`, search-replace `v{prev}` → `v{X.Y}` and `{prev}.0` → `{X.Y}.0`, prepend a BOOTSTRAPPED banner with a TODO for the maintainer to add version-specific deltas. The drift gate runs against the bootstrapped `library.md` immediately so any search-replace breakage surfaces before the consumer migration starts.
4. **Phase A — AUDIT against the library's Tier 1 contract.** Before doing any migration, run `scripts/lib-sync-audit.py` which extracts every `### ✅ Verify` block from `docs/adoption/v{X.Y}/consumer.md` and runs it against the consumer's working tree. Each block is classified:
   - **PASS** — consumer already conforms; nothing to do for that section
   - **AUTO_FIX** — failed, but the script knows how to repair it safely (currently: `version_pin` bumps in `libs.versions.toml` + `toolchain_floor` bumps in Gradle wrapper)
   - **MANUAL** — failed and needs human judgment (new DSL surfaces, missing convention-plugin wiring, fork-specific business decisions)
   - **SKIP** — conditional and inapplicable (e.g. §4a only applies on the direct-apply pattern; §12 only on AGP 9)
5. **Phase B — APPLY auto-fixes + bump the version pin.** Edit `libs.versions.toml` (version-pin auto-fix) plus any other safe edits flagged by Phase A; the consumer-side Tier 2 record (`samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md`) gets a new version section appended.
6. **Phase C — POST-MIGRATION audit + drift gate.** Re-run the audit against the modified working tree:
   - **MANUAL gaps remaining** → surface each gap (section name + first error line) so the contributor knows exactly what still needs human attention. We never block on these — they're informational.
   - **Drift gate (consumer's `scripts/adoption-doc-verify.py` against the Tier 2 record)** → if any verify block regresses, HALT with the failing block + guidance. The migration stays in the working tree uncommitted for inspection.
7. **Confirm before commit.** If audit is clean (or MANUAL gaps acknowledged) AND drift gate is green, use AskUserQuestion to ask "Apply now / Show full diff / Cancel". On "Apply now", re-invoke `./scripts/lib-sync.sh` WITHOUT `--dry-run` to commit the migration.
8. **Print push instructions** at the end. NEVER auto-push.

### Auto-scaffold semantics

Phase 3's auto-scaffold solves the "new release → adoption doc must exist" problem. When you bump `kmpflavors.version` from `2.7.x` to `2.8.0-alpha.1` and run `/lib-sync`, the skill notices `docs/adoption/v2.8/` is missing and scaffolds the pair from `v2.7/` before continuing. You review the scaffolded files (the banner TODO highlights any version-specific edits needed), commit them as the library-side adoption doc for the new minor, and the consumer migration proceeds on top.

Pass `--no-bootstrap` to refuse the scaffold (e.g. you want to author the doc by hand).

## Workflow detail

```
User types: /lib-sync
     │
     ▼
detect cwd is library repo? ─── no ──→ error + exit
     │ yes
     ▼
resolve consumer (default samples/kmp-project-template)
     │
     ▼
bootstrap docs/adoption/v{X.Y}/ if missing (Phase 3)
     │
     ▼
Phase A — AUDIT: run library's Tier 1 verify blocks against consumer
     │           emits {pass, auto_fix, manual, skip} counts
     ▼
run `./scripts/lib-sync.sh --dry-run <consumer>`
   ├── bumps version pin (always)
   ├── applies AUTO_FIX edits (toolchain bumps, etc.)
   ├── re-audits → reports remaining MANUAL gaps to contributor
   └── runs drift gate on the post-migration working tree
     │
     ▼
classify outcome:
     │
     ├── "already current" ──→ report ✓ + exit
     │
     ├── "applied N edits, audit clean, gate green" ──→ AskUserQuestion
     │       ├── Apply now → re-run without --dry-run → commit → push instructions
     │       ├── Show full diff → `git -C <consumer> diff` → re-ask
     │       └── Cancel → working tree preserved
     │
     ├── "applied N edits, MANUAL gaps surfaced" ──→ AskUserQuestion (same 3 options)
     │       (contributor decides if they want to ship the partial migration
     │        and address manual gaps in a follow-up, OR cancel + fix locally first)
     │
     └── "drift gate RED" ──→ surface failing verify block + halt (no commit)
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
