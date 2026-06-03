# Adoption guides

> **Pattern (since v2.7):** every minor release ships a pair of mirrored adoption guides — one library-side, one consumer-side — designed to be **AI-executable** end-to-end.

## Why two docs (not one)

| Doc | Audience | Question it answers |
|---|---|---|
| `v{X.Y}/library.md` | Library author + release-time CI | "What did v{X.Y} ship? What did we claim to consumers?" |
| `v{X.Y}/consumer.md` | Consumer + integration-time AI agent | "How do I get from zero to fully-integrated on v{X.Y}? How do I verify nothing is missing?" |

The two are **mirrored**: every claim on the library side has a paired verifier on the consumer side. If you see a v3 claim with no consumer verifier (or vice versa), file a bug — adoption is incomplete.

## Why this is different from `MIGRATION_v{X}_TO_v{Y}.md`

| Doc family | Scope | Tone |
|---|---|---|
| `MIGRATION_v{X}_TO_v{Y}.md` | Delta between two adjacent versions | "You do not need to migrate." Optimised for the 95% of consumers who just bump the version pin. |
| `adoption/v{X.Y}/consumer.md` | Full end-to-end integration of v{X.Y} | "Run these N verify gates. If all pass, you have 100% adoption with no missing pieces." Optimised for greenfield consumers + major upgrades + AI agents that need a checklist. |

Migration docs are for "I'm already on v{X-1}, what changed?"
Adoption docs are for "I'm new, or I want to verify I'm fully on v{X.Y}."

Both ship together. They don't duplicate — they cover different intents.

## AI-executable format

Every consumer-side section follows this shape:

```markdown
## N. <Topic>

### What you should have
<expected state — code snippet or description>

### ✅ Verify
\`\`\`bash
<concrete command an AI can run>
\`\`\`

**Expected output**: <pattern to match>

**If verify fails**: <remediation steps>
```

An AI agent (Claude, Cursor, Copilot, etc.) can paste the consumer doc and:
1. Run every verify block in order
2. Compare actual output against "Expected output"
3. Apply remediation if a verify fails
4. Re-run until every verify is green
5. Report "100% adoption complete" with the gate transcript

The library-side mirror has the same numbering — section N claims X, consumer section N verifies X.

## Cadence

| Release type | Adoption-doc pair? |
|---|---|
| Major (X+1.0.0) | New pair |
| Minor (X.Y+1.0) | New pair |
| Patch (X.Y.Z+1) | Reuse parent minor's pair |
| Alpha / RC | Reuse the next-stable's pair (or draft if shape is changing) |

So `2.7.x` patches inherit `v2.7/`. When `2.8.0` ships, a new `v2.8/` pair lands.

## Available versions

| Version | Library claims | Consumer verifier | Sections |
|---|---|---|---:|
| **v2.7** (current GA) | [library.md](v2.7/library.md) | [consumer.md](v2.7/consumer.md) | 14 |

The v2.7 pair covers: plugin pinning via `libs.versions.toml` (both `[plugins]` alias + `[libraries]` artifact entries), toolchain compat, **both adoption patterns (direct-apply + convention-plugin)**, DSL surfaces, flavor/dimension registration, `buildConfigPackage` (with the canonical single-source-of-truth pattern via `[versions].appId`), default variant resolution, BuildKonfig codegen output + claim mechanism, validator codes V01–V30, **AGP-only-module `configureFlavors(CommonExtension)` helper**, **downstream extension hook `LocalFlavorsLoader` pattern**, AGP 9 landmines (conditional), end-to-end smoke test, and the `samples/kmp-project-template` reference implementation.

Every section cites the corresponding file in `samples/kmp-project-template` (the first-party canonical consumer owned by the Mifos Initiative) so consumers can copy the exact reference shape rather than reading abstract instructions.

Older versions (v2.6 and earlier) do not have adoption docs — the pattern starts at v2.7. Consumers on v2.6 should use [`MIGRATION_v2.6_TO_v2.7.md`](../MIGRATION_v2.6_TO_v2.7.md) to bump, then run the v2.7 adoption gate.

## Three-tier source-of-truth chain

