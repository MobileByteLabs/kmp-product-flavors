# Coverage Deep Dive — Contributor Playbook

> Companion to [`COVERAGE_GUIDE.md`](COVERAGE_GUIDE.md). When CI surfaces a coverage regression on your PR, this is the playbook for closing the gap.

The plugin ships three established patterns for closing coverage gaps. Picking the right pattern saves hours of frustration; using the wrong one (e.g. TestKit when a direct mock would do) makes CI slow without adding signal.

---

## The three patterns

### Pattern A — Direct unit tests

**Use when:** the uncovered code is reachable through a public or `internal` entry point and the dependencies can be mocked with `mockk` + `ProjectBuilder`.

**Reference implementation:** [`AgpBridgeMultiDimTest`](../build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/AgpBridgeMultiDimTest.kt) (v2.6 Tier C). 11 tests that exercise `AgpBridge.propagateFlavors{Legacy,CrossProduct}` directly via a [`MockAndroidExtension`](../build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/MockAndroidExtension.kt) — Kotlin classes shaped to satisfy the bridge's reflection lookup. Visibility refactor (`private → internal`) on the two propagators made the test access possible.

**Recipe:**

1. Identify the uncovered function. Run `./gradlew koverHtmlReport` and inspect the per-class report under `build/reports/kover/html/`.
2. If the function is `private` and isn't reachable through a public/internal API, **promote it to `internal`**. Do not add `@VisibleForTesting` — promotion is preferred (matches the v2.6 Tier C precedent).
3. Build a reflection-shaped mock for any AGP / KGP / Gradle surface the function consumes via `Class.getMethods`. [`MockAndroidExtension`](../build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/MockAndroidExtension.kt) is the reference; add a sibling mock if your function consumes a different shape.
4. Add a focused unit test class (`<TargetClass>Test.kt`) using JUnit 5 + `mockk` + `ProjectBuilder` if a `Project` instance is required.
5. Assert mock state + observable behaviour (logger calls, returned values, side effects on the mock).

**Cost per test:** ~50–100 ms wall-clock.

### Pattern B — Snapshot fixtures

**Use when:** the uncovered code is in a string-template codegen emit path (`GenerateBuildConfigTask`, `GenerateKoinModulesTask`, `GenerateAnalyticsTagsTask`).

**Reference implementation:** [`BuildKonfigCodegenSnapshotTest`](../build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/tasks/BuildKonfigCodegenSnapshotTest.kt). Fixture files under `src/test/resources/buildkonfig-snapshots/{name}.kt.txt`; tests diff the generated output against the fixture using `assertEquals`.

**Recipe:**

1. Identify the uncovered emit branch (e.g. "network DSL with no flavor match" or "perTarget with multiple fields per target").
2. Set up the task inputs in a `@Test` method using `ProjectBuilder`-instantiated `GenerateBuildConfigTask` (or sibling).
3. Run the task once locally + read the emit output to construct the fixture file.
4. Commit the fixture to `src/test/resources/{group}-snapshots/{name}.kt.txt`.
5. Use either full-diff (`assertEquals(fixture(name), output)`) or substring assertions (`assertTrue(output.contains("..."))`). Full-diff is brittle but catches whitespace drift; substring is forgiving but allows accidental regressions in formatting.

**Cost per test:** ~30–50 ms wall-clock.

### Pattern C — TestKit fixtures

**Use when:** the uncovered code is in a plugin-application lifecycle hook (`project.afterEvaluate`, `androidComponents.finalizeDsl`, `beforeVariants`, `pluginManager.withPlugin`) that only fires when a real Gradle build context exists.

**Reference implementation:** [`SourceSetWiringRegressionTest`](../build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/SourceSetWiringRegressionTest.kt) (v2.6 Tier E.1). Uses `GradleRunner.create()` + `@TempDir` to construct a fixture build script, applies the plugin, and asserts against `--warning-mode=all` output / generated files.

**Recipe:**

1. Identify the uncovered lifecycle hook. Use `Phase 3 coverage-gap-ledger.md` classification "TestKit" rows.
2. Create a new `*RegressionTest.kt` under `src/test/kotlin/com/mobilebytelabs/kmpflavors/internal/`.
3. Construct a `settings.gradle.kts` + `build.gradle.kts` fixture pair using `@TempDir`. Apply the plugin via `withPluginClasspath()`.
4. Run a Gradle command (`tasks`, `assembleAllVariants`, a custom task) and capture `result.output`.
5. Assert against output substring matches or generated file existence/content.

