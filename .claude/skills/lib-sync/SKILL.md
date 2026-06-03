---
name: lib-sync
description: Sync the kmp-product-flavors library version into a consumer project per the library's adoption doc recipe. Default consumer is samples/kmp-project-template (the first-party canonical Tier 2 reference). Bumps libs.versions.toml, appends the Tier 2 adoption doc's version section, runs the drift gate, and prepares a commit on a new branch ready for push + PR.
allowed-tools: Bash, Read, Edit, Write, Grep, Glob
user_invocable: true
scope: project
---

# /lib-sync — automated per-release Tier 2 migration

Slash-command wrapping [`scripts/lib-sync.sh`](../../../scripts/lib-sync.sh). Runs the consumer-side migration loop documented in [`docs/adoption/README.md`](../../../docs/adoption/README.md) "/lib-sync — automated per-release Tier 2 migration" section.

## Workflow

1. Resolve the active project via `bash core/scripts/session-resolve.sh` (if framework session-binding is in use). If not bound, proceed against the current cwd as the library repo root.
2. Read the runtime spec at [`layers/lib/commands/lib-sync.md`](../../../layers/lib/commands/lib-sync.md) for the full step list.
3. Invoke `./scripts/lib-sync.sh` with the user-supplied args (or defaults — no args = sync `samples/kmp-project-template`).
4. Stream the script's output back; surface the final commit SHA + push instructions block.
5. If the script exits non-zero (drift gate failed), surface the gate's failure block verbatim — DO NOT auto-fix the doc; the contributor needs to decide whether the new library version's structural delta warrants a doc update or whether the consumer needs an implementation change first.

## Output style

Be terse and structured. Quote the script's section headers (`━━━ lib-sync ━━━`, `→ creating chore/sync-...`, `✓ drift gate green`, `✓ sync committed`). At the end, surface the `git push` and `gh pr create` instructions the script printed.

When the gate fails, lead with the offending section name + the doc path, then propose ONE of:
- "revert <implementation change>" (if the change was unintended)
- "update verify block in <doc> §N to match the new reality" (if the change was deliberate but the doc still encodes the old contract)

Never silently swallow gate failures.

## Reference

- Bash driver: `scripts/lib-sync.sh`
- Runtime spec: `layers/lib/commands/lib-sync.md`
- Adoption-doc-pattern docs: `docs/adoption/README.md`
- Tier 2 reference: `samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md`
