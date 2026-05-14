<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Gradle-8.0+-02303A?logo=gradle&logoColor=white" alt="Gradle">
</p>

# KMP Product Flavors

A Gradle plugin that brings Android-style product flavor support to **all Kotlin Multiplatform targets**.

<p align="center">
  <a href="https://github.com/MobileByteLabs/kmp-product-flavors/actions/workflows/ci.yml"><img src="https://github.com/MobileByteLabs/kmp-product-flavors/actions/workflows/ci.yml/badge.svg" alt="Build Status"></a>
  <a href="https://central.sonatype.com/artifact/io.github.mobilebytelabs.kmpflavors/flavor-plugin"><img src="https://img.shields.io/maven-central/v/io.github.mobilebytelabs.kmpflavors/flavor-plugin?label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://plugins.gradle.org/plugin/io.github.mobilebytelabs.kmp-product-flavors"><img src="https://img.shields.io/gradle-plugin-portal/v/io.github.mobilebytelabs.kmp-product-flavors?label=Gradle%20Plugin%20Portal" alt="Gradle Plugin Portal"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
</p>

<p align="center">
  <a href="#supported-platforms">Platforms</a> •
  <a href="#installation">Installation</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="https://github.com/MobileByteLabs/kmp-product-flavors/wiki">Documentation</a> •
  <a href="#samples">Samples</a>
</p>

---

## Supported Platforms

This plugin supports the following Kotlin Multiplatform targets in the current published version (`v1.1.5`). The status column reflects what `PlatformDetector.kt` actually recognises today — see roadmap below for planned additions.

| Platform | Targets | Status (v1.1.5) |
|----------|---------|-----------------|
| **Android** | `androidTarget()` | ✅ Detected |
| **iOS** | `iosArm64()`, `iosX64()`, `iosSimulatorArm64()` | ✅ Detected |
| **macOS** | `macosArm64()`, `macosX64()` | ✅ Detected |
| **tvOS** | `tvosArm64()`, `tvosX64()`, `tvosSimulatorArm64()` | ✅ Detected |
| **watchOS** | `watchosArm64()`, `watchosX64()`, `watchosSimulatorArm64()`, `watchosDeviceArm64()` | ✅ Detected |
| **Desktop/JVM** | `jvm()`, `jvm("desktop")` | ✅ Detected |
| **Linux** | `linuxX64()`, `linuxArm64()` | ✅ Detected |
| **Windows** | `mingwX64()` | ✅ Detected |
| **JavaScript** | `js()` | ✅ Detected |
| **WebAssembly (wasmJs)** | `wasmJs()` | ✅ Detected |
| **WebAssembly (wasmWasi)** | `wasmWasi()` | ✅ Detected |
| **Android Native** | `androidNativeArm64()`, `androidNativeX64()`, `androidNativeArm32()`, `androidNativeX86()` | ✅ Detected |

> **Status legend:** ✅ Detected = `PlatformDetector.kt` registers the target and creates flavor source sets. 🟡 Planned = roadmapped for the next minor release; declaring the target today is silently ignored.

## Features

- 🎯 **Multi-dimensional flavors** - Define dimensions (tier, environment, region) with automatic 2^n variant matrix
- ⚡ **BuildConfig generation** - Compile-time constants with `VARIANT_NAME`, `IS_<FLAVOR>` flags
- 📁 **Source set wiring** - Automatic `commonFree`, `androidFree`, `iosFree` source sets
- 🔗 **Intermediate source sets** - Optional `webMain` and `nativeMain` for shared code
- 📦 **Per-flavor dependencies** - Add dependencies for specific flavors only
- 🛠️ **IDE Run Configurations** - Auto-generated configs for each build variant
- 💾 **Build cache support** - Cacheable tasks for efficient builds

## Installation

