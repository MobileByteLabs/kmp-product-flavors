---
router:
  tier: opus
  dispatch: inline
  reasoning: "judgment + cross-repo state"
user_invocable: true
scope: project
---

# /lib-sync — Runtime Instructions

> Companion to [`.claude/skills/lib-sync/SKILL.md`](../../../.claude/skills/lib-sync/SKILL.md). Part of the library-publisher-side automation set documented in [`docs/adoption/README.md`](../../../docs/adoption/README.md).
>
> **What this is**: end-to-end Tier 2 migration when the library publishes a new version. Reads the library's adoption recipe at `docs/adoption/v{X.Y}/consumer.md`, applies the implied changes to a consumer (default: `samples/kmp-project-template`), runs the drift gate, prepares a commit on a new branch, and prints push instructions.
>
> **What this is NOT**: this does NOT push autonomously. It does NOT bump the submodule pointer in the library repo. It does NOT propagate changes to downstream consumers (mifos-mobile, mifos-x-field-officer-app) — those pick up the update via `sync-dirs.sh` against the merged consumer PR.

---

## Info

| Attribute | Value |
|---|---|
| Command | `/lib-sync` |
| Purpose | Sync library version into a consumer per the adoption-doc recipe; commit on a new branch; print push + PR instructions. |
| Category | lib (library-publisher automation, sister of `/lib-fix` + `/lib-observe`) |
| Scope | library repo (kmp-product-flavors) |
| Tier | opus (cross-repo state + adoption-doc judgment) |

**Usage:**

```
/lib-sync                              # default: syncs samples/kmp-project-template to current library version
/lib-sync <consumer-path>              # explicit consumer (rare — kmp-project-template is the only direct consumer)
/lib-sync --target-version X.Y.Z       # override the version to migrate to (default: parsed from gradle.properties)
/lib-sync --dry-run                    # plan + show staged diff; do not commit
```

The consumer-path arg is positional. Flags can appear anywhere.

---

## Workflow (10 phases)

```
PHASE 1   Pre-flight — verify cwd is library repo root + consumer path exists
PHASE 2   Resolve target version — parse gradle.properties or use --target-version
PHASE 3   Resolve adoption recipe path — docs/adoption/v{X.Y}/consumer.md
          (auto-scaffold from previous minor if missing)
PHASE 4   Consumer branch setup — fetch default, create chore/sync-… branch
PHASE 4a  AUDIT — run library's Tier 1 verify blocks against consumer
          classifies each into {PASS, AUTO_FIX, MANUAL, SKIP}
PHASE 5   Migration apply — bump libs.versions.toml + apply AUTO_FIX edits
          + append Tier 2 doc section
PHASE 5a  POST-MIGRATION AUDIT — re-audit, surface remaining MANUAL gaps
          (informational; does not block)
PHASE 6   Drift gate — run consumer's scripts/adoption-doc-verify.py
PHASE 7   Commit (or bail clean on gate failure)
PHASE 8   Summary — print push + PR creation instructions
```

---

## Phase 1 — Pre-flight

The script (`scripts/lib-sync.sh`) refuses to run if:

- Current directory does not contain both `gradle.properties` AND `scripts/adoption-doc-verify.py` (rules out wrong cwd).
- The consumer path passed as `$1` (or default `samples/kmp-project-template`) does not exist as a directory.
- The consumer has uncommitted local changes — fails fast to avoid clobbering.

These guards prevent the most common foot-guns: running from the wrong directory, syncing the wrong consumer, or losing local work.

---

## Phase 2 — Resolve target version

- If `--target-version X.Y.Z` was passed: use it verbatim.
- Otherwise: read `kmpflavors.version` from `gradle.properties`. If it ends in `-alpha.N` / `-beta.N` / `-rc.N` / `-SNAPSHOT`, strip the suffix to get the implied stable target (e.g. `2.8.0-alpha.1` → `2.8.0`).

The resolved value MUST match `^[0-9]+\.[0-9]+\.[0-9]+$`. Otherwise fail with a clear message.

---

## Phase 3 — Resolve adoption recipe

Derive `LIB_MINOR` (`{X}.{Y}`) from the target version, then read `docs/adoption/v${LIB_MINOR}/consumer.md`.

If the recipe doesn't exist, fail with:

```
error: adoption doc not found: docs/adoption/v{X.Y}/consumer.md
       expected to exist for migration recipe
```

