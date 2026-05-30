<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.2+-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Gradle-8.0+-02303A?logo=gradle&logoColor=white" alt="Gradle">
</p>

# KMP Product Flavors

A Gradle plugin that brings Android-style product flavors to **every Kotlin Multiplatform target** — Android, iOS, macOS, watchOS, tvOS, Desktop/JVM, Linux, Windows, JS, Wasm.

<p align="center">
  <a href="https://github.com/MobileByteLabs/kmp-product-flavors/actions/workflows/ci.yml"><img src="https://github.com/MobileByteLabs/kmp-product-flavors/actions/workflows/ci.yml/badge.svg" alt="Build Status"></a>
  <a href="https://central.sonatype.com/artifact/io.github.mobilebytelabs.kmpflavors/flavor-plugin"><img src="https://img.shields.io/maven-central/v/io.github.mobilebytelabs.kmpflavors/flavor-plugin?label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://plugins.gradle.org/plugin/io.github.mobilebytelabs.kmp-product-flavors"><img src="https://img.shields.io/gradle-plugin-portal/v/io.github.mobilebytelabs.kmp-product-flavors?label=Gradle%20Plugin%20Portal" alt="Gradle Plugin Portal"></a>
  <a href="https://plugins.jetbrains.com/plugin/31779-kmp-product-flavors"><img src="https://img.shields.io/jetbrains/plugin/v/31779-kmp-product-flavors?label=IDE%20Plugin" alt="IDE Plugin"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
</p>

<p align="center">
  <a href="docs/QUICKSTART.md">Quickstart</a> •
  <a href="docs/README.md">Docs</a> •
  <a href="docs/REFERENCE.md">Reference</a> •
  <a href="docs/MIGRATION_v1_to_v2.md">Migration</a> •
  <a href="#samples">Samples</a>
</p>

---

## Quick start

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors") version "2.5.0-alpha.1"
}

kmpFlavors {
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
    buildTypes {
        register("debug") { isDefault.set(true) }
        register("release")
    }
}
```

> **v2.5 highlights** — optional `dimensions { dimension("tier") { flavor("free") } }`
> ergonomic DSL block, expanded sample/CI coverage for 9 KMP targets (watchOS×4, tvOS×3,
> linuxX64, mingwX64, wasmJs), and `buildKonfig { secret(); enum(); customField(); perTarget {} }`
> for vault-integrated secrets, dimension enums, custom-type fields, and per-target conditional
> codegen. **v2.5 does not raise the v2.4 version floor** (Gradle 8.0+ / KGP 2.0.21+ / AGP 8.0+ /
> JDK 17+ / CMP 1.7.0+ — UNCHANGED). See [`docs/COMPATIBILITY_MATRIX.md`](docs/COMPATIBILITY_MATRIX.md)
> + [`docs/MIGRATION_v2.4_TO_v2.5.md`](docs/MIGRATION_v2.4_TO_v2.5.md) (opens with
> "You do not need to migrate.").

That's a 4-variant matrix (`freeDebug`, `freeRelease`, `paidDebug`, `paidRelease`) on every KMP target you declare. Source-set conventions are `src/commonFree/kotlin/`, `src/commonPaid/kotlin/`, etc. Switch active variant via `-PkmpFlavor=paidRelease`.

Full walkthrough: **[`docs/QUICKSTART.md`](docs/QUICKSTART.md)** (5 minutes).

## Capability highlights

- **Multi-dimensional flavors** with automatic 2^n variant matrix and per-flavor `BuildKonfig` codegen.
- **Matrix mode** (`buildMatrix.set(true)`) — compile every variant × every target in one Gradle invocation.
- **Per-variant publishing** to Maven Central (JVM classifier-tagged), iOS XCFramework, SPM, npm, Sonatype Snapshots.
- **Adjacent-plugin helpers** — Detekt-per-variant, Spotless-exclusion, dependency-guard baselines, Compose hot-reload variant switching.
- **AGP bridge** — `kmpFlavors.flavors` forwards into Android's `productFlavors { … }` block automatically.
- **Auto-detection** — `autoEnable=true` (default) flips matrix mode + adjacent helpers on when the shape is detected; opt out via `kmpFlavors.autoEnable.set(false)`.
- **Structured validator** — `KMPF-V01` … `KMPF-V22` codes catch misconfigurations at apply time; full catalogue in [`docs/ERROR_CODES.md`](docs/ERROR_CODES.md).
- **API stability buckets** — Stable / `@KmpFlavorsExperimental` / `CMP-API-WAITING`. Stable surfaces are locked for the 2.x cycle; full reference in [`docs/REFERENCE.md`](docs/REFERENCE.md).
- **IDE plugin** — [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31779-kmp-product-flavors) gives project-view decoration, status-bar variant switcher, and Gradle-tool-window task grouping.

## Documentation

Curated index: **[`docs/README.md`](docs/README.md)**. Most-visited:

| Doc | Topic |
|---|---|
| [`docs/QUICKSTART.md`](docs/QUICKSTART.md) | 5-min onboarding |
| [`docs/REFERENCE.md`](docs/REFERENCE.md) | Full DSL with stability buckets |
| [`docs/MATRIX_MODE.md`](docs/MATRIX_MODE.md) | Per-variant compilation matrix |
| [`docs/PUBLISHING.md`](docs/PUBLISHING.md) | Maven Central, XCFramework, SPM, npm, Snapshots |
| [`docs/ERROR_CODES.md`](docs/ERROR_CODES.md) | `KMPF-V<NN>` catalogue |
| [`docs/MIGRATION_v1_to_v2.md`](docs/MIGRATION_v1_to_v2.md) | v1.x → v2.x (critical pre-2026-11-14) |
| [`CHANGELOG.md`](CHANGELOG.md) | What's new per release |

## Samples

| Sample | Description |
|---|---|
| [`samples/basic-flavors/`](samples/basic-flavors/) | Minimal plugin demo. |
| [`samples/compose-multiplatform/`](samples/compose-multiplatform/) | Full Compose Multiplatform app. |
| [`samples/convention-integration/`](samples/convention-integration/) | Standalone convention plugin demo. |
| [`samples/kmp-project-template/`](samples/kmp-project-template/) | `openMF/kmp-project-template` integration. |
| [`samples/matrix-mode/`](samples/matrix-mode/) | Every matrix-mode consumer surface exercised end-to-end. |
| [`samples/multi-target-multi-variant/`](samples/multi-target-multi-variant/) | 3 flavors × 3 buildTypes × 6 targets = 54-compilation stress test. |

## Compatibility

| `kmp-product-flavors` | Kotlin | Compose Multiplatform | AGP (Android consumers) | Gradle | JDK |
|---|---|---|---|---|---|
| `2.4.x` | `2.0.21`+ (built against `2.2.21`) | `1.7`+ (samples on `1.9.x`) | `8.0`+ (built against `8.7.x`) | `8.0`+ | `17`+ |

The plugin tests its full KGP × CMP × Gradle matrix nightly. Combinations outside the tested matrix may work but are not actively verified. See [`docs/REFERENCE.md` → "Compatibility windows"](docs/REFERENCE.md) for the per-property version-introduced trail.

## Requirements

- Kotlin Multiplatform plugin applied
- JDK 17+
- One of: Maven Central, Gradle Plugin Portal, or Sonatype Snapshots in `pluginManagement.repositories`

## Contributing

Contributions are welcome. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for guidelines and [`docs/RELEASE.md`](docs/RELEASE.md) for the release cascade.

## License

Apache License, Version 2.0. See [`LICENSE`](LICENSE) for the full text.

```
Copyright 2026 MobileByteLabs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
