# Migrating from v2.7.x to v2.8.0

> **You DO need to migrate.** v2.8.0 is the AGP 9.2.1+ cut. Consumers on AGP 8.x or Gradle 8.x must update before adopting.

This is the inverse of `MIGRATION_v2.6_TO_v2.7.md` (which opened with "You do not need to migrate"). v2.7 kept reflective AGP-8.x compatibility; v2.8 drops the bridge code path that supported it.

---

## Required versions

| Tool | v2.7.x supported | v2.8.0 required |
|---|---|---|
| Android Gradle Plugin | 8.2.2 / 8.5.2 / 8.10.0 / 9.2.1 | **9.2.1+** |
| Gradle wrapper | 8.x or 9.x | **9.5.1+** |
| Kotlin | 2.3.0+ | **2.3.21+** |
| Compose Multiplatform | 1.6+ | 1.6+ (no change) |
| JVM toolchain | 17+ | 17+ (no change) |

---

## What changed

### Breaking — at consumer build time

1. **AGP floor raised to 9.2.1.** Consumers on AGP 8.x will hit `Unsupported AGP version` at plugin apply.
2. **`agp-matrix-compat.yml` workflow retired.** The library no longer tests against AGP 8.2/8.5/8.10; downstream forks reusing that workflow file should delete it.
3. **`AppFlavor.kt` consumer boilerplate is obsoleted.** Phase 1 of the v2.8 epic added a pure-`com.android.application` runtime path — apply `com.mobilebytelabs.kmpflavors` directly; the `withPlugin("com.android.application")` block that called `configureFlavors()` from the convention plugin is no longer needed.
4. **Per-(target × flavor) source set fan-out is permanently single-axis.** v2.7 documented per-target-per-flavor source sets in roadmap; v2.8 plugin creates only `{F}Main` (cross-cutting). KGP rule: no source set may `dependsOn` a default-for-compilation source set, so dual-axis is impossible. Consumers needing per-(target × flavor) logic place it inside `{F}Main` with `expect`/`actual` or platform-conditional code. See `LEARNINGS.md` L4.

### Breaking — at API surface

5. **Internal `setProperty` / `setBooleanProperty` helpers in `AgpBridge.kt` were removed.** No public callers — internal reorganization via `AgpReflectiveSetters`. Listed for completeness; consumers do not invoke `AgpBridge` directly.

### Additive — no migration needed

6. **`kmpFlavors { ios { … } / desktop { … } / web { … } }` DSL blocks** for Phases 2-5 integration.
7. **`KmpFlavorsRuntime` commonMain API** auto-generated with reflection-safe Android actual (D40).
8. **Per-flavor Compose Resources + Android res** routing under `composeResources/{F}/` and `src/{F}/res/`.
9. **Per-flavor Firebase wiring** (opt-in via `googleServiceConfig(…)`).
10. **iOS pbxproj zero-setup bootstrap** via `:kmpFlavorsBootstrapXcode`.
11. **`:kmpFlavorsDoctor` task** with V01-V53 validator dump.

---

## Step-by-step migration

### 1. Bump AGP + Gradle + Kotlin

In `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.2.1"          # was 8.x or 9.2.1
kotlin = "2.3.21"      # was 2.3.0
```

In `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
```

Run `./gradlew wrapper --gradle-version 9.5.1 --distribution-type bin`.

### 2. Pin the plugin to 2.8.0

In `gradle/libs.versions.toml`:

```toml
[versions]
kmp-product-flavors = "2.8.0"   # was 2.7.x
```

If using composite build via `lib-integrate.properties`, update the version pin instead.

### 3. Delete obsoleted consumer code

Delete `AppFlavor.kt` from the convention plugin module:

```bash
rm build-logic/convention/src/main/kotlin/<your-package>/AppFlavor.kt
```

And remove the `withPlugin("com.android.application") { configureFlavors() }` fallback block from your KmpFlavorsConventionPlugin.kt. Apply the v2.8 plugin directly; AGP product flavors propagate automatically via `AgpProductFlavorRegistrar.whenObjectAdded` (no consumer code path needed).