The recipe is the source of truth. If a new library version ships without a corresponding adoption recipe, `/lib-sync` refuses to proceed.

---

## Phase 4 — Consumer branch setup

Inside the consumer path:

```bash
git fetch origin <default-branch>
git checkout -b chore/sync-kmp-product-flavors-v{X.Y.Z} origin/<default-branch>
```

Default branch is resolved by probing in order: `dev`, `development`, `main`, `master`. The first one that exists on `origin` wins.

If the target branch already exists, reuse it + rebase onto fresh `origin/<default>`. Fail clean on rebase conflict — the user resolves manually.

---

## Phase 4a — AUDIT (run library's contract against this consumer)

Before any edits are made, invoke `scripts/lib-sync-audit.py`:

```bash
python3 ../scripts/lib-sync-audit.py \
    --consumer . \
    --library-doc ../docs/adoption/v{X.Y}/consumer.md \
    --target-version {X.Y.Z}
```

The audit extracts every `### ✅ Verify` / `### Release-time check` / `#### [§N` bash block from the library's Tier 1 `consumer.md` and runs it against the consumer working tree. Each section is classified:

| Status | Meaning | Lib-sync action |
|---|---|---|
| **PASS** | Verify exited 0; consumer conforms | Nothing |
| **AUTO_FIX** | Failed AND section title matches `AUTO_FIX_PATTERNS` (currently: "Plugin pinned to v…" → `version_pin`; "Toolchain compatibility" → `toolchain_floor`) | Apply the safe edit in Phase 5 |
| **MANUAL** | Failed AND no known safe auto-fix | Surface in Phase 5a + Phase 8 summary; contributor decides |
| **SKIP** | Section has a `CONDITIONAL_PATTERN` precondition that doesn't hold (e.g. AGP-9-only §12 when consumer is on AGP 8; §4a vs §4b mutex) | Ignored as inapplicable |

Output: structured JSON on stdout (consumed by lib-sync.sh) + human-readable summary on stderr.

The KEY insight: the library's `consumer.md` IS the contract. We don't need a parallel rule engine — running the contract against the consumer IS the audit.

To add a new auto-fix kind: append a row to `AUTO_FIX_PATTERNS` in the audit script AND add the corresponding edit logic in Phase 5.

To add a new precondition (e.g. "Section only applies on Gradle 9+"): append a row to `CONDITIONAL_PATTERNS` with a precondition lambda.

---

## Phase 5 — Migration apply

Two changes:

### 5.1 Bump `gradle/libs.versions.toml#kmpProductFlavors`

```python
# In-place edit via Python (portable across BSD + GNU sed differences).
import re, pathlib
p = pathlib.Path("gradle/libs.versions.toml")
src = p.read_text()
new = re.sub(
    r'^(kmpProductFlavors\s*=\s*)"[^"]*"',
    rf'\1"{TARGET_VERSION}"',
    src, count=1, flags=re.MULTILINE,
)
p.write_text(new)
```

Skip if the consumer is already at the target version.

### 5.2 Append a version section to `docs/ADOPTION_KMP_PRODUCT_FLAVORS.md`

If the consumer has a Tier 2 adoption record AND a section for the target version doesn't yet exist, prepend the new section above the most recent existing `## vX.Y.Z` section. Section template:

```markdown
## v{X.Y.Z} — adopted {YYYY-MM-DD}

### Bump from v{prev} → v{X.Y.Z}

```diff
- gradle/libs.versions.toml#[versions].kmpProductFlavors = "{prev}"
+ gradle/libs.versions.toml#[versions].kmpProductFlavors = "{X.Y.Z}"
```

### Why the bump is safe

See the library's migration doc:
[MobileByteLabs/kmp-product-flavors/docs/MIGRATION_v{prev_minor}_TO_v{X.Y}.md](https://github.com/...).

### Verify

Run the §1–§14 verify suite (see Tier 1 [consumer.md](https://github.com/...)).
```

The script does NOT auto-fill the "Why the bump is safe" prose beyond linking to the migration doc — that's the contributor's call to expand if structural changes happened.

---

## Phase 5a — POST-MIGRATION AUDIT REPORT

Re-run `lib-sync-audit.py` against the post-migration working tree. For each section now classified MANUAL, print:

