# KMP Product Flavors - Sample Projects

This directory contains sample projects demonstrating the KMP Product Flavors plugin.

## Samples Overview

| Sample | Description | Platforms |
|--------|-------------|-----------|
| [basic-flavors](basic-flavors) | Minimal plugin demo with multi-dimensional flavors | Desktop, iOS |
| [compose-multiplatform](compose-multiplatform) | Full Compose Multiplatform app (WIP) | Android, iOS, Desktop, WASM |
| [kmp-project-template](kmp-project-template) | Production KMP template from openMF | All platforms |
| [convention-integration](convention-integration) | Standalone convention plugin demo | JVM, iOS |

---

## basic-flavors

**Minimal demonstration of plugin features.**

### Features Demonstrated
- Multi-dimensional flavors (tier × environment)
- BuildConfig generation with custom fields
- Flavor-specific source sets
- Gradle tasks (listFlavors, validateFlavors)

### Flavor Configuration

| Dimension | Flavors | Default |
|-----------|---------|---------|
| tier | free, paid | free |
| environment | dev, staging, prod | dev |

**Variants:** freeDev, freeStaging, freeProd, paidDev, paidStaging, paidProd

### Run

```bash
# Build with specific flavor
./gradlew :samples:basic-flavors:assemble -PkmpFlavor=paidProd

# List all flavors
./gradlew :samples:basic-flavors:listFlavors

# Validate configuration
./gradlew :samples:basic-flavors:validateFlavors

# Generate IDE run configurations
./gradlew :samples:basic-flavors:generateRunConfigurations
```

### Source Structure

```
basic-flavors/
└── src/
    ├── commonMain/kotlin/          # Shared code (all variants)
    │   └── App.kt                  # Uses FlavorConfig
    ├── commonFree/kotlin/          # Free tier code
    │   └── AdManager.kt
    ├── commonPaid/kotlin/          # Paid tier code
    │   └── PremiumFeatures.kt
    └── commonDev/kotlin/           # Dev environment code
        └── DevTools.kt
```

---

## compose-multiplatform

**Full Compose Multiplatform application.** (Integration in progress)

### Status
- [ ] Integrate flavor plugin
- [ ] Add flavor-specific UI components
- [ ] Configure free/paid dimensions

### Platforms
- Android (via `androidTarget()`)
- iOS (arm64, x64, simulatorArm64)
- Desktop (JVM)
- Web (WASM)

---

## kmp-project-template