You can also run the bundled migration scanner to automate this:

```bash
./gradlew :kmpFlavorsMigrateFromV27 --apply
```

### 4. Run the doctor

```bash
./gradlew :kmpFlavorsDoctor
```

Confirms V31–V53 validators pass against your `kmpFlavors {}` declarations.

### 5. Verify your platforms still build

```bash
./gradlew :cmp-android:assembleDemoDebug
./gradlew :cmp-desktop:packageDistributionForCurrentOS
./gradlew :cmp-web:jsBrowserDevelopmentWebpack
./gradlew :cmp-web:wasmJsBrowserDevelopmentWebpack
```

For iOS, run `./gradlew :kmpFlavorsBootstrapXcode` once to seed the pbxproj, then build through Xcode normally.

---

## Validator codes that may newly fire on v2.8

| Code | Phase introduced | Catches |
|---|---|---|
| V31 | Phase 1 | Consumer applies `com.mobilebytelabs.kmpflavors` without `com.android.application` AND without KMP — no platform to integrate against |
| V32 | Phase 1 | Pure-AGP mode declared but `phaseKmp` integrators (RuntimeApi codegen, source set wiring) requested |
| V33 | Phase 2 | iOS xcconfig output dir not writable |
| V34 | Phase 2 | iOS xcconfig variant missing required `PRODUCT_BUNDLE_IDENTIFIER` |
| V35 | Phase 4 | Desktop `nativeDistributions.packageName` could not be resolved reflectively (Compose Desktop plugin not applied) |
| V36 | Phase 5 | Web `webpack.config.d/` overlay dir absent — `js(IR){browser()}` not configured |
| V37 | Phase 6 | `KmpFlavorsRuntime` runtime API codegen failed |
| V38 | Phase 6 | RuntimeApi codegen-host election conflict (multiple modules claiming the same package — see D38) |
| V39–V41 | Phase 7 | Per-flavor source set wiring failed; default hierarchy lock; dimension/source-set name collision |
| V42–V44 | Phase 8 | Compose Resources / Android res per-flavor routing failure |
| V45–V47 | Phase 9 | Firebase per-flavor wiring (Android `google-services.json` missing; iOS `KMPF_FIREBASE_CONFIG_FILE` xcconfig missing) |
| V48–V49 | Phase 12 | iOS pbxproj parse/bootstrap failure |
| V50–V53 | Phase 14 | Per-flavor signing config / Desktop OS expansion gaps |

Run `:kmpFlavorsDoctor` to see which (if any) fire against your declarations.

---

## Cross-references

- [`AGP_SUPPORT.md`](AGP_SUPPORT.md) — 9.2.1+ floor contract + retired matrix-CI rationale
- [`LEARNINGS.md`](LEARNINGS.md) — execution-discovered locked contracts L1–L5 driving v2.8 architecture
- [`AGP_9_MIGRATION_NOTES.md`](AGP_9_MIGRATION_NOTES.md) — AGP 9 consumer-side cookbook (CommonExtension type-param drop, dataBinding deprecation, etc.)
- [`MIGRATION_v2.6_TO_v2.7.md`](MIGRATION_v2.6_TO_v2.7.md) — v2.7 (optional) cookbook for reference
- [`adoption/v2.8/`](adoption/) — full v2.8 adoption guide pair

---

## Rollback

If the v2.8 cut surfaces regressions, you can pin back to the last v2.7 patch:

```toml
[versions]
kmp-product-flavors = "2.7.0"
agp = "9.2.1"        # v2.7 also supports AGP 9.2.1
kotlin = "2.3.21"
```

v2.7's AGP 9.2.1 row in `agp-matrix-compat.yml` was the source-of-truth gate before v2.8 dropped older rows, so AGP 9.2.1 + Kotlin 2.3.21 is a known-good v2.7 configuration as well.

File regressions in [github.com/openMF/kmp-product-flavors/issues](https://github.com/openMF/kmp-product-flavors/issues) so the team can patch a v2.7.x release if needed.