```
  ⚠ N section(s) still need manual contributor attention:
    · 10. AGP-only modules: configureFlavors(CommonExtension) helper
      → expected `configureFlavors(commonExtension)` call site not found in :app/build.gradle.kts
    · 11. Downstream extension hook: LocalFlavorsLoader pattern (optional)
      → expected LocalFlavorsLoader.kt absent
```

This is INFORMATIONAL — the script does NOT halt on MANUAL gaps. The version pin + auto-fixes have been applied; the contributor decides whether to:

1. Ship the partial migration now (commit + open PR, address manual gaps in follow-ups).
2. Cancel + fix locally first (`git checkout -- .` to revert, then hand-author the missing changes).

The drift gate (Phase 6) IS the hard halt — that fires on regressions, not on pre-existing gaps that lib-sync can't auto-resolve.

---

## Phase 6 — Drift gate

If the consumer has `scripts/adoption-doc-verify.py` AND a Tier 2 record:

```bash
python3 scripts/adoption-doc-verify.py docs/ADOPTION_KMP_PRODUCT_FLAVORS.md
```

- **Green** → continue to commit.
- **Red** → bail clean. Print the gate's failure block. Tell the user: "the new library version requires X — update the Tier 2 verify block + 'What you should have' section to match, then re-run `/lib-sync`." Leave the working-tree changes uncommitted so the user can inspect via `git diff`.

The script never silently fixes gate failures. The decision (revert vs. update doc) belongs to the contributor.

---

## Phase 7 — Commit

If gate is green AND there are staged changes:

```
chore(deps): sync kmp-product-flavors → v{X.Y.Z}

Automated migration via lib-sync.sh against the library's
docs/adoption/v{X.Y}/consumer.md adoption recipe.

Changes:
  - gradle/libs.versions.toml: bump kmpProductFlavors {prev} → {X.Y.Z}
  - docs/ADOPTION_KMP_PRODUCT_FLAVORS.md: appended v{X.Y.Z} section

The library's migration doc says no breaking changes for this bump
(see MobileByteLabs/kmp-product-flavors/docs/MIGRATION_*.md).
Drift gate passed against the consumer's verify blocks.
```

If `--dry-run`, skip commit. Print `git status --short` so the user sees what would be staged.

---

## Phase 8 — Summary

Print:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ✓ sync committed: <sha> on branch chore/sync-kmp-product-flavors-v{X.Y.Z}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Push to remote (not done autonomously):
    git -C {consumer-path} push -u origin chore/sync-kmp-product-flavors-v{X.Y.Z}

  Then open a PR on the consumer repo:
    gh -R "$(git -C {consumer-path} remote get-url origin | sed 's|.*:||;s|.git$||')" pr create \
      --base <default-branch> --head chore/sync-kmp-product-flavors-v{X.Y.Z} \
      --title 'chore(deps): sync kmp-product-flavors → v{X.Y.Z}'
```

The push step is deliberately manual. Same rationale as `/git-session-commit` — autonomous pushes to consumer repos are risky.

---

## Error Handling

| Scenario | Action |
|---|---|
| Run from wrong directory (no `gradle.properties` / no `scripts/adoption-doc-verify.py`) | Exit 2 with `error: run this from the kmp-product-flavors library repo root` |
| Consumer path doesn't exist | Exit 2 |
| Target version malformed | Exit 2 with hint to pass `--target-version X.Y.Z` |
| Adoption recipe missing at `docs/adoption/v{X.Y}/consumer.md` | Exit 2 with hint to author the recipe first |
| Consumer has uncommitted changes | Exit 2 with "stash or commit first" |
| Could not resolve consumer's default branch | Exit 2 (probed `dev`/`development`/`main`/`master`) |
| Rebase onto fresh default branch failed | Exit 2 with "resolve manually" |
| `kmpProductFlavors` line not found in `libs.versions.toml` | Exit 2 (the consumer doesn't have this library pinned via the expected key) |
| Drift gate failed after migration | Exit 1 with the gate's failure block + remediation guidance (revert OR update doc) |

---

## Related

- Companion skill: `.claude/skills/lib-sync/SKILL.md`
- Bash driver: `scripts/lib-sync.sh`
- Sister automation: `/lib-fix` (frame-cross bug fix), `/lib-observe` (runtime telemetry), `/lib-install-bump-workflow` (subscribe consumer to library publish events)
- Adoption pattern docs: `docs/adoption/README.md` "Three-tier source-of-truth chain"
- Drift gate script: `scripts/adoption-doc-verify.py`
