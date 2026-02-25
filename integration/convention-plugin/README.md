# Convention Plugin Integration for kmp-project-template

This directory contains ready-to-use convention plugin files for integrating `kmp-product-flavors` with the [kmp-project-template](https://github.com/openMF/kmp-project-template).

## Overview

These files provide a centralized, project-wide flavor configuration that works across all platforms (Android, iOS, Desktop, Web) through the template's convention plugin system.

## Files

| File | Purpose |
|------|---------|
| `KMPFlavorsConventionPlugin.kt` | Main convention plugin that wraps kmp-product-flavors |
| `org/convention/KmpFlavors.kt` | Centralized flavor enum and configuration |
| `org/convention/KmpFlavorsBuildConfig.kt` | Helper functions for common build config patterns |

## Integration Steps

### 1. Add Dependency

Update `build-logic/convention/build.gradle.kts`:

```kotlin
dependencies {
    // Existing dependencies...

    // Add KMP Product Flavors plugin
    compileOnly("io.github.mobilebytelabs:kmp-product-flavors-gradle-plugin:1.0.0")
}
```

### 2. Update Version Catalog

Update `gradle/libs.versions.toml`:

```toml
[versions]
kmpProductFlavors = "1.0.0"

[libraries]
kmpProductFlavors-gradlePlugin = { module = "io.github.mobilebytelabs:kmp-product-flavors-gradle-plugin", version.ref = "kmpProductFlavors" }

[plugins]
kmpProductFlavors = { id = "io.github.mobilebytelabs.kmp-product-flavors", version.ref = "kmpProductFlavors" }
```

### 3. Copy Plugin Files

Copy the files from this directory to `build-logic/convention/src/main/kotlin/`:

```bash
# From kmp-product-flavors directory
cp integration/convention-plugin/src/main/kotlin/KMPFlavorsConventionPlugin.kt \
   path/to/kmp-project-template/build-logic/convention/src/main/kotlin/

cp -r integration/convention-plugin/src/main/kotlin/org/convention/* \
   path/to/kmp-project-template/build-logic/convention/src/main/kotlin/org/convention/
```

### 4. Register Plugin

Update `build-logic/convention/build.gradle.kts` to register the plugin:

```kotlin
gradlePlugin {
    plugins {
        // ... existing plugins ...

        register("kmpFlavors") {
            id = "org.convention.kmp.flavors"
            implementationClass = "KMPFlavorsConventionPlugin"
            description = "Configures KMP Product Flavors for cross-platform flavor support"
        }
    }
}
```

### 5. Update KMPLibraryConventionPlugin

Modify `KMPLibraryConventionPlugin.kt` to apply the new plugin:

```kotlin
class KMPLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.convention.kmp.flavors") // Add this line
                // ... rest of plugins
            }
            // ...
        }
    }
}
```

## Customization

### Changing Default Flavors

Edit `org/convention/KmpFlavors.kt` to customize:

```kotlin
enum class Flavor(...) {
    // Your custom flavors
    FREE(Dimension.TIER, isDefault = true, applicationIdSuffix = ".free"),
    PAID(Dimension.TIER, applicationIdSuffix = null),
}

val defaultDimensions = setOf(Dimension.TIER)
```

### Adding Module-Specific Build Config

In your module's `build.gradle.kts`:

```kotlin
plugins {
    id("org.convention.kmp.library")
}

kmpFlavors {
    flavors {
        named("demo") {
            buildConfigField("String", "API_URL", "\"https://demo-api.example.com\"")
        }
        named("prod") {
            buildConfigField("String", "API_URL", "\"https://api.example.com\"")
        }
    }
}
```

### Using Multiple Dimensions

```kotlin
extensions.configure<KmpFlavorExtension> {
    useProjectFlavors(
        dimensions = setOf(
            KmpFlavors.Dimension.CONTENT_TYPE,
            KmpFlavors.Dimension.ENVIRONMENT
        )
    )
}
```

## Generated Build Config

The plugin generates a Kotlin object accessible in `commonMain`:

```kotlin
// Generated: build/generated/kmpFlavors/commonMain/kotlin/.../FlavorConfig.kt
object FlavorConfig {
    const val VARIANT_NAME: String = "demo"
    const val FLAVOR: String = "demo"
    const val IS_DEMO: Boolean = true
    const val IS_PRODUCTION: Boolean = false
    const val API_BASE_URL: String = "https://demo-api.example.com"
}
```

## Migration from Android-only Flavors

If you're currently using `AppFlavor.kt` for Android-only flavors:

1. The new `KmpFlavors.kt` replaces `AppFlavor.kt`
2. `AndroidApplicationFlavorsConventionPlugin` can be deprecated
3. Flavor configuration is now shared across all platforms

See the full migration guide in `docs/GAP_PLAN_CONVENTION_PLUGIN_INTEGRATION.md`.

## Requirements

- Kotlin 2.1.0+
- Gradle 8.0+
- KMP Product Flavors 1.0.0+