> **Latest Version:** Check the badges above or [Maven Central](https://central.sonatype.com/artifact/io.github.mobilebytelabs.kmpflavors/flavor-plugin) / [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.mobilebytelabs.kmp-product-flavors)

### Gradle Plugin Portal (Recommended)

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.2.21"
    id("io.github.mobilebytelabs.kmp-product-flavors") version "<latest-version>"
}
```

### Maven Central

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.2.21"
    id("io.github.mobilebytelabs.kmp-product-flavors") version "<latest-version>"
}
```

### Local Development

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

## Quick Start

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")
}

kmpFlavors {
    buildConfigPackage.set("com.example.app")
    // buildConfigClassName defaults to "BuildKonfig" (v1.1.5+)
    // generateBuildConfig + createIntermediateSourceSets + bridgeAgp* defaults are safe — zero-config.

    // Define dimensions
    flavorDimensions {
        register("tier") { priority.set(0) }
        register("environment") { priority.set(1) }
    }

    // Define flavors
    flavors {
        register("free") {
            dimension.set("tier")
            isDefault.set(true)
            buildConfigField("Boolean", "IS_PREMIUM", "false")
        }
        register("paid") {
            dimension.set("tier")
            buildConfigField("Boolean", "IS_PREMIUM", "true")
        }
        register("dev") {
            dimension.set("environment")
            isDefault.set(true)
            buildConfigField("String", "BASE_URL", "\"https://dev-api.example.com\"")
        }
        register("prod") {
            dimension.set("environment")
            buildConfigField("String", "BASE_URL", "\"https://api.example.com\"")
        }
    }
}
```

## Build Variants

With 2 dimensions × 2 flavors each, you get **4 build variants** (2^n):

| Variant | Tier | Environment |
|---------|------|-------------|
| `freeDev` | free | dev |
| `freeProd` | free | prod |
| `paidDev` | paid | dev |
| `paidProd` | paid | prod |

### Switching Variants

**Command Line:**
```bash
./gradlew build -PkmpFlavor=paidProd
```

**gradle.properties:**
```properties
kmpFlavor=freeDev
```

**IDE Run Configurations:**
```bash
# Generate run configurations for all variants
./gradlew generateRunConfigurations
```

This creates `.run/` configurations that appear in your IDE's run dropdown.

### List All Variants

```bash
./gradlew listFlavors
```

Output:
```
╭──────────────────────────────────────────────────────────────╮
│                    KMP Flavor Variants                       │
├──────────────────────────────────────────────────────────────┤
│ Dimensions:                                                  │
│   • tier (priority: 0)                                       │
│   • environment (priority: 1)                                │
├──────────────────────────────────────────────────────────────┤
│ Variant     │ Flavors       │ Status     │                   │
│ freeDev     │ free, dev     │ ✓ ACTIVE   │                   │
│ freeProd    │ free, prod    │            │                   │
│ paidDev     │ paid, dev     │            │                   │
│ paidProd    │ paid, prod    │            │                   │
╰──────────────────────────────────────────────────────────────╯
```

## Multi-module setup (v1.1.5+)

When `org.convention.kmp.flavors` (or any convention plugin applying `KmpFlavorPlugin`) is auto-applied across every module in a multi-module build, only **one** module should generate `BuildKonfig.kt` — otherwise the same class lands in every module's classpath and you hit DEX merge duplicate-class errors on Android.

The plugin handles this automatically via a rootProject-extras claim: the first subproject configured wins, the rest log info-level "skipping codegen" messages. For deterministic results across builds, designate a specific module as the codegen host:

```kotlin
// In :cmp-shared/build.gradle.kts (or whichever module you choose):
extensions.configure<KmpFlavorExtension> {
    codegenHost.set(true)
}
```

`codegenHost.set(true)` always wins the claim regardless of configuration order. `codegenHost.set(false)` opts a module out entirely. Default (`null`) is first-come-first-served auto-claim.

See [CHANGELOG.md](CHANGELOG.md) for the full v1.1.5 release notes.

## Generated BuildKonfig

Default class name is `BuildKonfig` since v1.1.5 (was `FlavorConfig`). Override via `buildConfigClassName.set("MyName")` if you prefer a different identifier.

```kotlin
// build/generated/kmpFlavors/commonMain/kotlin/com/example/app/BuildKonfig.kt
package com.example.app

