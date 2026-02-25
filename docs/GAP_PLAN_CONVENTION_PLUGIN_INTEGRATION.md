# Gap Planning: Convention Plugin Integration for kmp-project-template

## Overview

This plan outlines the integration of the `kmp-product-flavors` library with the [kmp-project-template](https://github.com/openMF/kmp-project-template)'s convention plugin system. The goal is to provide a single-place integration that can be applied project-wide through custom Gradle convention plugins.

## Current State Analysis

### kmp-project-template Convention Plugins

The template uses a sophisticated convention plugin system in `build-logic/convention/`:

| Plugin ID | Class | Purpose |
|-----------|-------|---------|
| `org.convention.kmp.library` | `KMPLibraryConventionPlugin` | KMP library module setup |
| `org.convention.android.application.flavors` | `AndroidApplicationFlavorsConventionPlugin` | Android-only flavors |
| `org.convention.cmp.feature` | `CMPFeatureConventionPlugin` | Compose Multiplatform features |

### Current Flavor Handling (Android-only)

```kotlin
// AppFlavor.kt - Only supports Android
enum class FlavorDimension { contentType }
enum class AppFlavor(val dimension: FlavorDimension, val applicationIdSuffix: String?) {
    demo(FlavorDimension.contentType, ".demo"),
    prod(FlavorDimension.contentType)
}

fun configureFlavors(commonExtension: CommonExtension<...>) {
    // Only configures Android product flavors
}
```

**Limitations:**
- Android-only (no KMP source set support)
- Hardcoded flavors (demo/prod)
- No build config generation for common code
- No variant filtering

## Proposed Integration

### 1. New Convention Plugin: `KMPFlavorsConventionPlugin`

Create a new convention plugin that wraps the `kmp-product-flavors` library:

```kotlin
// KMPFlavorsConventionPlugin.kt
class KMPFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply the KMP Flavors plugin
            pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")

            // Configure with project-wide defaults
            extensions.configure<KmpFlavorExtension> {
                // Default configuration from centralized KmpFlavors.kt
                configureKmpFlavors(this)
            }
        }
    }
}
```

### 2. Centralized Flavor Configuration: `KmpFlavors.kt`

```kotlin
// org/convention/KmpFlavors.kt
package org.convention

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension

/**
 * Centralized KMP Flavor configuration for the entire project.
 * Replaces the Android-only AppFlavor.kt approach.
 */
object KmpFlavors {

    /**
     * Flavor dimensions used across the project.
     */
    enum class Dimension(val priority: Int) {
        CONTENT_TYPE(0),
        ENVIRONMENT(1)
    }

    /**
     * Product flavors available in the project.
     */
    enum class Flavor(
        val dimension: Dimension,
        val isDefault: Boolean = false,
        val applicationIdSuffix: String? = null,
        val bundleIdSuffix: String? = null,
    ) {
        // Content type dimension
        DEMO(Dimension.CONTENT_TYPE, isDefault = true, applicationIdSuffix = ".demo", bundleIdSuffix = ".demo"),
        PROD(Dimension.CONTENT_TYPE, applicationIdSuffix = null, bundleIdSuffix = null),

        // Environment dimension (optional)
        DEV(Dimension.ENVIRONMENT, isDefault = true),
        STAGING(Dimension.ENVIRONMENT),
        PRODUCTION(Dimension.ENVIRONMENT)
    }
}

/**
 * Configure KMP Flavors with project-wide settings.
 */
fun configureKmpFlavors(
    extension: KmpFlavorExtension,
    dimensions: Set<KmpFlavors.Dimension> = setOf(KmpFlavors.Dimension.CONTENT_TYPE),
    flavors: Set<KmpFlavors.Flavor> = setOf(KmpFlavors.Flavor.DEMO, KmpFlavors.Flavor.PROD),
    generateBuildConfig: Boolean = true,
    buildConfigPackage: String? = null,
) {
    extension.apply {
        this.generateBuildConfig.set(generateBuildConfig)
        buildConfigPackage?.let { this.buildConfigPackage.set(it) }

        // Register dimensions
        flavorDimensions {
            dimensions.forEach { dim ->
                register(dim.name.lowercase()) {
                    priority.set(dim.priority)
                }
            }
        }

        // Register flavors
        this.flavors {
            flavors.forEach { flavor ->
                register(flavor.name.lowercase()) {
                    dimension.set(flavor.dimension.name.lowercase())
                    isDefault.set(flavor.isDefault)
                    flavor.applicationIdSuffix?.let { applicationIdSuffix.set(it) }
                    flavor.bundleIdSuffix?.let { bundleIdSuffix.set(it) }
                }
            }
        }
    }
}
```

### 3. Integration with Existing Plugins

#### Update `KMPLibraryConventionPlugin`

```kotlin
class KMPLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.convention.kmp.flavors") // NEW: Apply KMP Flavors
                // ... other plugins
            }

            configureKotlinMultiplatform()

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // Remove: configureFlavors(this) - Android flavors now handled by KMP Flavors
            }

            // Configure KMP-specific flavor settings
            extensions.configure<KmpFlavorExtension> {
                buildConfigPackage.set("${target.group}.${target.name.replace("-", ".")}")
            }
        }
    }
}
```

#### Update `CMPFeatureConventionPlugin`

```kotlin
class CMPFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.convention.kmp.library")
                // KMP Flavors already applied via kmp.library
            }

            // Feature-specific flavor configuration
            extensions.configure<KmpFlavorExtension> {
                // Features can add their own build config fields
            }
        }
    }
}
```

### 4. Build Logic Dependencies

Update `build-logic/convention/build.gradle.kts`:

```kotlin
dependencies {
    // Existing dependencies...
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)

    // NEW: KMP Product Flavors plugin
    compileOnly(libs.kmpProductFlavors.gradlePlugin)
}
```

Update `gradle/libs.versions.toml`:

```toml
[versions]
kmpProductFlavors = "1.0.0"

[libraries]
kmpProductFlavors-gradlePlugin = { module = "io.github.mobilebytelabs:kmp-product-flavors-gradle-plugin", version.ref = "kmpProductFlavors" }

[plugins]
kmpProductFlavors = { id = "io.github.mobilebytelabs.kmp-product-flavors", version.ref = "kmpProductFlavors" }
```

### 5. New Files to Create

| File | Location | Purpose |
|------|----------|---------|
| `KMPFlavorsConventionPlugin.kt` | `build-logic/convention/src/main/kotlin/` | Convention plugin wrapper |
| `KmpFlavors.kt` | `build-logic/convention/src/main/kotlin/org/convention/` | Centralized flavor configuration |
| `KmpFlavorsBuildConfig.kt` | `build-logic/convention/src/main/kotlin/org/convention/` | Build config field helpers |

### 6. Migration Path

#### Phase 1: Add Plugin (Non-breaking)
1. Add `kmp-product-flavors` dependency
2. Create `KMPFlavorsConventionPlugin`
3. Create `KmpFlavors.kt` configuration
4. Register new plugin ID: `org.convention.kmp.flavors`

#### Phase 2: Integrate (Opt-in)
1. Update `KMPLibraryConventionPlugin` to apply flavors
2. Keep `AndroidApplicationFlavorsConventionPlugin` for backward compatibility
3. Allow modules to opt-in to KMP flavors

#### Phase 3: Migrate (Replace)
1. Migrate all modules to use KMP flavors
2. Deprecate `AndroidApplicationFlavorsConventionPlugin`
3. Remove `AppFlavor.kt` Android-only configuration

## Implementation Tasks

### Task 1: Create Convention Plugin Files

**Files to create in kmp-project-template:**

```
build-logic/convention/src/main/kotlin/
├── KMPFlavorsConventionPlugin.kt          # Plugin wrapper
└── org/convention/
    ├── KmpFlavors.kt                       # Centralized flavor enum/config
    └── KmpFlavorsBuildConfig.kt            # Build config helpers
```

### Task 2: Update Build Configuration

**Update `build-logic/convention/build.gradle.kts`:**
- Add kmp-product-flavors dependency
- Register `org.convention.kmp.flavors` plugin

### Task 3: Update Version Catalog

**Update `gradle/libs.versions.toml`:**
- Add kmpProductFlavors version
- Add gradle plugin library
- Add plugin alias

### Task 4: Update Existing Plugins

**Modify `KMPLibraryConventionPlugin.kt`:**
- Apply `org.convention.kmp.flavors`
- Configure default package name

**Modify `CMPFeatureConventionPlugin.kt`:**
- Inherit KMP flavors from library plugin
- Add feature-specific configuration

### Task 5: Create Documentation

**Create `docs/KMP_FLAVORS_INTEGRATION.md`:**
- Setup instructions
- Configuration options
- Migration guide from Android-only flavors

## Benefits

| Benefit | Description |
|---------|-------------|
| **Single Source of Truth** | Flavor configuration in one place (`KmpFlavors.kt`) |
| **Cross-Platform** | Works for Android, iOS, Desktop, Web |
| **BuildConfig Support** | Generated Kotlin object accessible everywhere |
| **Convention-Based** | Automatic application via convention plugins |
| **Type-Safe** | Enum-based flavor definitions |
| **Backward Compatible** | Existing Android flavors still work |

## Example Usage

### Module-level build.gradle.kts

```kotlin
// No flavor configuration needed - applied via convention plugin
plugins {
    id("org.convention.kmp.library")
}

// Optional: Override defaults
kmpFlavors {
    // Add module-specific build config fields
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

### Accessing Flavor Info in Code

```kotlin
// commonMain/kotlin/com/example/app/Config.kt
import com.example.app.FlavorConfig

object AppConfig {
    val isDemoMode: Boolean = FlavorConfig.FLAVOR == "demo"
    val apiUrl: String = FlavorConfig.API_URL
}
```

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking existing builds | High | Phase-based migration, backward compatibility |
| Plugin version conflicts | Medium | Use `compileOnly` dependency, version catalog |
| Learning curve | Low | Documentation, examples in sample module |

## Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| Phase 1 | 1-2 days | Convention plugin files created |
| Phase 2 | 1 day | Integration with existing plugins |
| Phase 3 | 2-3 days | Migration of all modules |

## Next Steps

1. ✅ Create this gap plan document
2. ✅ Create convention plugin files in kmp-product-flavors
3. ✅ Test integration with sample project
4. ✅ Document integration steps for kmp-project-template
5. ⬜ Submit PR to kmp-project-template

## Implementation Status

### Completed

| Item | Location |
|------|----------|
| Convention plugin files | `integration/convention-plugin/src/main/kotlin/` |
| Sample project | `samples/convention-integration/` |
| Integration tests | `build-logic/flavor-plugin/src/test/kotlin/.../ConventionPluginIntegrationTest.kt` |
| Install script | `integration/install-to-kmp-project-template.sh` |
| Documentation | `integration/convention-plugin/README.md`, Main README |

### Test Results

- **79 tests** passing (0 failures)
- Including 5 convention plugin integration tests

---

*Generated by Claude Code for KMP Product Flavors v1.0.0*
