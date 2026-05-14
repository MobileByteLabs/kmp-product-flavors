# `samples/matrix-mode` — v2.0 matrix mode reference

This sample exercises every consumer-facing surface of v2.0 matrix mode in one place. Use it as the canonical adoption reference when bringing matrix mode to your own project.

## What this sample shows

| Feature | Where it shows up |
|---|---|
| Q5 / Q16-C single-point opt-in | `buildMatrix.set(true)` in `build.gradle.kts` |
| 4-variant matrix (2 flavors × 2 buildTypes) | `flavors { register("free") / register("paid") }` + `buildTypes { register("debug") / register("release") }` |
| Q11 expect/actual across variants | `commonMain/AppName.kt` declares `expect fun appName()`; `commonFree` and `commonPaid` each provide an `actual` |
| Q12 cross-variant isolation | `commonPaid/AppName.kt` imports `kotlinx.coroutines.delay`; `commonFree` cannot |
| Q17 per-variant deps | `kotlinx-coroutines-core` declared only on `commonPaid` source set |
| Q19-B variant API | `variants.configureEach { … register("describe$Variant") { … } }` |
| Q20-A `variantFilter` AGP-style | `if (flavors.any { name == "paid" } && buildType == "release") setIgnore(true)` — removes `paidRelease` |
| Q21-D per-variant publishing | `publishMatrix.set(true)` + `maven-publish` plugin — classifier-tagged Maven publications per inactive variant |
| Q3-A per-variant BuildConfig | `IS_PREMIUM`, `MAX_ITEMS`, `IS_DEBUG` fields differ per variant in `BuildKonfig.kt` |

## Try it

```bash
# Compile every variant (active free* via main + paidDebug via matrix mode)
./gradlew :samples:matrix-mode:assembleAllDesktopVariants

# Discover what matrix mode added
./gradlew :samples:matrix-mode:tasks --group="kmpFlavors variants"

# Print metadata for a single variant (Q19-B variant API)
./gradlew :samples:matrix-mode:describePaidDebug

# Generate the per-variant BuildKonfig.kt
./gradlew :samples:matrix-mode:generatePaidDebugBuildConfig

# Publish per-variant artifacts to local Maven (Q21-D)
./gradlew :samples:matrix-mode:publishToMavenLocal
# -> ~/.m2/repository/io/github/mobilebytelabs/samples/matrix-mode/2.0.0-alpha.1/
#    matrix-mode-2.0.0-alpha.1-paidDebug.jar
```

## What this sample deliberately does NOT show

- iOS / JS / WasmJs targets — covered by `MultiTargetMatrixRegistrationTest` in the plugin's test suite. iOS per-variant *publishing* (XCFramework bundling) is deferred to v2.0 post-GA.
- AGP bridge / Android target — `bridgeAgpProductFlavors` is a separate v1.x surface; this sample stays focused on the v2.0 non-Android matrix.
- Convention plugin pattern — see `samples/convention-integration` for that.

## Notes on the build file order

The `kmpFlavors { flavors { register(...) } }` block appears BEFORE the `kotlin { sourceSets { val commonPaid by getting } }` block. This is required: the plugin eagerly creates per-flavor source sets as flavors are registered, so the consumer's `getByName("commonPaid")` only works if flavors registered first. See `docs/MATRIX_MODE.md` for the full rationale.
