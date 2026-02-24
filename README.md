# KMP Product Flavors

A Gradle plugin that brings Android-style product flavor support to Kotlin Multiplatform projects.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Features

- **Multi-dimensional flavors** - Define multiple dimensions (tier, environment, region) with automatic variant matrix generation
- **BuildConfig generation** - Compile-time constants with `VARIANT_NAME`, `IS_<FLAVOR>` flags, and custom fields
- **Source set wiring** - Automatic `commonFree`, `androidFree`, `iosFree`, etc. source sets with proper `dependsOn` relationships
- **Intermediate source sets** - Optional `webMain` and `nativeMain` for shared platform code
- **Per-flavor dependencies** - Add dependencies that only apply to specific flavors
- **IDE-friendly** - All flavor directories are recognized by the IDE, even inactive ones
- **Build cache support** - Cacheable tasks for efficient incremental builds

## Installation

### Using Plugin DSL

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.1.0"
    id("io.github.anthropic.kmp-product-flavors") version "1.0.0-alpha01"
}
```

### Using included build

```kotlin
// settings.gradle.kts
includeBuild("path/to/kmp-product-flavors/build-logic")

// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("io.github.anthropic.kmp-product-flavors")
}
```

## Quick Start

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.anthropic.kmp-product-flavors")
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")
}

kmpFlavors {
    // Generate BuildConfig object
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

## DSL Reference

### Extension Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `generateBuildConfig` | `Property<Boolean>` | `true` | Generate BuildConfig Kotlin object |
| `buildConfigPackage` | `Property<String>` | Required | Package name for generated config |
| `buildConfigClassName` | `Property<String>` | `"FlavorConfig"` | Class name for generated config |
| `activeFlavor` | `Property<String>` | Auto | Active variant name |
| `createIntermediateSourceSets` | `Property<Boolean>` | `true` | Create webMain/nativeMain |

### Flavor Dimension

```kotlin
flavorDimensions {
    register("dimensionName") {
        priority.set(0) // Lower = first in variant name
    }
}
```

### Flavor Configuration

```kotlin
flavors {
    register("flavorName") {
        dimension.set("dimensionName")        // Required if dimensions defined
        isDefault.set(true)                   // Default for this dimension

        // Build config fields
        buildConfigField("Boolean", "DEBUG", "true")
        buildConfigField("String", "API_KEY", "\"abc123\"")
        buildConfigField("Int", "MAX_RETRIES", "3")

        // Per-flavor dependencies
        dependency("implementation", "com.example:ads-sdk:1.0.0")

        // Suffixes and extras
        applicationIdSuffix.set(".free")
        versionNameSuffix.set("-free")
        extras.put("custom_key", "custom_value")
    }
}
```

## Source Set Hierarchy

The plugin creates source sets following this hierarchy:

```
commonMain
├── commonFree ──────────────── Free-tier common code
│   ├── androidFree
│   ├── iosFree
│   ├── desktopFree
│   └── webFree (if web targets exist)
│       ├── jsFree
│       └── wasmJsFree
└── commonPaid ──────────────── Paid-tier common code
    ├── androidPaid
    ├── iosPaid
    └── ...
```

With intermediate source sets enabled:

```
commonMain
├── nativeMain
│   ├── iosMain
│   └── macosMain
└── webMain
    ├── jsMain
    └── wasmJsMain
```

## Gradle Tasks

| Task | Description |
|------|-------------|
| `generateFlavorBuildConfig` | Generates the BuildConfig Kotlin object |
| `validateFlavors` | Validates flavor configuration |
| `listFlavors` | Lists all variants and marks the active one |

## Setting Active Flavor

### Via Gradle property (recommended for CI)

```bash
./gradlew build -PkmpFlavor=paidProd
```

### Via gradle.properties

```properties
kmpFlavor=freeDev
```

### Via DSL

```kotlin
kmpFlavors {
    activeFlavor.set("freeDev")
}
```

## Generated BuildConfig

The plugin generates a Kotlin object in `commonMain`:

```kotlin
// build/generated/kmpFlavors/commonMain/kotlin/com/example/app/FlavorConfig.kt
package com.example.app

object FlavorConfig {
    const val VARIANT_NAME: String = "freeDev"

    // Flavor flags
    const val IS_FREE: Boolean = true
    const val IS_PAID: Boolean = false
    const val IS_DEV: Boolean = true
    const val IS_PROD: Boolean = false

    // Custom fields (merged from active flavors)
    const val IS_PREMIUM: Boolean = false
    const val BASE_URL: String = "https://dev-api.example.com"
}
```

## Variant Matrix

With 2 dimensions, the plugin generates a cartesian product of variants:

| Dimension | Flavors | Default |
|-----------|---------|---------|
| tier | free, paid | free |
| environment | dev, staging, prod | dev |

**Generated variants:** `freeDev`, `freeStaging`, `freeProd`, `paidDev`, `paidStaging`, `paidProd`

**Default variant:** `freeDev` (combining defaults from each dimension)

## Best Practices

1. **Keep flavor code minimal** - Only put truly flavor-specific code in flavor source sets
2. **Use expect/actual for platform differences** - Not flavors
3. **BuildConfig for compile-time constants** - Use for values that differ per flavor
4. **Test multiple flavors in CI** - Run tests with different `-PkmpFlavor` values
5. **Set clear defaults** - Mark one flavor per dimension as default

## Requirements

- Kotlin 2.0.0+
- Gradle 8.0+
- Kotlin Multiplatform plugin applied

## License

```
Copyright 2026 Anthropic, Inc.

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
