# KMP Product Flavors - Sample Projects

This directory contains sample projects demonstrating the KMP Product Flavors plugin.

## Samples Overview

| Sample | Description | Platforms |
|--------|-------------|-----------|
| [basic-flavors](basic-flavors) | Minimal plugin demo with multi-dimensional flavors | Desktop, iOS |
| [compose-multiplatform](compose-multiplatform) | Full Compose Multiplatform app (WIP) | Android, iOS, Desktop, WASM |
| [kmp-template-integration](kmp-template-integration) | Production KMP template from openMF | All platforms |

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

## kmp-template-integration

**Production-ready KMP template from [openMF](https://github.com/openMF/kmp-project-template).**

This is a git subtree synced from the upstream repository.

### Sync Upstream Changes

```bash
./scripts/sync-kmp-template.sh
```

### Build Separately

```bash
cd samples/kmp-template-integration
./gradlew build
```

### Features
- Modular architecture (core, feature modules)
- CI/CD workflows
- Multi-platform support (Android, iOS, Desktop, Web)

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
