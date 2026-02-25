<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Gradle-8.0+-02303A?logo=gradle&logoColor=white" alt="Gradle">
</p>

# KMP Product Flavors

A Gradle plugin that brings Android-style product flavor support to **all Kotlin Multiplatform targets**.

<p align="center">
  <a href="https://github.com/MobileByteLabs/kmp-product-flavors/actions/workflows/build.yml"><img src="https://github.com/MobileByteLabs/kmp-product-flavors/actions/workflows/build.yml/badge.svg" alt="Build Status"></a>
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

This plugin supports **all Kotlin Multiplatform targets**:

| Platform | Targets | Status |
|----------|---------|--------|
| **Android** | `androidTarget()` | ✅ Full Support |
| **iOS** | `iosArm64()`, `iosX64()`, `iosSimulatorArm64()` | ✅ Full Support |
| **macOS** | `macosArm64()`, `macosX64()` | ✅ Full Support |
| **tvOS** | `tvosArm64()`, `tvosX64()`, `tvosSimulatorArm64()` | ✅ Full Support |
| **watchOS** | `watchosArm64()`, `watchosX64()`, `watchosSimulatorArm64()` | ✅ Full Support |
| **Desktop/JVM** | `jvm()`, `jvm("desktop")` | ✅ Full Support |
| **Linux** | `linuxX64()`, `linuxArm64()` | ✅ Full Support |
| **Windows** | `mingwX64()` | ✅ Full Support |
| **JavaScript** | `js()` | ✅ Full Support |
| **WebAssembly** | `wasmJs()`, `wasmWasi()` | ✅ Full Support |

## Features

- 🎯 **Multi-dimensional flavors** - Define dimensions (tier, environment, region) with automatic 2^n variant matrix
- ⚡ **BuildConfig generation** - Compile-time constants with `VARIANT_NAME`, `IS_<FLAVOR>` flags
- 📁 **Source set wiring** - Automatic `commonFree`, `androidFree`, `iosFree` source sets
- 🔗 **Intermediate source sets** - Optional `webMain` and `nativeMain` for shared code
- 📦 **Per-flavor dependencies** - Add dependencies for specific flavors only
- 🛠️ **IDE Run Configurations** - Auto-generated configs for each build variant
- 💾 **Build cache support** - Cacheable tasks for efficient builds

## Installation

### Gradle Plugin Portal (Recommended)

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.1.0"
    id("io.github.mobilebytelabs.kmp-product-flavors") version "1.0.0-alpha01"
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
    kotlin("multiplatform") version "2.1.0"
    id("io.github.mobilebytelabs.kmp-product-flavors") version "1.0.0-alpha01"
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
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.app")

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

## Generated BuildConfig

```kotlin
// build/generated/kmpFlavors/commonMain/kotlin/com/example/app/FlavorConfig.kt
package com.example.app

object FlavorConfig {
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
| **compose-multiplatform** | Full Compose app | [`samples/compose-multiplatform`](samples/compose-multiplatform) |
| **kmp-template-integration** | Production template | [`samples/kmp-template-integration`](samples/kmp-template-integration) |

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

- **Kotlin** 2.0.0+
- **Gradle** 8.0+
- **Kotlin Multiplatform** plugin applied

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