The full adoption story spans three docs, each owned by a different tier and serving a different audience:

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Tier 1: Library (this repo: MobileByteLabs/kmp-product-flavors)          │
│   docs/adoption/v{X.Y}/library.md  — "what we did + how we verify it"    │
│   docs/adoption/v{X.Y}/consumer.md — "abstract verify gates for ANY      │
│                                        consumer, AI-executable"          │
└──────────────────────────────────────────────────────────────────────────┘
                                 │
                                 │ first-party canonical reference
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ Tier 2: Template (samples/kmp-project-template, openMF/kmp-project-…)    │
│   docs/ADOPTION_KMP_PRODUCT_FLAVORS.md  — "concrete realization of every │
│                                            library verify gate in OUR    │
│                                            codebase, per version"        │
│   ├── Files inventory (KMPFlavorsConventionPlugin.kt, AppFlavor.kt, …)   │
│   ├── v2.7.0 section: bump, diff, why-safe, 14 concrete verify gates     │
│   ├── v2.8.0 section (future): same shape, appended on bump              │
│   └── "How future bumps work" + "How downstream forks structure theirs"  │
└──────────────────────────────────────────────────────────────────────────┘
                                 │
                                 │ sync-dirs.sh + local overrides
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ Tier 3: Downstream app (mifos-mobile, mifos-pay, mifos-x-…)              │
│   build-logic/convention/src/main/kotlin/local/LocalFlavors.kt           │
│                                          — ONLY file the fork owns.      │
│                                            Adds use-case-specific flavors│
│                                            (enterprise, regional, etc.). │
│                                                                          │
│   Everything else (convention plugin, AppFlavor, LocalFlavorsLoader,     │
│   the adoption doc itself, build-logic/convention/* base) ALWAYS         │
│   stays in kmp-project-template and arrives via sync-dirs.sh.            │
│   Forks DO NOT ship their own ADOPTION_KMP_PRODUCT_FLAVORS.md — they     │
│   inherit the Tier 2 doc from the template sync.                         │
└──────────────────────────────────────────────────────────────────────────┘
```

**Tier 3 is intentionally minimal**: a fork owns one file (`local/LocalFlavors.kt`). Every other adoption artefact lives in Tier 2 and arrives via the template sync. Forks don't think about adoption — they think about their flavors.

**Single source of truth per tier**: each doc IS the audit trail for its tier's adoption. No tier copies content from another — they cross-reference. Future migrations follow the recursive pattern: library publishes Tier 1 → `/lib-sync` updates Tier 2 in `samples/kmp-project-template` → forks pull the updated Tier 2 via `sync-dirs.sh` and (rarely) tweak their `LocalFlavors.kt` if the bump exposed a new flavor-DSL feature they want.

### Why this matters

- **No duplicated content** — each doc owns exactly what its tier knows.
- **Concrete + abstract pair** — library's `consumer.md` is the abstract spec; template's `ADOPTION_KMP_PRODUCT_FLAVORS.md` is the worked example. AI agents can paste both and have full context.
- **Future-proof** — when v2.8 ships, the library publishes a new Tier 1 pair; the `/lib-sync` automation appends a new version section to the template's Tier 2 doc; forks inherit it on next sync. Zero manual coordination across N forks.
- **Forks own exactly one file** — `local/LocalFlavors.kt`. Everything else flows top-down from library → template → fork.

### Tier 2 reference: `samples/kmp-project-template`

The canonical Tier 2 file lives at [`samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md`](../../samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md). It demonstrates:

| What | Where |
|---|---|
| Files inventory of the convention-plugin adoption | top-of-doc table |
| v2.7.0 adoption record | concrete §1–§14 verify gates citing kmp-project-template's actual paths and expected outputs |
| "How future bumps work" recipe | 8-step process for adopting v2.8 / v3.0 — single living doc, version-stacked |
| "How downstream forks structure theirs" | template for `mifos-mobile`/`mifos-pay`/etc. Tier 3 docs |

When you fork or copy `kmp-project-template`, copy that doc too — fill in your fork's specifics and ship it. That's how the chain stays intact.

## `/lib-sync` — automated per-release Tier 2 migration

When the library publishes a new version, the consumer-side migration is automated end-to-end via the `/lib-sync` slash-command (skill at [`.claude/skills/lib-sync/SKILL.md`](../../.claude/skills/lib-sync/SKILL.md), runtime at [`layers/lib/commands/lib-sync.md`](../../layers/lib/commands/lib-sync.md), bash driver at [`scripts/lib-sync.sh`](../../scripts/lib-sync.sh)).

What it does:

```
/lib-sync                    # default: syncs samples/kmp-project-template to current library version
/lib-sync <consumer-path>    # explicit consumer (rare — kmp-project-template is the only direct consumer)
/lib-sync --target-version X.Y.Z
/lib-sync --dry-run
```

Workflow:

1. Reads the library's current version from `gradle.properties`.
2. Locates the adoption recipe at `docs/adoption/v{X.Y}/consumer.md`.
3. Resolves the consumer's default branch (probes `dev` / `development` / `main` in order) and creates `chore/sync-kmp-product-flavors-v{X.Y.Z}` on top of it.
4. Applies the migration:
   - Bumps `kmpProductFlavors = "X.Y.Z"` in the consumer's `gradle/libs.versions.toml`.
   - Appends a new `## v{X.Y.Z} — adopted {YYYY-MM-DD}` section to `docs/ADOPTION_KMP_PRODUCT_FLAVORS.md` with the diff + a link to the library's migration doc.
5. Runs the consumer's `scripts/adoption-doc-verify.py` against the updated record.
6. Commits if green; bails clean (no partial commit) if any verify regresses.
7. Prints push instructions — never auto-pushes.

The forks (mifos-mobile / mifos-pay / mifos-x-field-officer-app / …) don't run `/lib-sync` directly. They run `sync-dirs.sh` against the template, which pulls in the bumped `gradle/libs.versions.toml` + updated convention plugin + new adoption-doc section. The only file the fork ever owns is `build-logic/convention/src/main/kotlin/local/LocalFlavors.kt` — and only if their use case needs extra flavors beyond the base demo/prod contract.

### Mental model

```
Library publishes v2.8.0
     │
     │  cd kmp-product-flavors && /lib-sync
     ▼
Tier 2 (samples/kmp-project-template) gets:
  - gradle/libs.versions.toml bumped
  - docs/ADOPTION_KMP_PRODUCT_FLAVORS.md appended
  - PR opened on chore/sync-kmp-product-flavors-v2.8.0
     │
     │  PR merges into kmp-project-template/dev
     ▼
Tier 3 (mifos-mobile, mifos-pay, mifos-x-…) get:
  - ./sync-dirs.sh pulls the updated template
  - No manual file edits needed UNLESS their LocalFlavors.kt
    uses a DSL feature that changed (rare; library is additive)
```

### When the gate forces a doc update during `/lib-sync`

If the library's new version structurally requires a change beyond a version-string bump (e.g. a renamed extension, a new required field, a removed alias), `/lib-sync` will:

- Apply the version bump
- Run the adoption-doc-verify against the Tier 2 record
- Detect the failure
- Bail with a clear message: "the new library version requires X — update the Tier 2 verify block + 'What you should have' section to match, then re-run `/lib-sync`."

This means migration discipline lives in TWO places, mechanically enforced:
1. **Library side**: the `consumer.md` for the new version MUST encode the structural deltas as verify-block diffs.
2. **Tier 2 side**: when `/lib-sync` applies the bump, the gate immediately catches whether the existing Tier 2 record covers the deltas.

There's no third place where a contributor has to "remember" to update something.

## Drift detection (local tool)

The single-source-of-truth property is enforced via a local script — no CI gate (we keep CI surface light). [`scripts/adoption-doc-verify.py`](../../scripts/adoption-doc-verify.py) parses every fenced `bash` block under a `### ✅ Verify` / `### Release-time check (CI)` / `#### [§N …]` header, runs each, and reports PASS/FAIL.

Run before pushing any change to the convention plugin / extension / validator / DSL / toolchain:

```bash
python3 scripts/adoption-doc-verify.py docs/adoption/v2.7/library.md
```

Expected output:

```
━━━ docs/adoption/v2.7/library.md ━━━
  · 1. Plugin published as v2.7.0 with maven artifact + Gradle plugin id    ✓ PASS
  · 2. Toolchain floors stated + built-against pinned                       ✓ PASS
  ...
═══════════════════════════════════════════════════════════════════════
Total: 14   PASS: 14   FAIL: 0
```

If anything fails, the script prints the offending block + output. The fix path is binary:
1. Revert the implementation change (file rename, alias move, dimension removal) IF unintended.
2. Update the verify block + corresponding section of the doc IF intentional.

To skip an individual block, prefix the first line of the bash block with `# adoption-verify: skip`.

## Adding a new pair — automated via `/lib-sync`

Every minor release MUST ship the pair before the GA promotion lands. With auto-scaffold wired into `/lib-sync` (since 2026-06-03), the maintainer flow collapses to:

1. Bump `kmpflavors.version` in `gradle.properties` to the new minor (e.g. `2.8.0-alpha.1`).
2. Run `/lib-sync`.
3. The skill detects `docs/adoption/v2.8/` is missing and auto-scaffolds:
   - Copies `docs/adoption/v2.7/library.md` → `docs/adoption/v2.8/library.md`
   - Copies `docs/adoption/v2.7/consumer.md` → `docs/adoption/v2.8/consumer.md`
   - Search-replaces `v2.7` → `v2.8` and `2.7.0` → `2.8.0` (targeted; older `v2.6`-style floor refs left alone)
   - Prepends a BOOTSTRAPPED banner with a TODO summarizing what was replaced
   - Runs the drift gate against the new `v2.8/library.md` — any breakage from the search-replace surfaces immediately
4. Maintainer reviews the two scaffolded files, fills in version-specific deltas (new sections for new capabilities, updated section content for changed contracts).
5. Maintainer updates `docs/adoption/README.md` "Available versions" table.
6. Maintainer commits + pushes the new pair.
7. `/lib-sync` proceeds with the consumer-side migration on top of the now-present library docs.

Power-user flags:

- `/lib-sync --no-bootstrap` — refuse the scaffold; fail fast if the pair is missing (use when authoring by hand).
- `/lib-sync --target-version X.Y.0` — explicitly target a version (e.g. to bootstrap the doc before bumping `gradle.properties`).

This means the "Adding a new pair" discipline ISN'T a 10-step checklist anymore — it's `/lib-sync` + a maintainer review pass over the scaffolded diff.

## Why this matters

- **AI-first integration**: a consumer pastes `consumer.md` into Claude/Cursor and the agent runs the full adoption gate without human guidance.
- **No silent gaps**: every library-side change has a paired consumer-side verifier. If we shipped something a consumer should configure, the verify block exists.
- **Greenfield onboarding**: new adopters don't need to read the whole CHANGELOG — they read one consumer doc and they're fully integrated.
- **Audit trail**: "are we on v2.7 properly?" is now a deterministic yes/no via the verify-gate transcript.