**Cost per test:** ~3–8 s wall-clock (Gradle daemon spawn). Use sparingly; prefer Pattern A or B if reachable.

---

## Choosing the right pattern

```
uncovered function
        │
        ├─ Is it pure / does it touch AGP / KGP only via reflection?
        │   └─ YES → Pattern A (direct unit test + mock)
        │
        ├─ Is it a string-template codegen emit path?
        │   └─ YES → Pattern B (snapshot fixture)
        │
        └─ Does it only run inside a Gradle lifecycle hook?
            └─ YES → Pattern C (TestKit fixture)
```

If a function falls into multiple categories, pick the cheapest pattern first.

---

## The sealed exclusion list

Per [`GOAL.md`](../../plan-layer/project-plans/mbs/kmp-product-flavors/active/v27-agp9-support/GOAL.md) D10, the kover exclusion list is **sealed** at 6 categories. Adding a new exclusion requires a GOAL amendment + reviewer sign-off.

| Pattern | Rationale |
|---|---|
| `*BuildConfig` | AGP-generated; not plugin code |
| `*BuildKonfig*` | Codegen output; tested via snapshot fixtures |
| `*Test*` | Test helpers themselves |
| `*.generated.*` | Generated emission paths |
| `*$Companion$*` | Sealed `companion object` markers without behaviour |
| `*$sam$*` | Kotlin lambda SAM conversion shims |

**Forbidden alternatives:**
- `@Suppress("Unused")` to hide code from coverage — promote + add a test instead
- `@VisibleForTesting` to grant test access — promote to `internal` instead (v2.6 Tier C precedent)
- New exclusion categories without GOAL amendment — fails the v2.7 contract gate

---

## Reading the kover HTML report

```bash
./gradlew :build-logic:flavor-plugin:koverHtmlReport
open build-logic/flavor-plugin/build/reports/kover/html/index.html
```

- **Class index** — per-package line counts. Sort by missed lines; the largest gap is the highest-value target.
- **Per-class drill-down** — green = covered, red = uncovered, yellow = partial. Click the class name.
- **Branch coverage column** — informational only in v2.7; line coverage is the gate.

For machine-parseable per-class data:

```bash
./gradlew :build-logic:flavor-plugin:koverXmlReport
# Output at: build-logic/flavor-plugin/build/reports/kover/report.xml
```

Parse `report > package > class > counter[type='LINE']` for `missed` / `covered` counts.

---

## Common pitfalls

| Pitfall | Symptom | Fix |
|---|---|---|
| Mock missing a method the function calls reflectively | `NullPointerException` or "method not found" | Add the method to your mock; check via `methods.firstOrNull { it.name == "X" }` parity |
| TestKit fixture missing `settings.gradle.kts` | "Project not found" build error | Always write both `settings.gradle.kts` and `build.gradle.kts` in the `@TempDir` |
| Snapshot fixture has trailing whitespace | `assertEquals` fails with confusing diff | Use editor settings to trim trailing whitespace; or use substring assertions |
| Direct test relies on `Project.afterEvaluate` | Test passes locally but assertions fire BEFORE the action body | `afterEvaluate` only fires via real Gradle build; switch to TestKit |
| Promoted `private → internal` triggers downstream visibility errors | Compile failure in subagent files | Audit reverse-dependents; promote intermediate callers as needed |

---

## When you're stuck

1. Check the [`GOAL.md`](../../plan-layer/project-plans/mbs/kmp-product-flavors/active/v27-agp9-support/GOAL.md) Pillar 3 contract — your gap might be excluded by the sealed list.
2. Check [`coverage-gap-ledger.md`](../../plan-layer/project-plans/mbs/kmp-product-flavors/active/v27-agp9-support/coverage-gap-ledger.md) — your gap might already be assigned to a pattern.
3. If the function is genuinely impossible to cover (defensive return for a precondition the validator catches earlier), document the precondition link in a one-line comment and skip — but flag the line for review in the PR.

---

## See also

- [`COVERAGE_GUIDE.md`](COVERAGE_GUIDE.md) — gate definition + floor table + CI workflow contract
- [`SOURCE_SET_DISCIPLINE.md`](SOURCE_SET_DISCIPLINE.md) — discipline contracts from v2.6 Tier E.1
- v2.6 Tier C reference implementation: `AgpBridgeMultiDimTest` + `MockAndroidExtension`
- v2.6 Tier E.1 reference implementation: `SourceSetWiringRegressionTest`
