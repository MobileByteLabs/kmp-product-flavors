# Supported KMP Targets

> **Since v2.5** — comprehensive matrix of every Kotlin Multiplatform target the
> `kmp-product-flavors` plugin detects, wires source sets for, exercises in
> samples, and supports per-variant Compose resources for.

The plugin's `PlatformDetector` has registered all targets listed below since v1.1.0
(phases G1-G4 of the multi-platform expansion). What v2.5 adds is **end-to-end sample
coverage + CI matrix runs** for the previously-uncovered families. Detection itself is
unchanged.

---

## Coverage matrix

Legend:

- ✓ — Supported, exercised in the canonical test/sample
- ✓ (v2.5) — Newly added sample/CI coverage in v2.5; detection was already in v1.1.0+
- — — Detected but sample coverage deferred (see notes)
- n/a — Not applicable to this target family

| Target | Detected | Source-sets wired | Sample-exercised | Per-variant `composeResources/` | Notes |
|---|:--:|:--:|:--:|:--:|---|
| `android` | ✓ | (AGP-native) | ✓ | ✓ | Per-flavor `productFlavors` source sets handled by AGP bridge |
| `jvm` / `desktop` | ✓ | ✓ | ✓ | ✓ | Default — no platform rotation needed |
| `iosX64` | ✓ | ✓ | ✓ | ✓ | Native — `nativeMain` intermediate |
| `iosArm64` | ✓ | ✓ | ✓ | ✓ | Native |
| `iosSimulatorArm64` | ✓ | ✓ | ✓ | ✓ | Native — M-series simulator |
| `macosX64` | ✓ | ✓ | — | ✓ | Native; sample coverage deferred to v2.5.x patch |
| `macosArm64` | ✓ | ✓ | — | ✓ | Native; sample coverage deferred to v2.5.x patch |
| `watchosX64` | ✓ | ✓ | ✓ (v2.5) | ✓ (v2.5) | Native — `apple-targets-extended` job in `sample-target-coverage.yml` |
| `watchosArm64` | ✓ | ✓ | ✓ (v2.5) | ✓ (v2.5) | Native |
| `watchosSimulatorArm64` | ✓ | ✓ | ✓ (v2.5) | ✓ (v2.5) | Native |
| `watchosDeviceArm64` | ✓ | ✓ | — | ✓ | Native; sample coverage deferred (KMP-side stability gating) |
| `tvosX64` | ✓ | ✓ | ✓ (v2.5) | ✓ (v2.5) | Native — `apple-targets-extended` job |
| `tvosArm64` | ✓ | ✓ | ✓ (v2.5) | ✓ (v2.5) | Native |
| `tvosSimulatorArm64` | ✓ | ✓ | ✓ (v2.5) | ✓ (v2.5) | Native |
| `linuxX64` | ✓ | ✓ | ✓ (v2.5) | ✓ (v2.5) | Native — `linux-native-target` job in `sample-target-coverage.yml` |
| `linuxArm64` | ✓ | ✓ | — | ✓ | Native; sample coverage deferred (cross-compile complexity) |
| `mingwX64` | ✓ | ✓ | ✓ (v2.5) | ✓ (v2.5) | Native — `windows-native-target` job in `sample-target-coverage.yml` |
| `androidNativeArm64` | ✓ | ✓ | — | n/a | Native; specialized use cases |
| `androidNativeX64` | ✓ | ✓ | — | n/a | Native; specialized use cases |
| `androidNativeArm32` | ✓ | ✓ | — | n/a | Native; specialized use cases |
| `androidNativeX86` | ✓ | ✓ | — | n/a | Native; specialized use cases |
| `js` | ✓ | ✓ | ✓ | n/a | Web — `webMain` intermediate; per-variant npm publish |
| `wasmJs` | ✓ | ✓ | ✓ | n/a | Web — per-variant npm publish via `PerVariantNpmPublishConfigurator` |
| `wasmWasi` | ✓ | ✓ | — | n/a | Web; sample coverage deferred |

**Family count (v2.5):** 5 leaf families × Apple = (ios, macos, watchos, tvos, androidNative) ; 2 × Web = (js, wasmJs, wasmWasi) ; 2 × Linux/Windows native = (linuxX64, mingwX64) ; 1 × Android = (android) ; 1 × JVM = (jvm/desktop) — fully covered.

---

## What "sample-exercised" means

For a target to be marked ✓ in the **Sample-exercised** column, it MUST:

