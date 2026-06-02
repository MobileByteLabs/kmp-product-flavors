# Coverage Guide

> **Since v2.6** — Kover line-coverage gate + Pitest mutation report. Filter +
> verify shape adopted from `mifos-x/kmp-project-template`'s proven
> `configureKoverRootReports()` pattern.

The plugin module ships a coverage gate on every PR via
`.github/workflows/coverage-gate.yml`. The gate runs `koverVerify` against a
configurable line-coverage floor; failure blocks merge. A second informational
job runs Pitest mutation analysis and uploads the HTML report — failure does
**not** block the PR (planned promotion to gate in v2.7+).

---

## The current floor

| Metric                   | Value (as of 2026-06-02)                 |
|--------------------------|------------------------------------------|
| Empirical line coverage  | **99.63%** — measured at every koverLog run |
| Gate floor (default)     | **99%** (since v2.7; `-PkoverLineMin` overridable) |
| Headroom                 | ~0.6 percentage points                   |
| Test count               | **701 tests across 92 classes** (was 281 / 45 at v2.6 GA — +420 tests, +47 classes) |
| Roadmap target           | **100%** — remaining ~6 missed lines are documented unreachable defensive paths; closing them is v2.7.x cleanup |

The empirical baseline is published in `build-logic/flavor-plugin/build.gradle.kts`
next to the `kover { reports.verify { rule { minBound(...) } } }` block. Raise
the floor in lockstep with coverage growth via:

```bash
# One-shot override on a feature branch
./gradlew :build-logic:flavor-plugin:koverVerify -PkoverLineMin=40

# Permanent bump — edit the default in flavor-plugin/build.gradle.kts and
# document the ramp in this file's table above.
```

---

## What's excluded from coverage

The filter set mirrors `kmp-project-template`'s production-tested pattern
(adapted for this single-composite plugin project):

| Pattern                        | Why excluded                              |
|--------------------------------|-------------------------------------------|
| `*BuildConfig`                 | AGP-generated constants                   |
| `*BuildKonfig*`                | Plugin codegen output — tested via snapshot fixtures, not line coverage |
| `*Test*`                       | Test helpers themselves                   |
| `*.generated.*` (package)      | Any future generated codegen output paths |

The full block lives in `build-logic/flavor-plugin/build.gradle.kts` →
`kover { reports.filters.excludes { ... } }`.

---

## Running locally

```bash
# Verify against the current floor (fastest — just gates, no report)
./gradlew :build-logic:flavor-plugin:koverVerify

# Generate the HTML report for browsable line/branch breakdown
./gradlew :build-logic:flavor-plugin:koverHtmlReport
open build-logic/flavor-plugin/build/reports/kover/html/index.html

# Generate the XML report (CI-consumable, e.g. Codecov upload)
./gradlew :build-logic:flavor-plugin:koverXmlReport

# Quick text summary
./gradlew :build-logic:flavor-plugin:koverLog
```

---

## Pitest (informational)

Pitest mutation testing ships in v2.6 as an **informational artifact only** —
it runs on every PR via the `pitest-informational` job in
`.github/workflows/coverage-gate.yml` (job-level `continue-on-error: true`)
and uploads the HTML report. PRs are never blocked on mutation failures in
v2.6.

```bash
./gradlew :build-logic:flavor-plugin:pitest
open build-logic/flavor-plugin/build/reports/pitest/index.html
```

**Why informational, not gating?** Mutation scores are volatile in early
adoption — false positives from generated codegen + reflection sites dominate.
The plan promotes Pitest to a gating signal in **v2.7+** once the baseline
mutation score stabilises.

Configuration lives in the same `build.gradle.kts` next to the kover block:

```kotlin
pitest {
    targetClasses.set(listOf("com.mobilebytelabs.kmpflavors.*"))
    threads.set(4)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    junit5PluginVersion.set(libs.versions.pitestJunit5.get())
}
```

---

## Closing the gap to 95%

The 26.5% → 28.1% jump in Tier C came from re-enabling
`AgpBridgeMultiDimTest` (11 tests, direct calls to internal propagators via
`MockAndroidExtension`). The same playbook applies to the remaining gap:

1. **Open the HTML report** → identify uncovered classes ranked by line count.
2. **Identify internal-only surfaces** — refactor `private` → `internal` if
   the function is logically unit-testable but currently only reachable
   through an integration gate. (Precedent: `propagateFlavorsLegacy` +
   `propagateFlavorsCrossProduct` in v2.6 Tier C.)
3. **Build a reflection-shaped mock** for any AGP / KGP / Gradle surface the
   function consumes via `Class.getMethods`. `MockAndroidExtension` is the
   reference pattern at
   `build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/MockAndroidExtension.kt`.
4. **Add a focused unit test class** — assert mock state + telemetry-log
   shape via `mockk` + `io.mockk.verify`.
5. **Re-run `koverHtmlReport`** to confirm the gap closes; raise
   `-PkoverLineMin` accordingly.

---

## CI gate details

`.github/workflows/coverage-gate.yml` runs on PRs that touch:

- `build-logic/**`
- `gradle/libs.versions.toml`
- `.github/workflows/coverage-gate.yml`

It has two jobs:

| Job                       | Gates PR? | Notes                                                    |
|---------------------------|:---------:|----------------------------------------------------------|
| `kover-verify`            |   YES     | Runs `koverVerify`; uploads HTML+XML to PR artifacts     |
| `pitest-informational`    |   NO      | Runs `pitest`; uploads HTML; `continue-on-error: true`   |

A separate `workflow_dispatch` trigger lets you override
`-PkoverLineMin` ad-hoc via the workflow inputs UI.

---

## Roadmap

- **v2.6.x rolling** — close internal-helper testability gaps; ramp floor 25 →
  40 → 60 → 80 → 95 in step with coverage growth. Each bump lands as a single
  PR with the new floor + the test(s) that justify it.
- **v2.7** — promote Pitest to a gating signal (separate threshold;
  `continue-on-error` removed from the workflow). Per-method mutation score
  surfaces in the HTML report; CI fails on regressions below the locked
  mutation floor.

---

## See also

- `build-logic/flavor-plugin/build.gradle.kts` — kover + pitest config
- `.github/workflows/coverage-gate.yml` — PR gate definition
- `samples/multi-dim-3d/` — integration-level AgpBridge coverage (TestKit + real AGP classpath)
- `plan-layer/.../v26-stability-parity-beyond-platform/01-coverage-gate.md` —
  the originating epic plan with full AC list + tier breakdown
