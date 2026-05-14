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
| `publishMatrix` opt-in | Classifier-tagged Maven publications per variant (JVM) | RFC §3 Q21-D |

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
| `org.jetbrains.compose` (Compose Multiplatform) | ✅ Compatible | Per-variant compilations honor Compose's own source-set hierarchy. **Hot-reload is active-variant only at v2.0 GA** (per-variant hot-reload is v2.1 scope). |
| `org.jetbrains.kotlin.plugin.serialization` | ✅ Compatible | KSP codegen runs per compilation; per-variant compilations each get their own generated sources. |
| `org.jetbrains.kotlinx.atomicfu` | ✅ Compatible | Atomicfu's compilation transformer runs per `KotlinCompilation`, including ours. |
| `dependency-guard` | ⚠ Per-variant baselines | Each variant compilation has its own `compileClasspath`, so dependency-guard sees N baselines. Consumers may need to add explicit `dependencyGuard { configuration("{variant}CompileClasspath") }` entries. |
| `com.diffplug.spotless` | ⚠ Watch source-set scope | If Spotless rules use a glob that matches generated `BuildKonfig.kt`, the per-variant copies trigger N format checks. Exclude `build/generated/kmpFlavors/**` from Spotless globs. |
| `io.gitlab.arturbosch.detekt` | ⚠ Watch source-set scope | Same caveat as Spotless. |
| Kover | ⚠ Coverage per variant | Each variant compilation produces its own coverage; Kover merges them. No special configuration needed; consumers may want to set `kover.useReportSet(...)` to scope. |

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