1. Be enabled in `samples/multi-target-multi-variant/build.gradle.kts` so the variant matrix
   includes it (3 flavors × 3 buildTypes × N targets compilations).
2. Have a corresponding `assembleAll{Target}Variants` aggregate task auto-registered by
   `AggregateTasksRegistrar` (proven by `AggregateVariantTasksTest` for `linuxX64` / `mingwX64` / `wasmJs`).
3. Be exercised end-to-end by a CI job in either `sample-multi-target.yml` (v2.4 baseline:
   iOS + Desktop + JS + WasmJs) or `sample-target-coverage.yml` (v2.5 expansion: watchOS + tvOS + linuxX64 + mingwX64).

---

## What "per-variant `composeResources/`" means

For a target to be marked ✓ in the **Per-variant composeResources/** column, the convention
is that consumers can drop resource files at:

```
src/common{Flavor}/composeResources/{type}/{file}
```

(e.g. `src/commonFree/composeResources/values/strings.xml`) and Compose Multiplatform
auto-discovers them for the active variant compilation per RFC §3 Phase 3A.

This is target-agnostic at the configurator API level — `ComposeResourcesConfigurator` operates
on `kotlin.sourceSets` and per-flavor source-set names, not on specific target identities.
The actual discovery on each target family is verified end-to-end via the
`samples/multi-target-multi-variant/` runs in `sample-target-coverage.yml`.

---

## Cross-references

- **Detection logic:** `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/PlatformDetector.kt`
- **Detection tests:** `build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/PlatformDetectorTest.kt`
  (per-arch v2.5 AC 8 tests under "v2.5 — per-arch detection discipline" section)
- **Aggregate task naming:** `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AggregateTasksRegistrar.kt`
- **Aggregate task tests:** `build-logic/flavor-plugin/src/test/kotlin/com/mobilebytelabs/kmpflavors/AggregateVariantTasksTest.kt`
  (v2.5 AC 12 tests for `linuxX64` / `mingwX64` / `wasmJs`)
- **CI v2.4 baseline:** `.github/workflows/sample-multi-target.yml` (iOS + Desktop + JS + WasmJs)
- **CI v2.5 expansion:** `.github/workflows/sample-target-coverage.yml` (watchOS + tvOS + linuxX64 + mingwX64)
- **Canonical multi-target sample:** `samples/multi-target-multi-variant/build.gradle.kts`
- **Canonical multi-dim sample:** `samples/multi-dim-3d/build.gradle.kts` (3 dimensions × tier × env × form-factor)

---

## Compatibility floor (unchanged from v2.4)

| Tool | Minimum (v2.5) | Notes |
|---|---|---|
| Gradle | **8.0** | Same as v2.4 |
| Kotlin Gradle Plugin (KGP) | **2.0.21** | wasmJs stable since 2.0; watchOS / tvOS / linuxX64 / mingwX64 stable since KGP 1.4 |
| Android Gradle Plugin (AGP) | **8.0** | `androidComponents.finalizeDsl` available since AGP 7.1 |
| Compose Multiplatform | **1.7.0** | Per-variant `composeResources/` auto-discovery threshold |
| JDK toolchain | **17** | |
| BuildKonfig | (pinned in `libs.versions.toml`) | |

**No new floor required for v2.5.** v2.4 consumers can drop in v2.5 without any
toolchain bump. See `docs/COMPATIBILITY_MATRIX.md` (authored in v2.5 Phase 4) for the full
explanation.

---

## Adding a new target family

If a future KMP release adds a new target family (e.g. `linuxArm32`, hypothetical), the
extension surface is:

1. **`PlatformDetector.detect()`** — add a detection block recognizing the target name and
   assigning it to a parent intermediate (`native` / `web` / null).
2. **`PlatformDetectorTest.kt`** — add a per-arch detection test mirroring the v2.5 AC 8
   pattern.
3. **`samples/multi-target-multi-variant/build.gradle.kts`** — add the `targetName()` call.
4. **`.github/workflows/sample-target-coverage.yml`** — add a CI job under the appropriate
   runner (`macos-latest` / `ubuntu-latest` / `windows-latest`).
5. **This document** — add a row to the coverage matrix above.

That's it. No changes needed to `SourceSetConfigurator`, `CompilationRegistrar`,
`AggregateTasksRegistrar`, `ComposeResourcesConfigurator`, or `PerVariantNpmPublishConfigurator` — they're all
generic over the `PlatformGroup` list `PlatformDetector` produces.