object BuildKonfig {
    const val VARIANT_NAME: String = "freeDev"

    // Auto-generated flavor flags
    const val IS_FREE: Boolean = true
    const val IS_PAID: Boolean = false
    const val IS_DEV: Boolean = true
    const val IS_PROD: Boolean = false

    // Custom fields from flavor config
    const val IS_PREMIUM: Boolean = false
    const val BASE_URL: String = "https://dev-api.example.com"
}
```

## Source Set Hierarchy

```
src/
├── commonMain/          # All variants
├── commonFree/          # Free tier (all platforms)
├── commonPaid/          # Paid tier (all platforms)
├── commonDev/           # Dev environment (all platforms)
├── commonProd/          # Prod environment (all platforms)
├── androidMain/         # Android (all flavors)
├── androidFree/         # Android + Free
├── iosMain/             # iOS (all flavors)
├── iosFree/             # iOS + Free
├── desktopMain/         # Desktop (all flavors)
└── desktopFree/         # Desktop + Free
```

## Samples

| Sample | Description | Location |
|--------|-------------|----------|
| **basic-flavors** | Minimal plugin demo | [`samples/basic-flavors`](samples/basic-flavors) |
| **kmp-project-template** | Full KMP convention plugin integration | [`samples/kmp-project-template`](samples/kmp-project-template) |
| **convention-integration** | Standalone convention plugin demo | [`samples/convention-integration`](samples/convention-integration) |
| **compose-multiplatform** | Full Compose Multiplatform app | [`samples/compose-multiplatform`](samples/compose-multiplatform) |

## Convention Plugin Integration

For projects using convention plugins like [kmp-project-template](https://github.com/openMF/kmp-project-template), we provide ready-to-use integration files.

### Quick Setup

```bash
# Copy integration files to your project
./integration/install-to-kmp-project-template.sh /path/to/your/project
```

### Manual Integration

1. **Add dependency** to `build-logic/convention/build.gradle.kts`:
   ```kotlin
   dependencies {
       compileOnly("io.github.mobilebytelabs:kmp-product-flavors-gradle-plugin:1.0.0")
   }
   ```

2. **Copy plugin files** from [`integration/convention-plugin/`](integration/convention-plugin/):
   - `KMPFlavorsConventionPlugin.kt`
   - `org/convention/KmpFlavors.kt`
   - `org/convention/KmpFlavorsBuildConfig.kt`

3. **Register the plugin**:
   ```kotlin
   gradlePlugin {
       plugins {
           register("kmpFlavors") {
               id = "org.convention.kmp.flavors"
               implementationClass = "KMPFlavorsConventionPlugin"
           }
       }
   }
   ```

4. **Apply in modules**:
   ```kotlin
   plugins {
       id("org.convention.kmp.flavors")
   }
   ```

See the full [Convention Plugin Integration Guide](integration/convention-plugin/README.md) for details.

### Run Samples

```bash
# Basic flavors demo
./gradlew :samples:basic-flavors:build -PkmpFlavor=freeDev
./gradlew :samples:basic-flavors:listFlavors