**Production-ready KMP template from [openMF](https://github.com/openMF/kmp-project-template).**

This sample demonstrates how to integrate kmp-product-flavors into a real-world KMP project using the **convention plugin pattern**. The plugin is automatically applied to all KMP modules through the convention plugins.

### Features

- **Automatic integration** - All KMP modules get flavors via `KMPLibraryConventionPlugin`
- **Convention plugin pattern** - Wraps kmp-product-flavors in reusable convention plugins
- **Centralized configuration** - Single source of truth in `KmpFlavors.kt`
- **Demo/Prod flavors** - Aligned with Android application flavors
- **Cross-platform BuildConfig** - Access flavor info from shared code

### How It Works

The plugin is applied in `KMPLibraryConventionPlugin` and `KMPCoreBaseLibraryConventionPlugin`:

```kotlin
// KMPLibraryConventionPlugin.kt
with(pluginManager) {
    apply("com.android.library")
    apply("org.jetbrains.kotlin.multiplatform")
    apply("org.convention.kmp.flavors") // <-- Automatically applied
    // ...
}
```

All modules using these convention plugins automatically get cross-platform flavor support.

### Flavor Configuration

Defined in [`org/convention/KmpFlavors.kt`](kmp-project-template/build-logic/convention/src/main/kotlin/org/convention/KmpFlavors.kt):

| Flavor | Description | Suffix |
|--------|-------------|--------|
| `demo` | Demo environment (default) | `.demo` |
| `prod` | Production environment | - |

### Generated BuildConfig

```kotlin
// Access from any shared module
import com.example.FlavorConfig

println(FlavorConfig.VARIANT_NAME)      // "demo"
println(FlavorConfig.IS_DEMO)           // true
println(FlavorConfig.BASE_URL)          // "https://demo-api.mifos.org"
println(FlavorConfig.ANALYTICS_ENABLED) // false
```

### Build Commands

```bash
cd samples/kmp-project-template

# Build with demo flavor (default)
./gradlew build

# Build with prod flavor
./gradlew build -PkmpFlavor=prod

# List all variants
./gradlew listFlavors

# Initialize flavor source directories
./gradlew kmpFlavorInit
```

### Convention Plugin Structure

```
build-logic/convention/
├── build.gradle.kts                           # Plugin dependency
├── src/main/kotlin/
│   ├── KMPFlavorsConventionPlugin.kt          # Convention plugin wrapper
│   ├── KMPLibraryConventionPlugin.kt          # Applies kmp.flavors
│   ├── KMPCoreBaseLibraryConventionPlugin.kt  # Applies kmp.flavors
│   └── org/convention/
│       ├── KmpFlavors.kt                      # Flavor definitions
│       └── KmpFlavorsBuildConfig.kt           # BuildConfig helpers
```

### Integration Steps (for other projects)

1. Add dependency to `build-logic/convention/build.gradle.kts`:
   ```kotlin
   implementation("io.github.mobilebytelabs.kmpflavors:flavor-plugin:1.0.0")
   ```

2. Copy convention plugin files from this sample

3. Apply `org.convention.kmp.flavors` in your KMP convention plugins

4. Customize `KmpFlavors.kt` for your project's flavors

---

## convention-integration

**Standalone convention plugin demo** - A minimal example showing convention plugin integration without the full kmp-project-template structure.

### Build

```bash
cd samples/convention-integration
./gradlew build
```

---

## Integration Guide

### Step 1: Add Plugin

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors") version "<version>"
}
```

### Step 2: Configure Flavors

```kotlin
kmpFlavors {
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.app")

    flavorDimensions {
        register("tier") { priority.set(0) }
        register("environment") { priority.set(1) }
    }

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
            buildConfigField("String", "API_URL", "\"https://dev-api.example.com\"")
        }
        register("prod") {
            dimension.set("environment")
            buildConfigField("String", "API_URL", "\"https://api.example.com\"")
        }
    }
}
```

### Step 3: Create Source Directories

```bash
mkdir -p src/commonFree/kotlin
mkdir -p src/commonPaid/kotlin
mkdir -p src/commonDev/kotlin
mkdir -p src/commonProd/kotlin
```

### Step 4: Use BuildConfig

```kotlin
// src/commonMain/kotlin/com/example/App.kt
import com.example.app.FlavorConfig

fun printConfig() {
    println("Variant: ${FlavorConfig.VARIANT_NAME}")
    println("Premium: ${FlavorConfig.IS_PREMIUM}")
    println("API URL: ${FlavorConfig.API_URL}")
}
```

### Step 5: Build

```bash
# Default flavor
./gradlew build

# Specific flavor
./gradlew build -PkmpFlavor=paidProd
```

---

## Quick Reference

| Task | Description |
|------|-------------|
| `listFlavors` | Show all variants and active one |
| `validateFlavors` | Validate flavor configuration |
| `generateFlavorBuildConfig` | Generate BuildConfig object |
| `generateRunConfigurations` | Create IDE run configs |

| Property | Description |
|----------|-------------|
| `-PkmpFlavor=<variant>` | Set active flavor variant |

---

## Documentation

- [GitHub Wiki](https://github.com/MobileByteLabs/kmp-product-flavors/wiki)
- [Getting Started](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/Getting-Started)
- [Build Variants](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/Build-Variants)
- [IDE Integration](https://github.com/MobileByteLabs/kmp-product-flavors/wiki/IDE-Integration)
