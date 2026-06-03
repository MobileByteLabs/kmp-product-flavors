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
│   docs/ADOPTION_KMP_PRODUCT_FLAVORS.md  — "we inherit kmp-product-flavors│
│                                            transitively via the template;│
│                                            fork-specific deltas listed"  │
│   ├── Inherited template SHA + version reference                         │
│   ├── LocalFlavors.kt additions (fork-only flavors)                      │
│   └── Fork-specific buildConfigPackage, codegen-host, etc.               │
└──────────────────────────────────────────────────────────────────────────┘
```

**Single source of truth per tier**: each doc IS the audit trail for its tier's adoption. No tier copies content from another — they cross-reference. Future migrations follow the same recursive update pattern: library publishes Tier 1 → template updates Tier 2 → forks update Tier 3.

### Why this matters

- **No duplicated content** — each doc owns exactly what its tier knows.
- **Concrete + abstract pair** — library's `consumer.md` is the abstract spec; template's `ADOPTION_KMP_PRODUCT_FLAVORS.md` is the worked example. AI agents can paste both and have full context.
- **Future-proof** — when v2.8 ships, the library publishes a new Tier 1 pair; the template appends a new version section to its existing Tier 2 doc; forks append to theirs. No fork-out from a single repo.
- **AI-executable end-to-end** — Tier 1 verify gates + Tier 2 concrete paths/outputs = an agent can paste both and run the full adoption unattended.

### Tier 2 reference: `samples/kmp-project-template`

The canonical Tier 2 file lives at [`samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md`](../../samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md). It demonstrates:

| What | Where |
|---|---|
| Files inventory of the convention-plugin adoption | top-of-doc table |
| v2.7.0 adoption record | concrete §1–§14 verify gates citing kmp-project-template's actual paths and expected outputs |
| "How future bumps work" recipe | 8-step process for adopting v2.8 / v3.0 — single living doc, version-stacked |
| "How downstream forks structure theirs" | template for `mifos-mobile`/`mifos-pay`/etc. Tier 3 docs |

When you fork or copy `kmp-project-template`, copy that doc too — fill in your fork's specifics and ship it. That's how the chain stays intact.

## Adding a new pair (release-time discipline)

Every minor release MUST ship the pair before the GA promotion lands. Mechanically:

1. Bump `kmpflavors.version` in `gradle.properties` to the new minor (e.g. `2.8.0-alpha.1`).
2. Create `docs/adoption/v{X.Y}/library.md` + `docs/adoption/v{X.Y}/consumer.md`.
3. Mirror every NEW section: library claim → consumer verifier.
4. Carry forward sections that are still active (DSL surface, validator codes — see v2.7 as the template).
5. Update `docs/adoption/README.md` "Available versions" table.
6. CI gate `adoption-doc-symmetry-check.yml` (TODO — v2.8) will enforce that every library section has a matching consumer section.

## Why this matters

- **AI-first integration**: a consumer pastes `consumer.md` into Claude/Cursor and the agent runs the full adoption gate without human guidance.
- **No silent gaps**: every library-side change has a paired consumer-side verifier. If we shipped something a consumer should configure, the verify block exists.
- **Greenfield onboarding**: new adopters don't need to read the whole CHANGELOG — they read one consumer doc and they're fully integrated.
- **Audit trail**: "are we on v2.7 properly?" is now a deterministic yes/no via the verify-gate transcript.
