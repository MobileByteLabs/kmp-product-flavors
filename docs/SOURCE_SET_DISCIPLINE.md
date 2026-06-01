# Source-set Wiring Discipline

> **Status:** Research finding (2026-06-01). Recommended fix
> ([Hypothesis D](#hypothesis-d-opt-in-flag)) is pending implementation in a
> follow-up Tier E.1 plan. This document is the canonical reference for the
> failure mode + the disproved fix + the path forward.

A consumer (kmp-project-template, 2026-06-01) surfaced a long-standing KGP
warning when the plugin was active alongside on-disk `src/commonProd/kotlin/`
content but `prod` was **not** the active flavor:

```
w: Following Kotlin Source Sets were configured but not added to any Kotlin compilation:
   * commonProd
```

This document captures the investigation, the failed first-pass fix, the
remaining hypotheses, and the recommended path. A regression test
(`SourceSetWiringRegressionTest` — deferred to follow-up Tier E.1) will lock
the fix once chosen.

---

## The trigger

When a consumer has on-disk content at `src/commonProd/kotlin/` but builds
with the default active flavor (`demo` in their case), the plugin's
`SourceSetConfigurator` *creates* the `commonProd` source set lazily — its
`hasOnDiskContent` rule fires because the directory exists. But because
`prod` isn't the active flavor, the configurator does **not** wire
`commonProd.dependsOn(commonMain)` or attach it to any `KotlinCompilation`.
KGP then emits the "Unused Kotlin Source Sets" warning at config time.

Consumer workaround (currently in
`samples/kmp-project-template/build-logic/convention/src/main/kotlin/org/convention/KotlinMultiplatform.kt`):

```kotlin
afterEvaluate {
    sourceSets.findByName("commonProd")
        ?.dependsOn(sourceSets.getByName("commonMain"))
}
```

This silences the warning but only because the consumer reaches in after the
plugin has finished wiring. It's a plumbing leak we'd rather not require.

---

## Hypothesis A — `dependsOn(commonMain)` silences the warning (DISPROVED 2026-06-01)

**Hypothesis.** Wiring `commonProd.dependsOn(commonMain)` regardless of
active-flavor status would make KGP treat `commonProd` as "used" — same
mechanism the consumer's `afterEvaluate` workaround relies on.

**Attempted fix.** Dropped the `isActiveFlavor` gate on the 5 wiring sites in
`SourceSetConfigurator.kt` (lines 81, 94, 111, 163, 171 in the v2.5.0-alpha.1
snapshot) so every `common<Flavor>` / `intermediate<Flavor>` /
`platform<Flavor>` source set got its `dependsOn` edge wired regardless of
active status. Published as `2.5.0-alpha.2` for local TestKit verification.

**Result.** Warning **STILL FIRES**. KGP's "Unused" check is
compilation-membership-based — it walks `KotlinCompilation.kotlinSourceSets`
and flags any registered source set absent from that closure. The `dependsOn`
graph is not consulted.

**Reverted.** SourceSetConfigurator restored to v2.5.0-alpha.1 + the
preceding-section discipline (active-only wiring + lazy creation rule) on
2026-06-01. v2.5.0-alpha.2 published with the revert (no behavioural delta
from v2.5.0-alpha.1).

**Lesson.** Future fix proposals MUST be validated against a TestKit
regression test that loads the actual KGP version + asserts the warning is
absent from `--warning-mode=all` output — not just inspect the `dependsOn`
graph or pin assumptions on AGP-side behavior.

---

## Remaining hypotheses

### Hypothesis B — suppress lazy creation when matrix mode is off

Don't create the inactive source set in the first place, even if its
`src/commonProd/kotlin/` directory has content. Emit a structured WARN log so
the consumer notices their code is unreachable:

> `[KMP Flavors] src/commonProd/kotlin/ has files but matrix mode is off and 'prod' is not the active flavor — this code is currently DEAD. Set buildMatrix=true or switch active via -PkmpFlavor=prodDebug.`

**Pros.**
- No new source set → no "Unused" warning, structurally.
- Forces the consumer to opt into one of two well-defined paths (matrix mode
  OR active-flavor switch). Removes the silent dead-code class.

**Cons.**
- Breaking change for any consumer who *wanted* the source set created (e.g.
  conditional compilation harness, dev preview, expect/actual scaffolding).
- IDE editors lose code navigation in inactive files until matrix mode
  toggles on.

### Hypothesis C — attach inactive source sets via `KotlinSourceSetTree` API

Formally register the inactive source set with a custom
`KotlinSourceSetTree` so KGP sees it as "intentionally outside any
compilation but tracked." API surface inspection still pending.

**Pros.** Cleanest semantic match if the KGP API actually supports this
use case.

**Cons.**
- Likely requires KGP-internal API access (`org.jetbrains.kotlin.gradle.plugin.mpp.*`
  internals). Cross-version stability is risky — every KGP minor could
  rename or rework the type.
- Unknown whether registering a sourceSetTree without a backing compilation
  actually silences the "Unused" warning — KGP's check may still fire.

### Hypothesis D — opt-in flag `kmpFlavors.createInactiveFlavorSourceSets`

Add a strict-additive boolean flag (default `false`):

```kotlin
kmpFlavors {
    createInactiveFlavorSourceSets.set(true) // opt-in
}
```

**Default (`false`).** When the directory has content but the flavor is
inactive and matrix mode is off, the plugin silently SKIPS source set
creation and logs:

> `[KMP Flavors] Skipping creation of inactive source set 'commonProd' — set kmpFlavors.createInactiveFlavorSourceSets.set(true) to enable, or switch active flavor via -PkmpFlavor=prod{BuildType}.`

No warning fires because no source set was created.

**Opt-in (`true`).** Restores current behavior: lazy-create the inactive
source set with `hasOnDiskContent` and accept the KGP warning as the cost of
keeping the IDE editing experience.

**Pros.**
- Strict-additive (matches the v2.5+ discipline contract).
- Default-off is the safer choice for the majority of consumers who don't
  rely on inactive source set creation.
- Migration path is explicit: opt-in if you want the prior behavior.
- Compatible with both Hypothesis B's "structural skip" and a future
  Hypothesis C implementation (the flag gates which path runs).

**Cons.**
- Adds one DSL knob. Strict-additive contract makes this low-cost but it's
  still surface area to document + test.
- Existing consumers (kmp-project-template + others) need to either set the
  flag OR drop their `afterEvaluate` workaround — minor migration.

### Recommendation

**Hypothesis D + log.** Conservative, strict-additive, preserves the v2.4
contract for opt-in consumers, removes the warning for the silent majority.
Hypothesis B's behaviour is one knob away (the default-off path); Hypothesis
C remains a possible future enhancement under the same flag (toggle the
implementation, not the DSL surface).

---

## Implementation plan (deferred to Tier E.1)

The user pre-approved Tier E in v2.6 Phase 1 as **research-only**. The
implementation lands as a separate plan:

| Task | Description | Where |
|------|-------------|-------|
| T-E1.1 | Add `createInactiveFlavorSourceSets: Property<Boolean>` to `KmpFlavorExtension` (default false) | `KmpFlavorExtension.kt` |
| T-E1.2 | Gate `maybeCreateLazy()` in `SourceSetConfigurator` on the new flag | `SourceSetConfigurator.kt` |
| T-E1.3 | Author `SourceSetWiringRegressionTest` (TestKit) — reproduces consumer scenario, asserts `--warning-mode=all` output has zero "Unused Kotlin Source Sets" matches when flag is default-off | `internal/SourceSetWiringRegressionTest.kt` |
| T-E1.4 | Cascade-clean kmp-project-template consumer — remove the `afterEvaluate { ?.dependsOn(commonMain) }` workaround in `KotlinMultiplatform.kt` | `samples/kmp-project-template/build-logic/convention/...` (cross-repo PR) |
| T-E1.5 | Document the flag in `docs/PRODUCT_FLAVORS.md` (DSL reference) + cross-link from this guide | `PRODUCT_FLAVORS.md` |

Acceptance: `./gradlew :samples:multi-dim-3d:tasks --all --warning-mode=all 2>&1 | grep -c "Unused Kotlin Source Sets"` returns `0`.

---

## Why not just ship the Hypothesis A fix anyway?

The failed attempt cost ~1h of investigation + a published `v2.5.0-alpha.2`
revert. The lesson: **never ship a source-set fix without a TestKit-level
regression test that asserts `--warning-mode=all` output**. KGP's
config-time checks are not graph-traversal-based; they are
compilation-membership-based, and intuition about `dependsOn` semantics
mistakes one for the other.

The Hypothesis D flag is the smallest change that (a) gives consumers the
warning-free experience by default and (b) preserves the existing lazy-
creation behavior under an opt-in. It does so without depending on
KGP-internal APIs (vs. Hypothesis C) or imposing a breaking change
(vs. unconditional Hypothesis B).

---

## See also

- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/SourceSetConfigurator.kt`
  — the 5 active-only wiring sites
- `plan-layer/.../v26-stability-parity-beyond-platform/01-coverage-gate.md`
  Tier E — the originating investigation tasks (T11–T14)
- `docs/COVERAGE_GUIDE.md` — coverage gate the v2.6 Phase 1 ships alongside this research
