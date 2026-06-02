# Migrating Your Consumer Project to AGP 9.x

> Companion to [`MIGRATION_v2.6_TO_v2.7.md`](MIGRATION_v2.6_TO_v2.7.md). This doc covers AGP-9-specific changes your consumer build needs, independent of bumping the kmp-product-flavors version.

The plugin's reflection-based bridge ([`AgpBridge.kt`](../build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/AgpBridge.kt)) survives the AGP 8.x → 9.x transition unchanged because every API it looks up — `finalizeDsl`, `beforeVariants`, `getFlavorDimensions`, `getProductFlavors`, `VariantBuilder.{getName, setEnabled}` — stayed stable across the major bump. **The plugin works on AGP 8.2+ and AGP 9.x without source changes.**

What changes is the *consumer-facing* AGP DSL surface. If your build scripts touch any of these surfaces, you'll need targeted edits.

---

## What AGP 9 breaks

### `CommonExtension` type parameters dropped

AGP 8.x exposed `CommonExtension<BuildFeatures, BuildType, DefaultConfig, ProductFlavor, AndroidResources, Installation>` — a six-parameter generic. AGP 9 simplified this to `CommonExtension` with no type parameters; the DSL methods (`buildFeatures`, `testOptions`, `productFlavors`, `findByName`) moved to the concrete subtypes (`ApplicationExtension`, `LibraryExtension`).

**Before (AGP 8.x convention plugin helper):**

```kotlin
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        buildFeatures { compose = true }
        testOptions { unitTests { isIncludeAndroidResources = true } }
    }
}
```

**After (AGP 9 convention plugin helper):**

```kotlin
internal fun Project.configureAndroidCompose(extension: Any) {
    when (extension) {
        is ApplicationExtension -> extension.apply {
            buildFeatures { compose = true }
            testOptions { unitTests { isIncludeAndroidResources = true } }
        }
        is LibraryExtension -> extension.apply {
            buildFeatures { compose = true }
            testOptions { unitTests { isIncludeAndroidResources = true } }
        }
    }
}
```

Alternative: split into two functions per extension subtype. The duplication is intentional — AGP 9 deliberately surfaces the differentiation.

### `dataBinding` deprecated

`buildFeatures { dataBinding = true }` is removed in AGP 9. If your Android app or library uses dataBinding, migrate to Compose or Jetpack ViewBinding. If the `dataBinding = true` line is dead config (e.g. Compose-only modules carrying it for historical reasons), just delete the line.

```kotlin
// Before (AGP 8.x, may have been dead config):
buildFeatures {
    dataBinding = true
    buildConfig = true
    resValues = true
}

// After (AGP 9):
buildFeatures {
    buildConfig = true
    resValues = true
}
```

### `com.android.kotlin.multiplatform.library` replaces `com.android.library + kotlin("multiplatform")` co-application

AGP 9 refuses to apply `com.android.library` alongside `kotlin("multiplatform")` in the same module — they conflict on source-set ownership. The replacement is the unified `com.android.kotlin.multiplatform.library` plugin (introduced in AGP 8.2) with the `kotlin { androidLibrary { } }` DSL.

```kotlin
// Before (AGP 8.x):
plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    namespace = "com.example.lib"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

kotlin {
    androidTarget()
    iosArm64()
}

// After (AGP 9):
plugins {
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "com.example.lib"
        compileSdk = 36
        minSdk = 24
    }
    iosArm64()
}
```

App modules are unaffected — `com.android.application` continues to work with `kotlin("multiplatform")` co-applied, only library modules need this swap.

### `dependencyGuard` reads variants at configuration time

This isn't an AGP 9 change, but it surfaces in interaction with the plugin's AGP bridge. `dependencyGuard { configuration("prodReleaseRuntimeClasspath") { ... } }` reads variant resolution during the configuration phase, which can lock `flavorDimensions` before the plugin's `finalizeDsl` callback fires. If you see `It is too late to modify flavorDimensions`, defer the dependencyGuard configuration to `afterEvaluate`:

```kotlin
afterEvaluate {
    dependencyGuard {
        configuration("prodReleaseRuntimeClasspath") {
            modules = true
            tree = true
        }
    }
}
```

---

## How this plugin handles AGP 9

The plugin runtime uses reflection (`AgpBridge.kt`), so v2.7 of this plugin works transparently on both AGP 8.2+ and AGP 9.x consumers. No DSL changes required if you stick with the standard `kmpFlavors {}` block:

```kotlin
kmpFlavors {
    buildConfigPackage.set("com.example.app")
    flavors {
        register("demo") { isDefault.set(true) }
        register("prod")
    }
    buildTypes {
        register("debug") { isDefault.set(true); isDebuggable.set(true) }
        register("release") { isMinifyEnabled.set(true) }
    }
}
```

The plugin's `AgpBridge` reflects against AGP and registers flavors / build types on the consumer's `ApplicationExtension` / `LibraryExtension` via `androidComponents.finalizeDsl` — works identically on AGP 8.2+ and 9.x.

---

## Matrix-tested AGP versions in v2.7

| Status | AGP | Tested via |
|---|---|---|
| ✅ Matrix-tested | **8.2.2** | `.github/workflows/agp-matrix-compat.yml` |
| ✅ Matrix-tested | **8.5.2** | same |
| ✅ Matrix-tested | **8.10.0** | same |
| ✅ Matrix-tested | **9.2.1** | same (new in v2.7) |
| ❌ Not tested | AGP < 8.2 | Floor since v2.6 |
| ❌ Not tested | AGP 9.0–9.1 | Skipped; AGP 9.2.1 is the v2.7 9.x stable target |

`9.0.0-rc01` (matrix-tested in v2.6) was dropped from the v2.7 matrix — superseded by the stable 9.2.1.

---

## See also

- [`MIGRATION_v2.6_TO_v2.7.md`](MIGRATION_v2.6_TO_v2.7.md) — plugin migration cookbook (opens "You do not need to migrate.")
- [`COMPATIBILITY_MATRIX.md`](COMPATIBILITY_MATRIX.md) — version floor + built-against table
- [`KMP_AGP_PARITY.md`](KMP_AGP_PARITY.md) — how the v2.6 bridge keeps KMP and AGP variant sets in sync
- AGP 9 upgrade guide: <https://developer.android.com/build/agp-upgrade-assistant>
