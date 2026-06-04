# RFC: v2.0 — Per-Variant KotlinCompilation (Matrix Mode)

| Field | Value |
|---|---|
| Status | **Draft — open for review** |
| Author | Rajan Maurya (`rmaurya@mifos.org`) |
| Created | 2026-05-13 |
| Spike branch | `spike/d1-per-variant-compilation` (HEAD `58ee241`) |
| Predecessor | `2026-05-12-kmp-product-flavors-followups.md` (v1.1.6 shipped) |
| Target | v2.0.0 (6-week milestone plan in §5) |

---

## TL;DR

The D1 spike proved KGP cleanly accepts a second `KotlinCompilation` on a non-Android target alongside `main`. v2.0 brings AGP-style **matrix mode** to KMP: build every variant × every target in one Gradle invocation, opt-in, with **zero per-module DSL changes** for consumers. This RFC locks the design across 26 questions, presents the 6-week plan, and asks for **GO / NO-GO / DEFER** sign-off before W1 implementation begins.

---

## 1. Goal & Non-goals

**Goal**: Build all N variants of a KMP module in a single Gradle invocation on non-Android targets, matching what AGP already does for Android.

**Non-goals**:
- Change active-variant resolution for legacy `-PkmpFlavor=…` callers (v1.x semantics preserved when matrix mode is OFF).
- Change the public API surface of `kmpFlavors { }` extension (additive only — no field removals or renames).
- Change Android target behaviour (AGP already handles matrix; we don't touch it).
- Replace the Kotlin Hierarchy Template (v2.0 composes with it, doesn't replace it).

**Explicit non-promises for v2.0 GA** (deferred to v2.1+ unless evidence forces inclusion):
- Per-variant resources (image/string assets that differ between variants).
- Cross-variant code-sharing intermediate source sets (e.g., a `commonStaging` shared between `freeStaging` + `paidStaging`).

> **Updated by Q4 spike (this RFC)**: Per-variant compilations on iOS/native targets are now **in scope for v2.0 GA**, not deferred. See §3 Q4 for evidence.

### 1.1 Design Tenet — Zero-Touch Adoption (PRIMARY, NON-NEGOTIABLE, NON-BYPASSABLE)

> **v2.0 MUST preserve and strengthen the v1.x single-setup adoption model. Consumer wires the plugin ONCE in their convention plugin. Every KMP module that applies the convention inherits all matrix behavior automatically. Per-module `build.gradle.kts` files contain ZERO v2.0-specific DSL.**
>
> **All per-variant compilation logic lives INSIDE the `kmp-product-flavors` plugin.** Consumers never write `compilations { create("freeDev") { … } }` blocks — on any target (Desktop / iOS / JS / WasmJs). If a future spike, sample, or doc snippet shows such a block in a CONSUMER build file, that snippet is a PROOF artifact only and MUST be removed before merging to `main`. Reviewers MUST reject any PR that introduces per-variant `compilations { create(…) }` to a consumer KMP module's `build.gradle.kts`.

What this means concretely for a consumer app:

```kotlin
// build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt
// ───── consumer wires this exactly ONCE for the whole project ─────
class KMPFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")
        extensions.configure<KmpFlavorExtension> {
            buildConfigPackage.set("org.example.app")
            // flavors / buildTypes / buildMatrix toggle defined here as v1.x
        }
    }
}

// cmp-shared/build.gradle.kts          ← unchanged from v1.x
plugins {
    alias(libs.plugins.kmp.library.convention)
    alias(libs.plugins.cmp.feature.convention)
}
kotlin {
    jvm("desktop")        // ← auto-detected; per-variant compilations registered for FREE
    iosX64(); iosArm64(); iosSimulatorArm64()
    js(IR) { browser() }  // ← same
    wasmJs { browser() }  // ← same
}

// feature/home/build.gradle.kts        ← unchanged from v1.x
// feature/login/build.gradle.kts       ← unchanged from v1.x
// (… every module that applies kmp.library.convention gets the matrix automatically …)
```

What this rules OUT for v2.0:
- ❌ Per-module `kotlin { … }` DSL to opt into matrix per target.
- ❌ Per-module `buildMatrix.set(…)` calls.
- ❌ Per-module `compilations { … create("freeDev") … }` blocks on ANY target (JVM/Desktop, iOS/Native, JS, WasmJs). **The D1 spike used these in `samples/basic-flavors/build.gradle.kts` (Desktop), and the 2026-05-13 Q4 iOS mini-spike added one to `iosSimulatorArm64` — both are PROOF artifacts only, NOT the v2.0 consumer-facing API.** The plugin's `apply()` registers all per-variant compilations programmatically; consumer KMP module files keep their bare `iosSimulatorArm64()` / `jvm("desktop")` declarations exactly as they were in v1.x.
- ❌ Any v2.0 setting that requires editing more than one file when adding a new variant.

### 1.2 Where the code lives — plugin internals vs consumer files

This is a clarification on §1.1 in response to design feedback during RFC drafting:

| Layer | Owns what | Touched by consumer? |
|---|---|---|
| Consumer's per-module `build.gradle.kts` | Just `jvm("desktop")` / `iosX64()` / `js(IR)` declarations — **bare target calls, no body, no `compilations { }` block** | ❌ Never edited for v2.0 |
| Consumer's convention plugin (single file) | `extensions.configure<KmpFlavorExtension> { … flavors, buildTypes, buildMatrix, buildConfigPackage … }` | ✅ Edited exactly once per project, ever |
| Consumer's `gradle.properties` (optional override) | `kmpFlavors.buildMatrix=true` (Q16 hybrid opt-in) | ✅ Optional; flips matrix on globally |
| Plugin internals (`io.github.mobilebytelabs.kmp-product-flavors`) | Walks `kotlin.targets`, calls `target.compilations.create("freeDev") { ... }` programmatically for every (variant × target). Registers BuildConfig generator tasks per variant. Wires per-variant source sets. | n/a — invisible to consumer |

This separation must be testable in §7 Acceptance Criteria: every test that opts into matrix mode does so without touching any KMP module's `build.gradle.kts`. Specifically: `git diff samples/kmp-project-template/cmp-shared/build.gradle.kts` between v1.x and v2.0 with matrix mode enabled MUST produce **zero hunks**. Same for every `feature/*/build.gradle.kts` and `core/*/build.gradle.kts`. The plugin owns the compilation matrix; consumer KMP modules stay clean.

---

## 2. Spike evidence summary

The D1 spike (`spike/d1-per-variant-compilation`, HEAD `58ee241`) added a second `KotlinCompilation` named `freeDev` alongside `main` on the Desktop JVM target of `samples/basic-flavors`. Outcome: **green** — KGP registered `compileFreeDevKotlinDesktop`, the `BuildKonfig` generator produced output, and the compilation succeeded. Full details in `docs/D1-SPIKE-RESULT.md`.

This RFC additionally ran **three new live probes** during drafting (2026-05-13) to convert the plan's open questions on iOS, configuration cache, and build time into measured data. Results inline in §3 Q4, Q7, Q8.

---

## 3. The twenty-six design questions

Each question lists options, the provisional answer (final answers come from stakeholder survey + reviewer sign-off), and where applicable the spike evidence that locks it.

### Q1 — Naming of additional compilations
- A: `freeDevDesktop` (variant-prefix-target-suffix)
- B: `desktopFreeDev` (target-prefix-variant-suffix — matches the spike's `compileFreeDevKotlinDesktop` task naming)
- C: `desktop/freeDev` (slash-separated like AGP source sets)
- Affects: task names IDEs surface, completion in build files, hierarchy traversal, autocomplete UX.
- **Provisional answer**: **B** (matches the spike-proven KGP convention; `compileFreeDevKotlinDesktop` reads as "compile the FreeDev variant of the Kotlin Desktop compilation").

### Q2 — Source-set inheritance strategy
- A: Direct `kotlin.srcDir()` per compilation (used in spike — works, but bypasses Hierarchy Template).
- B: Register a `KotlinSourceSet` with `dependsOn` chain (idiomatic for KMP, integrates with Hierarchy Template, but more verbose plugin code).
- C: Hybrid — Hierarchy Template for common edges, `srcDir` for variant-specific roots.
- Affects: shared-code resolution across iOS / Desktop / Web; whether `expect`/`actual` works across variants; whether `commonFree → commonMain` is automatic or explicit.
- **Provisional answer**: **C** — Hierarchy Template + per-variant `KotlinSourceSet`s registered by the plugin.

### Q3 — BuildConfig generator changes
Current state: `:generateFlavorBuildConfig` emits ONE `BuildKonfig.kt` for the active variant under `build/generated/kmpFlavors/commonMain/kotlin/`.

v2.0 options:
- A: One generator task per variant (`generateFreeDevBuildKonfig`), each with its own `@OutputDirectory`. N tasks, easier per-variant invalidation.
- B: Single task with `@OutputDirectories` keyed by variant name. Fewer tasks, but coarser-grained invalidation.
- C: Variant-aware `Provider<Directory>` registered into each compilation's `kotlin.srcDir`. Lazy, configuration-cache-friendly.

Affects: incremental build correctness, configuration cache compatibility, task graph size, `./gradlew tasks` output verbosity.

**Provisional answer**: **A** — one generator task per variant.

### Q4 — iOS / JS / WasmJs / Native targets

**Spike-locked answer.** Direct probe on `iosSimulatorArm64` during this RFC's drafting session:

```text
$ ./gradlew :samples:basic-flavors:tasks --all | grep -i 'freeDev'
compileFreeDevKotlinIosSimulatorArm64 - Compiles a klibrary from the 'freeDev' compilation in target 'iosSimulatorArm64'.
iosSimulatorArm64FreeDevBinaries       - Links all binaries for compilation 'freeDev' of target 'iosSimulatorArm64'.
iosSimulatorArm64FreeDevKlibrary       - Assembles outputs for compilation 'freeDev' of target 'iosSimulatorArm64'
iosSimulatorArm64FreeDevProcessResources - Processes file collection.
desktopFreeDevClasses                  - Assembles outputs for compilation 'freeDev' of target 'desktop'
```

KGP correctly registers the full per-variant task graph on `KotlinNativeTarget` — `klibrary` assembly + `Binaries` linking lifecycle integrates automatically. The probe's `compileFreeDevKotlinIosSimulatorArm64` then ran and progressed past dependency download (`downloadKotlinNativeDistribution`), past `compileKotlinIosSimulatorArm64`, and into the freeDev compilation step itself before failing on a **sample-content** issue:

```text
e: file://…/samples/basic-flavors/src/commonDev/kotlin/.../DevTools.kt:32:25
   Unresolved reference 'System'.
```

`DevTools.kt` references `java.lang.System` — JVM-only, valid on Desktop but not on iOS Native. **This failure is sample source quality, not v2.0 infrastructure.** The KGP/Native pipeline for per-variant compilations is feasible.

**Provisional answer (REVISED from plan)**: iOS targets are **in scope for v2.0 GA**, not deferred to v2.1. W1 acceptance test will verify on a clean iOS-compatible `commonDev` source set.

- Per-platform realities (post-spike):
  - **JVM (Desktop)**: proven — D1 spike.
  - **JS / WasmJs**: assumed to work (same KGP compilation engine); verified in W3.
  - **Native (iosArm64 / iosSimulatorArm64 / iosX64)**: per-variant tasks register correctly. Open: does Framework registration (cocoapods integration in `samples/kmp-project-template`) need per-variant naming? Verified in W4.

### Q5 — Migration shim
- A: v2.0 default = active-variant-only (back-compat with v1.x); opt-in to matrix mode via `extension.buildMatrix.set(true)`.
- B: v2.0 default = matrix mode; opt-out via `extension.buildMatrix.set(false)`.
- C: Major version bump (v2.0) signals breaking change; matrix mode is the new default and consumers update or stay on v1.x.
- Decision will hinge on **B5 canary telemetry** (which apps actively need multi-variant non-Android builds — stakeholder survey before final lock).
- **Provisional answer**: **A** — opt-in `buildMatrix.set(true)`, default off.

### Q6 — Publish artifacts per variant
Superseded by **Q21** (per-variant publishing — closes AGP parity gap). See Q21.

### Q7 — Configuration cache compatibility

**Spike-locked answer.** Live probe (2026-05-13) — ran the same command twice with `--configuration-cache --rerun-tasks`:

```text
=== RUN 1 ===
BUILD SUCCESSFUL in 34s
Configuration cache entry stored.

=== RUN 2 ===
Reusing configuration cache.
BUILD SUCCESSFUL in 6s
Configuration cache entry reused.
```

Cold 34s → warm 6s. Per-variant compilation **is** configuration-cache compatible on the spike's design.

**Acceptance criterion**: matrix mode must achieve ≥95% configuration-cache hit rate on the second build. Probe outcome: trivially met (100% on this sample). W2 acceptance test will validate on a multi-module consumer.

### Q8 — Build time / performance SLO

**Spike-locked answer.** Live probe (2026-05-13) on `samples/basic-flavors`:

```text
=== Q8.1 baseline (compileKotlinDesktop only) ===
BUILD SUCCESSFUL in 15s
wall clock: 16.047s

=== Q8.2 2-compilation (compileFreeDevKotlinDesktop + compileKotlinDesktop) ===
BUILD SUCCESSFUL in 15s
wall clock: 16.170s
```

Ratio: **1.008× single-variant for a 2-compilation matrix on this sample**. Well under the ≤2× SLO.

**Caveat**: this measurement is dominated by Gradle configuration overhead on a tiny sample with `associateWith(main)` reusing main's classpath. A real 4-variant × 4-module consumer build will land closer to the SLO. W3 builds the proper performance regression suite on `samples/matrix-mode/` (new sample) covering 6 variants on Desktop, JS, WasmJs, iOS.

### Q9 — IDE integration & DX

- Open questions:
  - Does IntelliJ surface `compileFreeDevKotlinDesktop` distinctly from `compileKotlinDesktop` in the Gradle tool window?
  - Does "Build → Run with Variants" UI need a new selector?
  - Does the IDE's source set view correctly attribute files in `src/freeDev/` to the right compilation?
- Mitigation: group v2.0 tasks under a Gradle task group (`kmpFlavors variants`) so `./gradlew tasks --group="kmpFlavors variants"` is the discovery path; ungrouped `./gradlew tasks` stays clean.
- Action: take screenshots of IDE behaviour during W3. Embed in `docs/MATRIX_MODE.md`.

**Provisional answer**: task group named `kmpFlavors variants` is mandatory; screenshots in the GA docs.

### Q10 — Test compilations per variant

- Open questions:
  - Should v2.0 register `compileFreeDevTestKotlinDesktop` per variant?
  - Should the test compilation `associateWith` the variant's main compilation so test code resolves variant-specific BuildKonfig?
  - What about `kotlinx.serialization` codegen — does it work per-variant?
- **Provisional answer**: yes to per-variant test compilations; mirror main compilation registration pattern; verify via a test-only spike during W1.

### Q11 — `expect`/`actual` across variants

KMP's `expect`/`actual` pattern is critical. v2.0 must support:
- `commonMain` declares `expect fun foo(): String`
- `commonFree` provides `actual fun foo() = "free"`
- `commonPaid` provides `actual fun foo() = "paid"`

…and the per-variant compilation must pick up the right `actual` automatically.

- Open question: does this work out-of-the-box with Hierarchy Template + per-variant `KotlinSourceSet`, or does v2.0 need explicit `actual`-routing config?
- **MUST verify** in W1 with a unit test in `samples/basic-flavors`.

### Q12 — Cross-variant code isolation

Risk: a developer in `commonFree/SomeFile.kt` accidentally imports a symbol from `commonPaid/OtherFile.kt`. Compiles in v1.x active-variant-only because the inactive sibling isn't on the classpath. In v2.0 matrix mode, both compilations run — what happens?

- Expected behaviour: `commonFree` and `commonPaid` are sibling source sets, neither `dependsOn` the other. So a compile error should occur. But this depends on Q2 source-set strategy.
- **Acceptance criterion**: v2.0 must produce a compile error when `commonFree` references `commonPaid` symbols. Include a negative test in the integration suite.

> **Spike caveat surfaced (2026-05-13)**: running `compileFreeDevKotlinDesktop` on the spike branch produced a KGP warning `⚠️ Unused Kotlin Source Sets: commonPaid, iosDev, iosFree, nativeDev, nativeFree`. In v1.x active-variant-only mode that's expected. In v2.0 matrix mode those source sets must be wired into their respective variant compilations; the warning becomes the canary for incomplete plugin wiring.

### Q13 — Telemetry & observability

v1.x logs `[KMP Flavors] Active variant: freeDev` once at configuration. v2.0 needs richer observability:

- One info-level line per registered variant compilation (`[KMP Flavors] Registered variant compilation: freeDevDesktop, paidDevDesktop, …`)
- A summary task `./gradlew :module:listVariantCompilations` that prints the matrix as a table.
- Optional `-PkmpFlavors.debug=true` verbose mode that traces source-set construction.
- Gradle Build Scan (`--scan`) integration: every variant compilation surfaces as its own task in the scan.

### Q14 — Compatibility matrix

- Minimum KGP version: **2.1+** (spike used 2.3.20 + KGP 2.3.20). Hierarchy Template stabilized at KGP 2.1.
- Minimum AGP version: unchanged from v1.x (AGP 8.x — v2.0 doesn't touch Android).
- Minimum Gradle version: unchanged from v1.x (Gradle 8.5+; spike currently on Gradle 9.5).
- Compose Multiplatform: 1.10+ (proven by sample).
- Java toolchain: 17+ (consistent with v1.x).
- Published in README and enforced by `KmpFlavorPlugin.apply()` guard (fail fast with clear message on unsupported versions).

### Q15 — Deprecation policy for v1.x

- v2.0 ships → v1.x continues to receive critical-fix releases for **6 months**.
- After 6 months → v1.x is EOL; consumers must migrate.
- v2.0 README has a "Migrating from v1.x" section with a step-by-step diff.
- v1.1.6's `docs/ROLLBACK.md` is the rollback anchor for any v2.x → v1.x emergency.

### Q16 — Single-point opt-in for matrix mode (direct child of the Design Tenet §1.1)

WHERE does `buildMatrix=true` live?

- **A** (recommended): Root-level Gradle property in `gradle.properties`:
  ```properties
  kmpFlavors.buildMatrix=true
  ```
  Pros: zero-Kotlin-code change; CI can toggle via `-PkmpFlavors.buildMatrix=true`; best ergonomics for surveys.
  Cons: project-wide; can't selectively opt in for one consumer module.
- **B**: `KmpFlavorExtension.buildMatrix` set in the consumer's convention plugin:
  ```kotlin
  extensions.configure<KmpFlavorExtension> { buildMatrix.set(true) }
  ```
  Pros: stays in Kotlin DSL; co-located with `buildConfigPackage` etc.
  Cons: harder to override per-CI-job.
- **C**: Hybrid — gradle.properties as default; extension property overrides.

**Provisional answer**: **C** (hybrid). gradle.properties is the canonical opt-in; convention-plugin extension can override per-project. Both sit at a SINGLE consumer touch-point — no per-module diff.

**Acceptance**: opting a project into matrix mode requires editing exactly ONE file (`gradle.properties` OR the convention plugin); the change must propagate to every KMP module via auto-detection.

### AGP Parity Gap — what AGP product flavors do that v2.0 must consider

| AGP capability | v1.x state | v2.0 plan status (before this gap analysis) | Decision needed |
|---|---|---|---|
| Per-variant compilation matrix | ❌ active-only | ✅ Q1-Q4 |  |
| Per-variant `BuildConfig` fields | ✅ has it | ✅ Q3 |  |
| Per-variant source sets (`src/<flavor>/…`) | ✅ has it for active | ✅ Q2 |  |
| Per-variant test compilations | ❌ active-only | ✅ Q10 |  |
| `expect`/`actual` per variant | ⚠ untested | ✅ Q11 |  |
| Cross-variant isolation | ⚠ untested | ✅ Q12 |  |
| Per-variant dependencies | ⚠ inactive vars don't resolve | ❌ NOT in plan | **Q17 (new)** |
| Aggregate `assembleAll` task | ❌ no equivalent | ⚠ mentioned in Q8 | **Q18 (new)** |
| Variant API (`variants.all { … }`) | ❌ no equivalent | ❌ NOT in plan | **Q19 (new)** |
| Variant filtering | ❌ no equivalent | ❌ NOT in plan | **Q20 (new)** |
| Per-variant publishing | ❌ single artifact | ⚠ Q6 said status quo | **Q21 (revise Q6)** |
| Per-variant resources | ⚠ Android-only AGP | 📋 deferred to v2.1 |  |

**Verdict**: 5 gaps to close in v2.0 (Q17-Q21) for true AGP parity. Q21 supersedes Q6.

### Q17 — Per-variant dependencies (NEW — closes AGP parity)

In AGP:
```kotlin
dependencies {
    freeImplementation("io.free-only:lib:1.0")
    paidImplementation("io.paid-only:lib:1.0")
}
```

In KMP the analogous DSL is:
```kotlin
kotlin {
    sourceSets {
        val commonFreeMain by getting { dependencies { implementation("io.free-only:lib:1.0") } }
        val commonPaidMain by getting { dependencies { implementation("io.paid-only:lib:1.0") } }
    }
}
```

v1.x state: the DSL is accepted by Kotlin but only the **active** variant's source set is on the classpath, so `commonPaidMain.dependencies { }` is a no-op when `-PkmpFlavor=freeDev`.

v2.0 requirement: in matrix mode, BOTH `commonFreeMain` and `commonPaidMain` compilations must resolve their respective dependencies independently.

- Acceptance: a sample with `freeOnly:1.0` and `paidOnly:1.0` artifacts must compile both variants without classpath leakage (importing `paidOnly` in a `commonFree` file must fail).
- **MUST verify** via an integration test in W1.

### Q18 — Aggregate `assembleAllVariants` task (NEW — closes AGP parity)

- A: One aggregate task per target: `./gradlew :module:assembleAllDesktopVariants` → runs all variant compilations of that target in parallel.
- B: A super-aggregate `./gradlew :module:assembleAllVariants` walking every detected target × every variant.
- C: Both A and B (B depends on A across targets).
- **Provisional answer**: **C**. Per-target aggregates for CI sharding; super-aggregate for developer convenience.
- Tasks land in the `kmpFlavors variants` task group (Q9).

### Q19 — Variant API for programmatic customization (NEW)

- A: No variant API in GA. Defer to v2.1.
- B: Expose `kmpFlavors.variants` as a Gradle `NamedDomainObjectCollection<KmpFlavorVariant>`:
  ```kotlin
  kmpFlavors.variants.matching { it.flavors.contains("paid") }.configureEach {
      // attach a per-variant verification task, override BuildConfig field, etc.
  }
  ```
- C: Callback API: `kmpFlavors.onEachVariant { variant -> … }`.
- **Provisional answer**: **B** in v2.0 GA — collection model matches Gradle convention.
- `KmpFlavorVariant` minimum fields: `name`, `flavors: List<String>`, `buildType: String`, `targets: Set<KotlinTarget>`, `compilations: Map<KotlinTarget, KotlinCompilation<*>>`.

### Q20 — Variant filtering (NEW)

- A: `kmpFlavors.variantFilter { … }` block (AGP-style):
  ```kotlin
  kmpFlavors {
      variantFilter {
          if (flavors.contains("paid") && buildType == "staging") {
              setIgnore(true)
          }
      }
  }
  ```
- B: `flavors { register("paid") { incompatibleWith("staging") } }` builder DSL.
- C: Delegate to Q19 variant API: `kmpFlavors.variants.matching { … }.configureEach { excluded.set(true) }`.
- **Provisional answer**: **A** — closer to AGP DSL ergonomics; lower bar for migrating Android consumers.
- Filtered variants must NOT register compilations or BuildConfig.
- `./gradlew :module:listFlavors` shows `paidStaging (filtered out)` for discoverability.

### Q21 — Per-variant publishing (REVISION OF Q6 — closes AGP parity)

- A: Status quo. One published JAR per target. (Original Q6 answer.)
- B: Classifier-based publishing — `flavor-plugin-2.0.0.jar` (default) + `flavor-plugin-2.0.0-freeDev.jar` (variant classifier) on the same Maven coordinate.
- C: Per-variant Maven modules — `flavor-plugin-freeDev:2.0.0`, `flavor-plugin-paidProd:2.0.0`.
- D: Hybrid — Maven Central publishes A (single jar); Plugin Portal publishes A; opt-in `kmpFlavors.publishMatrix.set(true)` enables B for consumer-library publishing.
- **Provisional answer**: **D**. Plugin itself stays single-published for back-compat; v2.0 ships the *mechanism* (B) so consumer libraries can opt in.
- Tasks: `./gradlew :module:publishFreeDevPublicationToMavenLocal` etc. — auto-generated by maven-publish + the Q19 variant API.
- Open: does `vanniktech.maven-publish` tolerate per-variant publications? Spike during W4.

### Q22 — Error handling, partial failure & diagnostics (NEW)

- A: Fail-fast — first variant failure aborts the matrix.
- B: Continue-on-failure (AGP behaviour).
- C: Configurable via Gradle's standard `--continue` flag.
- **Provisional answer**: **C** — match Gradle default `--continue=false`; let CI matrix jobs pass `--continue` when reporting all failures matters.
- **Error attribution**: every compile failure must include the variant + target in the header. E.g. `> Task :module:compileFreeDevKotlinDesktop FAILED` → `What went wrong: variant=freeDev target=desktop`. Verified by integration test.
- **Diagnostic task**: `./gradlew :module:diagnoseVariant freeDev` prints the resolved source-set tree, classpath, BuildConfig fields, and filters for one variant.

### Q23 — Configuration validation (NEW)

- A consumer typo (`flavors { register("freev") }` instead of `freeDev`) — v1.x silently ignores. v2.0 fails with `"unknown flavor 'freev' — did you mean 'freeDev'? Registered flavors: [free, paid]"`.
- A flavor with the same name as a buildType → error.
- A `variantFilter` excluding ALL variants → error ("no buildable variants after filter").
- A module with zero KMP targets → warn + skip (no-op).
- A `buildConfigField` with an invalid type → existing v1.x check; preserve.
- A flavor with no dimension assigned → error.

- **Provisional answer**: every invalid configuration is caught by `KmpFlavorPluginValidator.kt` (new class). Each validation has a stable error code (`KMPF-Vxx`), human message, and concrete fix. Catalogued in `docs/ERROR_CODES.md`.

### Q24 — Adjacent-plugin compatibility (NEW)

| Plugin | Concern | Verified in |
|---|---|---|
| `vanniktech.maven-publish` | Per-variant publishing (Q21 D) | W4 |
| Compose Multiplatform | Per-variant Compose resources, per-variant BuildKonfig | W3 |
| `kotlinx-serialization` | Codegen per variant via KSP | W1 |
| Spotless | Format every variant's generated `BuildKonfig.kt`? | Doc only |
| Detekt | Run on every variant? Or merged? | Doc only |
| `dependency-guard` | Per-variant dependency baselines | W4 |
| Kover (coverage) | Coverage per-variant or merged? | Doc only |
| `kotlinx.atomicfu` | Compilation hooks may not handle N compilations | W1 |
| Hot-reload (Compose) | Active-variant only OR all? | Documented GA limitation: hot-reload only on the `gradle.properties`-pinned variant; v2.1 explores per-variant hot-reload. |

Every entry must end the cycle with ✅ compatible or ⚠ documented limitation in `docs/MATRIX_MODE.md` before v2.0 GA.

### Q25 — Edge cases & degenerate configurations (NEW)

- **Zero targets**: a module with no `kotlin { jvm() / iosX64() / … }` declared → no compilations registered, info log, skip cleanly.
- **One target only**: register variants on that one target only.
- **No flavors, only buildTypes**: register `debug` + `release` as variants on each target.
- **No buildTypes, only flavors**: register one variant per flavor.
- **Neither**: degenerate "active = main" → fall back to v1.x single-compilation behaviour.
- **Active variant ≠ any registered variant**: `-PkmpFlavor=ghost` → fail with the Q23 unknown-flavor error.

Each case: integration test in W1.

### Q26 — Migration tooling — codemod for v1.x → v2.0 consumers (NEW)

- A: Manual upgrade — switch from v1.x flat DSL to v2.x `register("name") { isDefault.set(true) }`.
- B: `./gradlew kmpFlavorsMigrateToV2 --dry-run` task printing the exact consumer diff.
- C: A separate `migrate-kmp-flavors-v2.sh` script in `scripts/`.
- **Provisional answer**: **B + A**. Code-driven migration check (B) catches long-tail issues; doc (A) covers conceptual changes.
- Migration output: structured JSON when `--json` flag is set so CI can validate without prose parsing.
- Acceptance: running migrate on `samples/kmp-project-template` produces a clean (no-op) diff at v2.0 GA.

---

## 3.5. Gap Analysis — End-to-End Critical Review

| # | Gap | Disposition |
|---|---|---|
| G1 | Partial-failure semantics + per-variant error attribution | ✅ Q22 |
| G2 | Configuration-validation rules + error code catalog | ✅ Q23 |
| G3 | Adjacent-plugin compatibility | ✅ Q24 |
| G4 | Edge cases | ✅ Q25 |
| G5 | Migration codemod / tooling | ✅ Q26 |
| G6 | Test-strategy section unifying Q10/Q11/Q12 + TestKit expansion + performance regression suite | ✅ Test Strategy below |
| G7 | Documentation plan beyond a file list | ✅ Documentation Plan below |
| G8 | Versioning rationale (why v2.0 vs v1.2) | ✅ Versioning Rationale below |
| G9 | Beta → RC → GA gate criteria | ✅ §5 Release Cadence |
| G10 | Risk register lacks ownership column | ✅ §6 updated |
| G11 | Definition of Done lacks "merged + tagged + announced" | ✅ §7 updated |
| G12 | W1→W6 lacks inter-week dependency arrows | ✅ §5 updated |
| G13 | Recommended path is a flat table without prose synthesis | ✅ §4 |
| G14 | Spike artifact lifecycle (merge / delete / keep?) | ⏸ Deferred — explicit decision in §8 |
| G15 | B5 canary informs Q5; B5 out of scope | ✅ Stakeholder Survey step in §9 |
| G16 | Build cache key growth with N variants | ✅ Performance & Scaling below |
| G17 | Memory footprint per concurrent variant compilation | ✅ Performance & Scaling below |
| G18 | RFC reviewer SLA, sign-off authority | ⏸ Handled at draft-PR review time |
| G19 | Public RFC announcement | ⏸ Comms task post-RFC sign-off |
| G20 | CI matrix expansion in plugin's own pipeline | ✅ CI Update below |
| G21 | SBOM / supply-chain implications of N-times dep footprint | ⏸ Deferred — v2.1 if real concern surfaces |
| G22 | IDE Run Configurations per variant | ⏸ Deferred to v2.1 |
| G23 | Hot-reload + variant matrix interaction (Compose) | ✅ Q24 — documented GA limitation |
| G24 | Variant tasks under `--scan` integration | ✅ §5 W3 |

### Test Strategy (G6)

Four test tiers:

1. **Unit tests** — `PlatformDetectorTest`, `SourceSetConfiguratorTest`, `KmpFlavorPluginValidatorTest`, `KmpFlavorVariantTest`. Mocking via `ProjectBuilder`.
2. **Integration tests (TestKit)** — `./gradlew tasks --all` outputs, `compileFreeDevKotlinDesktop` invocation, classpath-leakage negative test (Q17), cross-variant isolation negative test (Q12), variant filter test (Q20), expect/actual test (Q11).
3. **Sample-build verification** — `samples/basic-flavors` + new `samples/matrix-mode/` + `samples/kmp-project-template`, exercised by `pr-check.yml` on every PR.
4. **Performance regression suite** — `build-logic/flavor-plugin/perf/` (new). Captures single-variant and matrix-mode build times. CI fails if matrix-mode regresses >2× baseline (Q8 SLO).

Code volume estimate: ~2000 LoC. Coverage acceptance: ≥85% of plugin source by W6.

### Documentation Plan (G7)

| Doc | Path | Format | Status |
|---|---|---|---|
| Migration guide v1→v2 | retired in v2.8 — single-version doc set | Markdown + concrete diff blocks | Historical |
| Matrix Mode reference | `docs/MATRIX_MODE.md` | Markdown + screenshots (Q9) + DSL examples | New |
| Error codes catalog | `docs/ERROR_CODES.md` | `KMPF-Vxx` → message → fix | New |
| Adjacent-plugin compat | embedded in `docs/MATRIX_MODE.md` | Compatibility table | New |
| RFC document | `docs/RFC-v2.0-per-variant-compilation.md` | This file | This RFC |
| README "Matrix mode" section | `README.md` | 1-page intro + link | Updated |
| CHANGELOG v2.0 entry | `CHANGELOG.md` | Keep-a-changelog format | Required at GA |
| ADRs | `docs/adr/v2.0/*.md` | Nygard-style; one per top-3 designed Qs (Q1, Q2, Q5) | New convention |

Diagrams (Mermaid in `MATRIX_MODE.md`):
- Task graph (compile tasks per variant per target).
- Source-set inheritance graph (commonMain → commonFree → commonFreeMain → freeDevDesktopMain).
- Configuration cache flow.

### Versioning Rationale (G8)

Why v2.0 and not v1.2?

- v1.x semantic contract: "BuildConfig for the **active** variant; one compilation per target."
- v2.0 semantic contract: "with `buildMatrix.set(true)`, BuildConfig for **every** variant; N compilations per target."
- Even though Q5 ships matrix mode as opt-in (default = v1.x behaviour), the API surface and acceptable consumer patterns expand significantly.
- v2.0 chosen because:
  - **Q15 deprecation policy** is conceptually a major-version event.
  - The variant API (Q19) is new public API.
  - The classifier-publishing mechanism (Q21) is new public API.
  - The variant filter DSL (Q20) is new public API.
  - Documentation & tooling investment justifies a major bump.

### Performance & Scaling (G16, G17)

Beyond the build-time SLO (Q8):

- **Memory footprint**: peak heap during 6-variant matrix build ≤ 1.5× single-variant heap on the reference setup (`samples/basic-flavors` on a 4-core / 16 GB laptop). Measured via Gradle's `-Dorg.gradle.jvmargs=-XX:+HeapDumpOnOutOfMemoryError`.
- **Build cache scaling**: with `--build-cache`, second build hits cache for every variant. Measured by `--scan` cache hit-rate.
- **Worker saturation**: `--max-workers=4` on a 6-variant module must not deadlock. Smoke test in W3.

### CI Update (G20)

The plugin's `pr-check.yml` must validate v2.0 on every PR:

- Add a `Sample (matrix-mode)` job that runs `./gradlew :samples:matrix-mode:assembleAllVariants` and asserts every variant's `BuildKonfig.kt` exists with expected content.
- Extend `kmp-project-template sample build` to flip `kmpFlavors.buildMatrix=true` in `gradle.properties` and verify the sample still builds.
- Cost: ~3 additional min per PR.

---

## 4. Recommended path — prose synthesis

v2.0 commits to **AGP-parity matrix mode for KMP non-Android targets**, opt-in, with **zero per-module consumer changes**. The Design Tenet (§1.1) is the non-negotiable architectural commitment; every other decision is derivative.

**The shape of v2.0 GA:**

A consumer opts in by editing **one file**: either `gradle.properties` (`kmpFlavors.buildMatrix=true`, Q16-A) or the convention plugin (`buildMatrix.set(true)`, Q16-B). Hybrid resolution (Q16-C) means the convention plugin can override the property per project.

From that moment, the plugin's `apply()` walks every detected `KotlinTarget` in every module that applies the convention. For each `(variant × target)` pair it programmatically calls `target.compilations.create("freeDev") { … }` (Q1-B naming, Q2-C hybrid source-set strategy). A dedicated `generateFreeDevBuildKonfig` task per variant (Q3-A) feeds its output into the matching compilation's source set. Per-variant test compilations (Q10) and per-variant dependencies (Q17) flow naturally from the same loop. The variant API (Q19-B) exposes the result as `kmpFlavors.variants` for advanced customization; the variant filter (Q20-A) is the AGP-style escape hatch.

The whole spike confirmed three load-bearing pieces:

- **iOS path is feasible** (Q4 — `compileFreeDevKotlinIosSimulatorArm64` and its klibrary/binary lifecycle register correctly). iOS moves from v2.1 deferred to v2.0 GA scope.
- **Configuration cache is compatible** (Q7 — 34s cold → 6s warm, "Configuration cache entry reused"). v2.0 doesn't need a feature flag to disable config cache.
- **Build-time overhead is small** (Q8 — 1.01× on a 2-compilation matrix). The ≤2× SLO is comfortably reachable, though the real test is W3's 6-variant performance suite.

**What ships in v2.0 GA** (the AGP-parity rubric in §3 table):

| Capability | Decision | Where defined |
|---|---|---|
| Per-variant compilation matrix | Q1-B + Q2-C + Q3-A | Plugin internals |
| Per-variant `BuildConfig` fields | Q3-A | Plugin internals |
| Per-variant test compilations | Q10 yes | Plugin internals |
| `expect`/`actual` across variants | Q11 must-work | Plugin internals |
| Cross-variant isolation | Q12 compile error | Plugin internals (negative test) |
| Per-variant dependencies | Q17 isolated classpath | Plugin internals |
| Aggregate `assembleAll*` tasks | Q18-C | Plugin internals + `kmpFlavors variants` task group |
| Variant API | Q19-B `NamedDomainObjectCollection<KmpFlavorVariant>` | New public API |
| Variant filtering | Q20-A `variantFilter { … }` | New public API |
| Per-variant publishing mechanism | Q21-D (opt-in `publishMatrix.set(true)`) | New public API |
| Diagnostics + error attribution | Q22-C + `diagnoseVariant <name>` task | Plugin internals |
| Configuration validation | Q23 `KmpFlavorPluginValidator` + `KMPF-Vxx` codes | New internal class |
| Adjacent-plugin compat | Q24 — every row ✅ or ⚠ at GA | `docs/MATRIX_MODE.md` |
| Edge cases | Q25 — integration test per case | Test suite |
| Migration tooling | Q26-B+A — `kmpFlavorsMigrateToV2 --dry-run` task | New task |

**What stays unchanged from v1.x**: every consumer's per-module `build.gradle.kts`. The Design Tenet is non-negotiable; §7 includes a byte-equality check between v1.x and v2.0 sample modules.

**What ships deferred to v2.1**: per-variant resources, cross-variant intermediate source sets (e.g. `commonStaging` shared between `freeStaging` + `paidStaging`), per-variant IDE Run Configurations, supply-chain SBOM analysis.

---

## 5. Implementation plan — 6-week milestone breakdown

```text
W1 ──→ W2 ──→ W3 ──→ W4 ──→ W5 ──→ W6
        │       │       │       │
        │       │       │       └→ docs + canary feedback → GA
        │       │       └→ samples + publishing + adjacent plugins
        │       └→ JS/WasmJs/iOS + telemetry + variant API/filter
        └→ BuildConfig per variant + per-variant deps + config cache
W1 → core plugin + integration tests
```

| Week | Focus | Deliverable | Depends on | Owner |
|---|---|---|---|---|
| W1 | Plugin core: per-variant compilation registration on JVM only + `expect`/`actual` (Q11) + isolation (Q12) + edge cases (Q25) + validation (Q23) — all verified via integration tests | Tagged `2.0.0-alpha.1` to mavenLocal; PR opened against `development` (NOT merged) | RFC signed off | tbd |
| W2 | BuildConfig generator — one task per variant (Q3-A); per-variant dependencies (Q17); configuration cache compliance (Q7) | `2.0.0-alpha.2` mavenLocal | W1 source-set strategy locked | tbd |
| W3 | JS + WasmJs + iOS target support; build-time benchmarks (Q8); telemetry surfaces (Q13) incl. `--scan` integration; aggregate tasks (Q18); variant API (Q19); variant filter (Q20) | `2.0.0-alpha.3` mavenLocal | W2 generator design locked | tbd |
| W4 | Sample expansion — new `samples/matrix-mode/`; verify in `samples/kmp-project-template`; per-variant publishing mechanism (Q21); adjacent-plugin compat verifications (Q24) | `2.0.0-beta.1` published to Maven Central | W3 API surface frozen | tbd |
| W5 | Compat shim (Q5 / Q16) — `buildMatrix.set(true)` opt-in API; migration codemod (Q26); IDE screenshots (Q9); error code catalog (Q23 `docs/ERROR_CODES.md`); ADRs for top-3 design Qs | `2.0.0-beta.2` | W4 publishing mechanism stable | tbd |
| W6 | Bug fixes from canary; final docs (README "Matrix mode" section, `MATRIX_MODE.md`); CI updates (G20) | `2.0.0` GA | W5 doc complete + canary green | historical |

### Release cadence — gate criteria

| Milestone | Gate | Measurement |
|---|---|---|
| `2.0.0-alpha.1` (W1 end) | Integration tests green for Q11/Q12/Q23/Q25 | TestKit run output |
| `2.0.0-alpha.2` (W2 end) | Config cache hit rate ≥95% on second invocation | Build scan report |
| `2.0.0-alpha.3` (W3 end) | Build time ≤2× baseline on 4-variant Desktop sample | Benchmark suite |
| `2.0.0-beta.1` (W4 end) | All adjacent plugins (Q24 table) marked ✅ or ⚠ documented | `docs/MATRIX_MODE.md` |
| `2.0.0-beta.2` (W5 end) | `kmpFlavorsMigrateToV2 --dry-run` on `samples/kmp-project-template` produces no-op diff | Migration task output |
| `2.0.0` GA (W6 end) | ≥1 downstream app successfully runs on `2.0.0-beta.2` for ≥1 week | Canary feedback thread |

---

## 6. Risks & mitigations

| Risk | Likelihood | Impact | Owner | Mitigation |
|---|---|---|---|---|
| Kotlin Hierarchy Template doesn't accept N compilations per target | M | H | RFC author | Fall back to manual `KotlinSourceSet` registration (proven by spike) |
| Configuration cache breaks (Q7) | L (this RFC's probe = green) | M | Plugin maintainer | Feature-flag matrix mode during beta; fix before GA |
| iOS framework registration conflicts (Q4) | L (this RFC's probe = green) | M | Plugin maintainer | Per-variant framework naming; verified in W3 |
| Build time regression > 2× (Q8) | L (this RFC's probe = green on 2-compilation; full matrix TBD W3) | M | Plugin maintainer | Parallel compile workers; per-target build cache |
| IDE project-view overwhelmed (Q9) | M | L | RFC author | Task group; document IDE config recommendation |
| `expect`/`actual` resolution incorrect (Q11) | L | H | Plugin maintainer | Block W1 sign-off on unit test pass |
| Cross-variant accidental imports (Q12) | M | M | Plugin maintainer | Negative integration test; CI gate |
| Adoption regression — consumers stay on v1.x | M | M | Maintainer + canary lead | 6-month dual-support (Q15); migration doc; codemod (Q26) |
| Per-variant dependency classpath leakage (Q17) | M | H | Plugin maintainer | Per-variant `compileClasspath` isolation; negative test in W2 |
| Adjacent plugin breaks under matrix (Q24) | M | M | RFC author + plugin maintainer | Verify each plugin in W1-W4; document fallback |
| Migration codemod produces incorrect diff (Q26) | L | M | RFC author | Test on `samples/kmp-project-template`; no-op diff required by W5 |
| Schedule slip (RFC author availability) | M | M | RFC author | Each milestone has explicit gate; slip is visible at each W boundary |
| Reviewer availability for RFC draft PR | M | L | RFC author | Open RFC ≥1 week before W1 start; ping reviewers if no response in 5 business days |

---

## 7. Acceptance criteria for v2.0 GA

**Zero-Touch Adoption (Design Tenet — primary, non-negotiable):**
- [ ] Opting an entire consumer project into matrix mode requires editing **exactly ONE file** (`gradle.properties` OR the convention plugin) — measured by `git diff` on a sample-app upgrade PR.
- [ ] No KMP module's `build.gradle.kts` is touched during the v1.x→v2.0 upgrade in `samples/kmp-project-template`.
- [ ] `samples/kmp-project-template/cmp-shared/build.gradle.kts`, `feature/*/build.gradle.kts`, `core/*/build.gradle.kts` are **byte-identical** before and after enabling matrix mode (only `gradle.properties` differs).
- [ ] Auto-platform-detection: a sample module that adds `js(IR) { browser() }` gets matrix-mode JS variant compilations registered **automatically**, with zero kmpFlavors-DSL changes.
- [ ] Auto-codegen: BuildKonfig produces one `BuildKonfig.kt` per variant per module, registered into each compilation's source set, with zero per-module wiring.
- [ ] A consumer adding a new flavor (`flavors { register("enterprise") { … } }`) only edits the convention plugin; every module picks up the new variant automatically.

**Design questions resolved:**
- [ ] All 26 design questions have final answers documented in this RFC.

**AGP Parity (Q17-Q21):**
- [ ] Per-variant dependencies (Q17): a sample with `freeOnly:1.0` only in `commonFreeMain` and `paidOnly:1.0` only in `commonPaidMain` builds BOTH variants AND importing `paidOnly` in `commonFreeMain` FAILS to compile.
- [ ] Aggregate tasks (Q18): `./gradlew :samples:basic-flavors:assembleAllDesktopVariants` builds all 6 Desktop variants in one invocation; `assembleAllVariants` walks every target × variant.
- [ ] Variant API (Q19): test exercising `kmpFlavors.variants.matching { … }.configureEach { … }` runs at the expected configuration phase.
- [ ] Variant filtering (Q20): `variantFilter { setIgnore(true) when (flavors.contains("paid") && buildType == "staging") }` removes `paidStaging*` from the task graph; `listFlavors` shows `paidStaging (filtered out)`.
- [ ] Per-variant publishing (Q21): with `kmpFlavors.publishMatrix.set(true)`, `publishToMavenLocal` produces default + classifier-tagged artifacts; with `publishMatrix.set(false)` output is byte-identical to v1.x.

**Matrix mode correctness:**
- [ ] `samples/basic-flavors` builds all 6 variants in one Gradle invocation on JVM target.
- [ ] `samples/matrix-mode/` (new sample app) demonstrates matrix mode end-to-end.
- [ ] `samples/kmp-project-template` continues to build cleanly in v1.x-compatible mode (no opt-in to matrix).
- [ ] `expect`/`actual` works across variants (Q11) — automated test.
- [ ] Cross-variant import produces a compile error (Q12) — automated test.
- [ ] Per-variant test compilations work (Q10) — automated test.
- [ ] Configuration cache hit rate ≥95% on second invocation (Q7) — measured in CI.
- [ ] Matrix build time ≤2× single-variant build time (Q8) — measured in CI.
- [ ] Telemetry surfaces in place (Q13).
- [ ] `kmpFlavors variants` task group populated (Q9).
- [x] v1→v2 upgrade documented (collapsed into single-version doc set in v2.8).
- [ ] README has a "Matrix mode" section with at least one screenshot of IDE integration.
- [ ] `KmpFlavorPlugin.apply()` fails fast on unsupported KGP / AGP / Gradle versions (Q14).
- [ ] Compat matrix published in README and CHANGELOG.

**Release closure (G11):**
- [ ] `v2.0.0` tagged on `main`.
- [ ] `v2.0.0` GitHub Release published with release notes derived from CHANGELOG.
- [ ] Maven Central artifact resolved on a clean machine.
- [ ] Plugin Portal listing live.
- [ ] Announcement post (blog / X) — separate comms task (G19).

---

## 8. Decision: GO / NO-GO / DEFER

This RFC requests one of:

- **GO** if: ≥2 consumer apps want multi-variant non-Android matrix builds today **AND** the iOS path is tractable. The latter is now answered (Q4 spike = green). The former requires the stakeholder survey opened post-RFC merge.
- **NO-GO** if: stakeholder survey returns zero consumer demand — close this RFC, document, move on.
- **DEFER** if: demand exists but a follow-up concern (e.g. config cache regression in a real consumer app) surfaces during survey — schedule a 2-week follow-up spike, push v2.0 by one cycle.

**Spike artifact lifecycle decision (G14)**: on v2.0 GA, `spike/d1-per-variant-compilation` is **merged into `main` as a tagged historical reference** (`spike-d1-v2.0-evidence`) and the branch is then deleted. The spike's `samples/basic-flavors/build.gradle.kts` proof artifacts are removed because they were per-module DSL — the v2.0 plugin owns those compilations internally.

---

## 9. Next steps after sign-off

1. **Stakeholder survey** (informs Q5 default): open a GitHub Discussion on `MobileByteLabs/kmp-product-flavors` titled "v2.0 design poll — matrix mode default?". Survey questions: which consumer apps need multi-variant non-Android matrix builds? Which run on iOS today? Which use `freeImplementation`-style per-variant deps? Wait ≥48h before locking Q5.
2. **Open implementation tracker**: a sibling plan in the framework's `plan-layer/plans/` — `2026-MM-DD-kmp-product-flavors-v2-impl.md` — picks up where this RFC ends. PLANS_INDEX additionally lists this implementation plan (G11 follow-on).
3. **B5 canary rollout** (independent track): roll v1.1.6 to 6 consumer apps so real-world telemetry feeds Q5 + the survey above.
4. **W1 kickoff**: once survey closes and Q5 default is locked, RFC author opens `feat/v2.0-alpha.1-core-compilation` branch in plugin repo and starts W1 work per §5.

---

## 10. What this RFC deliberately does NOT solve

- **Implementation** — that's W1-W6 work after sign-off.
- **B5 canary rollout** — separate concern; informs but doesn't gate this RFC.
- **D4 fan-out automation** — separate framework plan.
- **v2.1+ deferrals**: per-variant resources, cross-variant intermediate source sets, IDE Run Configurations, SBOM.

---

## Appendix A — Spike commands (reproducible)

Performed on `spike/d1-per-variant-compilation` HEAD `58ee241`, Gradle 9.5, OpenJDK 21.0.4, macOS Darwin 25 (4-core / 16 GB).

```bash
# Q7 — Configuration cache probe (Desktop variant)
rm -rf .gradle/configuration-cache
./gradlew :samples:basic-flavors:compileFreeDevKotlinDesktop --configuration-cache --rerun-tasks
./gradlew :samples:basic-flavors:compileFreeDevKotlinDesktop --configuration-cache --rerun-tasks

# Q8 — Build-time baseline (Desktop, no build cache)
time ./gradlew :samples:basic-flavors:compileKotlinDesktop --rerun-tasks --no-build-cache
time ./gradlew :samples:basic-flavors:compileFreeDevKotlinDesktop :samples:basic-flavors:compileKotlinDesktop \
    --rerun-tasks --no-build-cache

# Q4 — iOS mini-spike: temporarily add per-variant compilation to iosSimulatorArm64
# in samples/basic-flavors/build.gradle.kts; then
./gradlew :samples:basic-flavors:tasks --all | grep -i 'freeDev'
./gradlew :samples:basic-flavors:compileFreeDevKotlinIosSimulatorArm64 --rerun-tasks
# Restore samples/basic-flavors/build.gradle.kts after the probe.
```

All three probes completed on 2026-05-13 during this RFC's drafting session.
