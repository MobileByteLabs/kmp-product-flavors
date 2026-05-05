# Convention Integration Sample

This sample demonstrates how to integrate `kmp-product-flavors` with a project using convention plugins, similar to the pattern used in [kmp-project-template](https://github.com/openMF/kmp-project-template).

## Project Structure

```
convention-integration/
├── build-logic/
│   └── convention/
│       ├── build.gradle.kts                    # Convention plugin build
│       └── src/main/kotlin/
│           ├── KMPFlavorsConventionPlugin.kt   # Flavor convention plugin
│           ├── KMPLibraryConventionPlugin.kt   # Library convention plugin
│           └── org/convention/
│               └── KmpFlavors.kt               # Centralized flavor config
├── app/                                        # Main application module
├── core/                                       # Core library module
├── feature/                                    # Feature module
├── gradle/
│   └── libs.versions.toml                      # Version catalog
└── settings.gradle.kts
```

## How It Works

### 1. Centralized Configuration (`KmpFlavors.kt`)

All flavor dimensions and flavors are defined in one place:

```kotlin
object KmpFlavors {
    enum class Dimension(val priority: Int) {
        CONTENT_TYPE(0),
    }

    enum class Flavor(
        val dimension: Dimension,
        val isDefault: Boolean = false,
        val applicationIdSuffix: String? = null,
    ) {
        DEMO(Dimension.CONTENT_TYPE, isDefault = true, applicationIdSuffix = ".demo"),
        PROD(Dimension.CONTENT_TYPE),
    }
}
```

### 2. Convention Plugin (`KMPFlavorsConventionPlugin.kt`)

Wraps the `kmp-product-flavors` plugin and applies centralized configuration:

```kotlin
class KMPFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")
            extensions.configure<KmpFlavorExtension> {
                configureKmpFlavors(this)
            }
        }
    }
}
```

### 3. Library Convention Plugin (`KMPLibraryConventionPlugin.kt`)

Applies Kotlin Multiplatform and flavors together:

```kotlin
class KMPLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("org.convention.kmp.flavors")
            // Configure targets...
        }
    }
}
```

### 4. Module Usage

Modules just apply the convention plugin:

```kotlin
// core/build.gradle.kts
plugins {
    id("org.convention.kmp.library")
}

// Optional: Add module-specific config
kmpFlavors {
    flavors {
        named("demo") {
            buildConfigField("String", "API_URL", "\"https://demo-api.example.com\"")
        }
    }
}
```

## Benefits

| Benefit | Description |
|---------|-------------|
| **Single Source of Truth** | Flavors defined once in `KmpFlavors.kt` |
| **Automatic Application** | Convention plugin applies to all modules |
| **Module Customization** | Modules can add their own build config fields |
| **Type Safety** | Enum-based flavor definitions |
| **Cross-Platform** | Works for all KMP targets |

## Running the Sample

```bash
# List available flavors
./gradlew :core:listFlavors

# Generate build config for demo flavor
./gradlew :core:generateFlavorBuildConfig

# Generate build config for prod flavor
./gradlew :core:generateFlavorBuildConfig -PkmpFlavor=prod
```

## Integration with kmp-project-template

To use this pattern in [kmp-project-template](https://github.com/openMF/kmp-project-template):

1. Copy the convention plugin files to `build-logic/convention/src/main/kotlin/`
2. Add the kmp-product-flavors dependency
3. Register the plugin in `build.gradle.kts`
4. Update existing convention plugins to use the new flavor system

See the main [integration guide](../../integration/convention-plugin/README.md) for detailed steps.
