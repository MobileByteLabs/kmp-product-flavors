<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.3.21+-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Gradle-9.5.1+-02303A?logo=gradle&logoColor=white" alt="Gradle">
  <img src="https://img.shields.io/badge/AGP-9.2.1+-3DDC84?logo=android&logoColor=white" alt="AGP">
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
  <a href="#samples">Samples</a>
</p>

---

## Quick start

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors") version "2.8.0"
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

`kmpFlavors {}` is the SINGLE API consumers ever use to declare product flavors across every KMP target — build-time AND runtime AND resources. Apply the plugin, declare flavors, ship.

Full walkthrough: **[`docs/QUICKSTART.md`](docs/QUICKSTART.md)** (5 minutes).

## Capability highlights

- **Multi-dimensional flavors** with automatic 2^n variant matrix and per-flavor `BuildKonfig` codegen.
- **Matrix mode** (`buildMatrix.set(true)`) — compile every variant × every target in one Gradle invocation.
- **Runtime API** — `KmpFlavorsRuntime` `expect` + 5 actuals (Android / iOS / Desktop / JS / WasmJs) reading from platform-native sources (BuildConfig / NSBundle / JAR Manifest / DefinePlugin externals).
- **Per-platform routers** — AGP Firebase + Android res + Compose Resources + iOS Firebase + iOS xcconfig + Desktop nativeDistributions + Webpack overlay + DefinePlugin.
- **iOS zero-setup** — `:kmpFlavorsBootstrapXcode` seeds `pbxproj` configurations + `xcconfig` includes + `Info.plist` keys idempotently.
- **Per-variant publishing** to Maven Central (JVM classifier-tagged), iOS XCFramework, SPM, npm, Sonatype Snapshots.
- **AGP bridge** — `kmpFlavors.flavors` forwards into Android's `productFlavors { … }` block automatically via reflective AGP 9 dispatch.
- **Auto-detection** — `autoEnable=true` (default) flips matrix mode + adjacent helpers on when the shape is detected.
- **Structured validator** — `KMPF-V01` … `KMPF-V53` codes catch misconfigurations at apply time; full catalogue in [`docs/ERROR_CODES.md`](docs/ERROR_CODES.md). Run `:kmpFlavorsDoctor` for the per-project report.
- **IDE plugin** — [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31779-kmp-product-flavors) gives project-view decoration, status-bar variant switcher, and Gradle-tool-window task grouping.

## Documentation

Curated index: **[`docs/README.md`](docs/README.md)**. Most-visited:

| Doc | Topic |
|---|---|
| [`docs/QUICKSTART.md`](docs/QUICKSTART.md) | 5-min onboarding |
| [`docs/REFERENCE.md`](docs/REFERENCE.md) | Full `kmpFlavors {}` DSL reference |
| [`docs/MATRIX_MODE.md`](docs/MATRIX_MODE.md) | Per-variant compilation matrix |
| [`docs/PUBLISHING.md`](docs/PUBLISHING.md) | Maven Central, XCFramework, SPM, npm, Snapshots |
| [`docs/ERROR_CODES.md`](docs/ERROR_CODES.md) | `KMPF-V<NN>` catalogue |
| [`docs/AGP_SUPPORT.md`](docs/AGP_SUPPORT.md) | AGP 9.2.1+ floor contract |
| [`docs/LEARNINGS.md`](docs/LEARNINGS.md) | Locked architectural learnings L1–L6 |
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

## Requirements

| Tool | v2.8.0+ floor |
|---|---|
| Android Gradle Plugin | **9.2.1+** |
| Gradle wrapper | **9.5.1+** |
| Kotlin | **2.3.21+** |
| Compose Multiplatform | **1.10.3+** |
| JVM toolchain | **17+** |

Kotlin Multiplatform plugin must be applied. Plugin coordinates available via Maven Central, Gradle Plugin Portal, or Sonatype Snapshots in `pluginManagement.repositories`.

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
