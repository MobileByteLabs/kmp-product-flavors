# Matrix Mode — Consumer Reference

> v2.0 opt-in feature. Builds every variant × every target in one Gradle invocation. Off by default; v1.x active-variant-only semantics are preserved.

This document is the consumer-facing reference for **matrix mode**. For the design rationale see [`RFC-v2.0-per-variant-compilation.md`](RFC-v2.0-per-variant-compilation.md).

---

## TL;DR

```kotlin
// In your convention plugin OR gradle.properties — single touch-point per project.
kmpFlavors {
    buildMatrix.set(true)               // OR set `kmpFlavors.buildMatrix=true` in gradle.properties
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
}
```

After enabling, run:

```bash
./gradlew compileKotlinDesktop                       # active variant (free)
./gradlew compilePaidKotlinDesktop                   # inactive variants — each one a task
./gradlew assembleAllDesktopVariants                 # all variants on Desktop
./gradlew assembleAllVariants                        # all variants on every target
./gradlew tasks --group="kmpFlavors variants"        # discover everything
./gradlew generateVariantRunConfigurations          # v2.1 — emit .run.xml per (variant × target)
```

## Single-point opt-in (RFC §3 Q16-C)

Two equivalent forms — pick whichever fits your convention plugin / CI ergonomics:

| Where | DSL |
|---|---|
| Project-wide default | `gradle.properties: kmpFlavors.buildMatrix=true` |
| Per-project override | `kmpFlavors { buildMatrix.set(true) }` (extension wins on conflict) |

**Order constraint**: when configuring per-flavor source-set dependencies, the `kmpFlavors { flavors { register(...) } }` block must appear BEFORE any `kotlin { sourceSets { val commonPaid by getting } }` block that references per-flavor source sets. The plugin registers `commonFree` / `commonPaid` source sets eagerly as flavors are registered, so consumer DSL must register flavors first.

---

## What matrix mode adds

| Surface | What it does | Reference |
|---|---|---|
| `compile{Variant}Kotlin{Target}` tasks per inactive variant × target | KGP-auto-generated from `compilations.create("{variantName}")` | RFC §3 Q1-B |
| `assembleAll{Target}Variants` task per target | Runs the target's `main` (active variant) + every inactive variant's compilation | RFC §3 Q18-C |
| `assembleAllVariants` super-aggregate | Walks every per-target aggregate | RFC §3 Q18-C |
| `generate{Variant}BuildConfig` task per inactive variant | Outputs `build/generated/kmpFlavors/{variantName}/kotlin/...` | RFC §3 Q3-A |
| `kmpFlavors.variants` public API | `NamedDomainObjectContainer<KmpFlavorVariant>` for `matching { … }.configureEach { … }` | RFC §3 Q19-B |
| `variantFilter { setIgnore(true) }` DSL | AGP-shaped filter; `buildType == "staging"` works | RFC §3 Q20-A |
| `publishMatrix` opt-in | Classifier-tagged Maven publications per (variant × target): JVM (v2.0), iOS klib (v2.1), JS / WasmJs (v2.1). See [PUBLISHING.md](PUBLISHING.md) for the full catalog. | RFC §3 Q21-D / v2.1 Phase 5 |
| `dependencyGuardPerVariant` opt-in (v2.1) | Auto-registers per-(variant × target) `dependencyGuard.configuration(...)` baselines | Q24 / v2.1 Phase 4 |
| `excludeGeneratedFromFormatters` opt-in (v2.1) | Auto-excludes generated `BuildKonfig` paths from Spotless + Detekt | Q24 / v2.1 Phase 4 |
| `detektPerVariant` opt-in (v2.1) | Registers `detekt{Variant}` task per variant with per-variant baselines | Q24 / v2.1 Phase 4 |
| `generateVariantRunConfigurations` task (v2.1) | One `.run.xml` per (variant × target) for IDE run-config dropdown | G22 / v2.1 Phase 4 |

---

## Per-variant resources (v2.1+)

Matrix mode supports per-variant resources on **both** Compose Multiplatform and Android targets. The conventions are independent — drop files in the right directory and the variant compilation picks them up; the plugin doesn't add a DSL surface.

### Compose Multiplatform (`composeResources/`)

The Compose Multiplatform plugin (`org.jetbrains.compose` v1.7+) auto-discovers `composeResources/` under any Kotlin source set. Matrix mode's per-flavor source sets (`commonFree`, `commonPaid`, etc.) ARE Kotlin source sets, so the convention applies:

```
src/
├── commonMain/composeResources/values/strings.xml   # base value
├── commonFree/composeResources/values/strings.xml   # override for free variant
└── commonPaid/composeResources/values/strings.xml   # override for paid variant
```

Merge precedence: **leaf source set wins on duplicate keys**. For `app_name`:
- Active variant `free` compiles through `compileKotlinDesktop` → sees `commonMain` + `commonFree` → `app_name` = "free".
- Inactive variant `paid` compiles through `compilePaidKotlinDesktop` → sees `commonMain` + `commonPaid` → `app_name` = "paid".
- Cross-variant isolation holds (Q12): the paid variant never sees `commonFree` resources.

No `kmpFlavors` DSL needed. The plugin emits a lifecycle line at apply time announcing the per-flavor paths when CMP is detected.

### Android (`res/<flavor>/`)

AGP handles per-flavor `res/` natively once the KMP→AGP bridge propagates flavors into `productFlavors { … }`. This works **unchanged from v1.x**:

```
src/
├── androidMain/res/values/strings.xml         # base
├── free/res/values/strings.xml                # AGP-discovered for the `free` flavor
└── paid/res/values/strings.xml                # AGP-discovered for the `paid` flavor
```

Per the AGP source-set discovery rules, `src/free/res/...` is automatically picked up by any variant whose flavor includes `free` (e.g., `freeDebug`, `freeStaging`, `freeProd`). Per-build-type, per-variant, and combined flavor-buildType directories (`src/freeDebug/res/...`) are also supported by AGP. The KMP-flavors plugin's `bridgeAgpProductFlavors`/`bridgeAgpBuildTypes` flags propagate the flavors/build types into AGP so these conventions Just Work.

### iOS / native targets

Per-variant resources on iOS / native targets flow through the same Kotlin source-set hierarchy. CMP's `composeResources/` convention applies to ALL non-Android targets, so iOS Compose apps get per-variant strings/images via the same `src/commonFree/composeResources/` path.

### Summary table

| Target family | Convention path | Resolver |
|---|---|---|
| Compose Multiplatform (Desktop, iOS, JS, WasmJs) | `src/common{Flavor}/composeResources/` | CMP `Res.string.x` accessor — leaf source set wins |
| Android | `src/{flavor}/res/values/strings.xml` | AGP native; KMP-flavors AGP bridge propagates flavors |
| iOS native (non-Compose) | `src/common{Flavor}/resources/` (Kotlin source-set resources) | Standard KMP source-set resource merging |

---

## What matrix mode preserves (v1.x semantics)

- The **active variant** (per `isDefault` or `-PkmpFlavor=…`) continues to compile through the standard `compileKotlin{Target}` task. Matrix mode adds compilations for **inactive** variants alongside.
- Consumer per-module `build.gradle.kts` is **byte-identical** between v1.x and v2.0 when matrix mode is enabled. The Zero-Touch Adoption design tenet (RFC §1.1) is a non-negotiable architectural commitment.
- v1.x source-set wiring (`commonFree.dependsOn(commonMain)` for the active flavor, AGP bridge, BuildConfig codegen, listFlavors / validateFlavors tasks) all keep working unchanged.

---

## Adjacent-plugin compatibility (Q24)

| Plugin | Status | Notes |
|---|---|---|
| `maven-publish` | ✅ Tested | Per-variant `MavenPublication` registered via `publishMatrix.set(true)`. Standard `publishVariant{X}PublicationToMavenLocal` tasks derived by Gradle. |
| `com.vanniktech.maven.publish` | ✅ Compatible (delegated) | Delegates to `maven-publish`; our `withId("maven-publish")` hook fires regardless of which plugin applied it. Smoke-tested via the W4 `samples/matrix-mode/` sample app — TestKit can't satisfy vanniktech's KotlinBasePlugin classpath, so the unit-level test is `@Disabled`. |
| `org.jetbrains.compose` (Compose Multiplatform) | ✅ Compatible (v2.1 adds per-variant resources) | Per-variant compilations honor Compose's own source-set hierarchy. **Per-variant `composeResources/` work end-to-end** via the source-set convention (v2.1 — see "Per-variant resources" above). **Hot-reload is still active-variant only** at v2.1 GA; per-variant hot-reload is v2.2 scope. |
| `org.jetbrains.kotlin.plugin.serialization` | ✅ Compatible | KSP codegen runs per compilation; per-variant compilations each get their own generated sources. |
| `org.jetbrains.kotlinx.atomicfu` | ✅ Compatible | Atomicfu's compilation transformer runs per `KotlinCompilation`, including ours. |
| `dependency-guard` | ✅ Helper API (v2.1) | Set `kmpFlavors { dependencyGuardPerVariant.set(true) }` to auto-register one `dependencyGuard.configuration(...)` entry per (variant × target). Without the opt-in, consumers can still wire them manually. |
| `com.diffplug.spotless` | ✅ Helper API (v2.1) | Set `kmpFlavors { excludeGeneratedFromFormatters.set(true) }` to auto-exclude `build/generated/kmpFlavors/` from every Spotless task. Without the opt-in, add the exclude pattern manually. |
| `io.gitlab.arturbosch.detekt` | ✅ Helper APIs (v2.1) | `kmpFlavors { excludeGeneratedFromFormatters.set(true) }` excludes per-variant codegen from Detekt scans. `kmpFlavors { detektPerVariant.set(true) }` registers a `detekt{Variant}` task per variant with per-variant baselines under `config/detekt/{variant}/baseline.xml` — Lint-per-variant for non-Android targets. |
| Kover | ✅ Per-variant coverage (manual scope) | Each variant compilation produces its own coverage; Kover merges them automatically. To scope reports per variant, set `kover.useReportSet(...)`. The plugin does not auto-configure this because the right scope depends on whether the consumer wants per-variant or merged reporting. |