# Local test with all variants
./MavenLocalRelease.sh paidProd
```

## Documentation

📚 **Full documentation available on the [GitHub Wiki](https://github.com/MobileByteLabs/kmp-product-flavors/wiki)**

- [Getting Started](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/Getting-Started)
- [Configuration Guide](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/Configuration)
- [Build Variants](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/Build-Variants)
- [Source Sets](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/Source-Sets)
- [BuildConfig Generation](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/BuildConfig)
- [IDE Integration](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/IDE-Integration)
- [Migration from Android](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/Migration-from-Android)

## Gradle Tasks

| Task | Description |
|------|-------------|
| `listFlavors` | List all variants and active selection |
| `validateFlavors` | Validate flavor configuration |
| `generateFlavorBuildConfig` | Generate BuildConfig object |
| `generateRunConfigurations` | Generate IDE run configs |

## Requirements

- **Kotlin Multiplatform** plugin applied
- **JDK** 17+
- See compatibility matrix below

## Matrix mode (v2.0)

> Build every variant × every non-Android target in one Gradle invocation, AGP-style. Opt-in, zero per-module DSL change. Available in v2.0.0+; v1.x active-variant-only behaviour is preserved when matrix mode is off (the default).

### Two-line opt-in

```kotlin
// In your convention plugin OR gradle.properties — single touch-point per project.
kmpFlavors {
    buildMatrix.set(true)        // OR `gradle.properties: kmpFlavors.buildMatrix=true`
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
}
```

That's it. No per-module `build.gradle.kts` edit. The Zero-Touch Adoption tenet is verified by tests that diff sample-app module files between v1.x and v2.0 with matrix mode enabled and assert byte-equality.

### What you get

| Task | Purpose |
|---|---|
| `compile{Variant}Kotlin{Target}` per inactive variant × target | KGP-auto-generated |
| `generate{Variant}BuildConfig` per inactive variant | Per-variant `BuildKonfig.kt` |
| `assembleAll{Target}Variants` per target + `assembleAllVariants` super-aggregate | CI matrix sharding + dev convenience |
| `kmpFlavors.variants` (`NamedDomainObjectCollection<KmpFlavorVariant>`) | `matching { … }.configureEach { … }` consumer hook |
| `variantFilter { … setIgnore(true) }` | AGP-style filter; `buildType == "staging"` works |
| `publishMatrix.set(true)` | Per-variant classifier-tagged Maven publications (JVM) |

### Reference

- **Full reference** — [`docs/MATRIX_MODE.md`](docs/MATRIX_MODE.md) (consumer guide, Q24 adjacent-plugin compat table, KMPF-Vxx error quickref)
- **Migration from v1.x** — [`docs/MIGRATION_v1_to_v2.md`](docs/MIGRATION_v1_to_v2.md)
- **Error codes** — [`docs/ERROR_CODES.md`](docs/ERROR_CODES.md)
- **End-to-end sample** — [`samples/matrix-mode/`](samples/matrix-mode/README.md) — exercises every consumer surface in one project
- **Design RFC** — [`docs/RFC-v2.0-per-variant-compilation.md`](docs/RFC-v2.0-per-variant-compilation.md) (sealed 2026-05-13)
- **Migration assistant** — `./gradlew kmpFlavorsMigrateToV2` prints a per-project Markdown report (add `--json` for CI)

> **Consumer-facing promise**: every consumer KMP module's `build.gradle.kts` is byte-identical between v1.x and v2.0. Matrix mode is opted in via a single property (`kmpFlavors.buildMatrix=true` in `gradle.properties` OR `buildMatrix.set(true)` in the convention plugin). The plugin's internals register all per-variant compilations programmatically — never via consumer DSL.

## Compatibility Matrix

| `kmp-product-flavors` version | Kotlin | AGP (if Android consumer) | Gradle | JDK | Compose Multiplatform |
|---|---|---|---|---|---|
| `1.0.5` | `2.0.0`+ (built/tested against `2.3.0`) | `8.0`+ (built against `8.12.3`) | `8.0`+ | `17`+ | `1.6`+ (samples on `1.7.x`) |
| `1.1.0` | `2.0.0`+ (built/tested against `2.3.0`) | `8.0`+ (built against `8.12.3`) | `8.0`+ | `17`+ | `1.6`+ (samples on `1.7.x`) |

> Verified combinations are those exercised by `samples/` builds in CI. Combinations outside this matrix may work but are not actively tested. A multi-version CI matrix is planned for `v1.2.0` (Phase Q in `plan-layer/plans/PLAN-gaps-fix-260510-191003.md`).

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

```
Copyright 2026 MobileByteLabs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
