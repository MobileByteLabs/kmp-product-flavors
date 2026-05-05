# Convention Plugins

The `build-logic` folder defines project-specific convention plugins, used to keep a single
source of truth for common module configurations.

This approach is heavily based on
[https://developer.squareup.com/blog/herding-elephants/](https://developer.squareup.com/blog/herding-elephants/)
and
[https://github.com/jjohannes/idiomatic-gradle](https://github.com/jjohannes/idiomatic-gradle).

By setting up convention plugins in `build-logic`, we can avoid duplicated build script setup,
messy `subproject` configurations, without the pitfalls of the `buildSrc` directory.

`build-logic` is an included build, as configured in the root
[`settings.gradle.kts`](../settings.gradle.kts).

Inside `build-logic` is a `convention` module, which defines a set of plugins that all normal
modules can use to configure themselves.

`build-logic` also includes a set of `Kotlin` files used to share logic between plugins themselves,
which is most useful for configuring Android components (libraries vs applications) with shared
code.

These plugins are *additive* and *composable*, and try to only accomplish a single responsibility.
Modules can then pick and choose the configurations they need.

## Convention Plugins

### Android Plugins

| Plugin | Description |
|--------|-------------|
| `org.convention.android.application` | Base Android application configuration |
| `org.convention.android.application.compose` | Android application with Compose |
| `org.convention.android.application.flavors` | Android application product flavors (demo/prod) |
| `org.convention.android.application.firebase` | Firebase integration (Analytics, Crashlytics) |
| `org.convention.android.application.lint` | Android Lint configuration |

### KMP & CMP Plugins

| Plugin | Description |
|--------|-------------|
| `org.convention.kmp.library` | KMP library with Android support (includes kmp.flavors) |
| `org.convention.kmp.core.base.library` | KMP core-base library (includes kmp.flavors) |
| `org.convention.kmp.koin` | Koin dependency injection for KMP |
| `org.convention.kmp.flavors` | **KMP Product Flavors** - Cross-platform flavor support |
| `org.convention.cmp.feature` | Compose Multiplatform feature module |
| `mifos.kmp.room` | Room database for KMP |

### Code Quality Plugins

| Plugin | Description |
|--------|-------------|
| `org.convention.detekt.plugin` | Detekt static analysis |
| `org.convention.spotless.plugin` | Spotless code formatting |
| `org.convention.ktlint.plugin` | Ktlint code style |
| `org.convention.git.hooks` | Git hooks for pre-commit checks |

## KMP Product Flavors Integration

This project integrates [kmp-product-flavors](https://github.com/MobileByteLabs/kmp-product-flavors)
for cross-platform flavor support that aligns with Android application flavors.

### Automatic Application

KMP flavors are automatically applied when using:
- `org.convention.kmp.library`
- `org.convention.kmp.core.base.library`

All KMP modules get cross-platform flavor support without any additional configuration.

### Flavor Configuration

Flavors are configured in [`org/convention/KmpFlavors.kt`](convention/src/main/kotlin/org/convention/KmpFlavors.kt):

| Flavor | Description | Default |
|--------|-------------|---------|
| `demo` | Demo/development environment | Yes |
| `prod` | Production environment | No |

### Generated BuildConfig

All KMP modules have access to flavor-specific constants:

```kotlin
import com.example.FlavorConfig

// Access from shared code
println("Variant: ${FlavorConfig.VARIANT_NAME}")  // "demo" or "prod"
println("Is Demo: ${FlavorConfig.IS_DEMO}")       // true/false
println("Is Prod: ${FlavorConfig.IS_PROD}")       // true/false
println("Base URL: ${FlavorConfig.BASE_URL}")     // flavor-specific URL
```

### Source Sets

Flavor-specific source sets are available:

```
src/
├── commonMain/      # All variants
├── commonDemo/      # Demo flavor (all platforms)
├── commonProd/      # Prod flavor (all platforms)
├── androidDemo/     # Android + Demo
├── iosDemo/         # iOS + Demo
└── desktopDemo/     # Desktop + Demo
```

### Build Commands

```bash
# Build with demo flavor (default)
./gradlew build

# Build with prod flavor
./gradlew build -PkmpFlavor=prod

# List all variants
./gradlew listFlavors

# Initialize source directories
./gradlew kmpFlavorInit
```

## Helper Files

Located in `convention/src/main/kotlin/org/convention/`:

| File | Description |
|------|-------------|
| `AppFlavor.kt` | Android application flavor definitions |
| `KmpFlavors.kt` | KMP cross-platform flavor configuration |
| `KmpFlavorsBuildConfig.kt` | BuildConfig field helpers |
| `KotlinAndroid.kt` | Kotlin Android configuration |
| `KotlinMultiplatform.kt` | Kotlin Multiplatform configuration |
| `AndroidCompose.kt` | Compose configuration |
| `HierarchyTemplate.kt` | Source set hierarchy template |