---

## Opt-out at any time

Setting `buildMatrix.set(false)` (or removing the opt-in entirely) restores v1.x behaviour byte-for-byte. No per-module consumer file needs to change.

---

## Where matrix mode does NOT apply

| Excluded | Why |
|---|---|
| Android JVM target (`androidTarget()`) | RFC §1 non-goal: "Change Android target behaviour (AGP already handles matrix; we don't touch it)." |
| Synthetic `metadata` target | KGP rejects `compilations.create()` on this target. |
| Modules with zero flavors registered | `KmpFlavorPluginValidator` raises `KMPF-V08` when `buildMatrix=true` AND zero flavors. |
| Modules without any KMP target | `KmpFlavorPluginValidator` raises `KMPF-V05` warning. |

---

## AGP-native capabilities (handled by AGP, not by this plugin)

The following AGP product-flavor features are **already supported** on Android consumers via this plugin's AGP bridge (`bridgeAgpProductFlavors` / `bridgeAgpBuildTypes`) in v1.x+. They are NOT gaps in v2.0 / v2.1 — AGP handles them natively once KMP flavors propagate into the AGP `productFlavors { }` block.

| AGP capability | How it works on Android consumers |
|---|---|
| `manifestPlaceholders` per variant | Declare `productFlavors { free { manifestPlaceholders["x"] = "y" } }` (or via the KMP→AGP bridge) — AGP injects the placeholder into the manifest per variant. |
| Per-variant signing | `signingConfigs { } / productFlavors { free { signingConfig signingConfigs.x } }` — AGP signs the per-variant APK with the matching config. |
| Per-variant ProGuard / R8 | `productFlavors { free { proguardFiles 'proguard-free.pro' } }` — AGP applies the per-variant rule file at minification time. |
| `applicationIdSuffix` / `versionNameSuffix` per variant | Already wired: this plugin's AGP bridge maps `kmpFlavors.flavors { register("free") { applicationIdSuffix.set(".free") } }` into AGP `productFlavors.free.applicationIdSuffix`. |
| Per-variant lint (`lintOptions { }`) | AGP's lint plugin per-variant config flows through unchanged when the bridge propagates KMP flavors. |

These items are documented for transparency — if you see them on an AGP feature matrix, the answer is "already supported on Android consumers via the bridge; no `kmpFlavors` DSL needed beyond declaring the flavor".

For Detekt-per-variant (non-Android) and other non-Android lint paths, see [`v2.1 plan`](https://github.com/MobileByteLabs/kmp-product-flavors/blob/development/plans/v2.0-RFC-tracker.md) Phase 4.

---

## Troubleshooting (KMPF-Vxx error codes)

See `docs/ERROR_CODES.md` (W5 deliverable). Quick reference:

| Code | Severity | Meaning |
|---|---|---|
| KMPF-V01 | ERROR | Flavor name collides with build type name |
| KMPF-V04 | ERROR | `variantFilter` excluded every variant |
| KMPF-V05 | WARNING | Matrix mode enabled but zero non-Android KMP targets |
| KMPF-V08 | ERROR | Matrix mode enabled but no flavors registered |

More codes (V02, V03, V06, V07) ship in W5+ as the validator is extended.
