# kmp-product-flavors: Integration Guide for kmp-project-template

> **Related:** [PRODUCT_FLAVORS](PRODUCT_FLAVORS.md) · [BUILD_VARIANTS](BUILD_VARIANTS.md)


**Machine-readable agent playbook.** This document is structured for AI agents (Claude Code, etc.) to read and execute without ambiguity. Every file path is absolute relative to the project root. Every code block is copy-paste ready.

---

## METADATA

```yaml
plugin_id: io.github.mobilebytelabs.kmp-product-flavors
plugin_version: 1.0.5
artifact_group: io.github.mobilebytelabs.kmpflavors
artifact_name: flavor-plugin
extension_class: com.mobilebytelabs.kmpflavors.KmpFlavorExtension
generated_class: org.openmf.kmptemplate.FlavorConfig   # configurable via buildConfigPackage + buildConfigClassName
sample_reference: samples/kmp-project-template/
```

---

## CAPABILITIES INDEX

| Capability | What it does | DSL key |
|---|---|---|
| **FlavorConfig codegen** | Generates a Kotlin object with compile-time constants for every flavor+buildType combination | `buildConfigPackage`, `buildConfigClassName`, `buildConfigField(...)` |
| **Flavor dimensions** | N independent axes of variation (e.g. consumer × tier) | `flavorDimensions { register(...) { priority } }` |
| **Flavor source sets** | Wires `commonFoo/` source set into the compilation for each flavor | automatic once a flavor named `foo` is declared |
| **Build types** | Debug/staging/release with per-type constants and flags | `buildTypes { register(...) { isDebuggable, isMinifyEnabled, applicationIdSuffix, ... } }` |
| **AGP bridge — product flavors** 🟡 *Planned (v1.1.0)* | Propagates KMP flavor dimensions + product flavors to Android's AGP | `bridgeAgpProductFlavors.set(true)` |
| **AGP bridge — build types** 🟡 *Planned (v1.1.0)* | Propagates KMP build types to AGP (replaces manual `buildTypes {}` in `cmp-android`) | `bridgeAgpBuildTypes.set(true)` |
| **AppID / BundleID suffixes** | Appends per-flavor suffix to Android `applicationId` and iOS `CFBundleIdentifier` | `applicationIdSuffix`, `bundleIdSuffix` on a flavor |
| **IS_xxx boolean flags** | Auto-generates `IS_INTERNAL`, `IS_DEMO`, `IS_ADVANCED`, `IS_BASIC`, `IS_DEBUG`, etc. from flavor/buildType names | automatic |
| **expect/actual compile-time exclusion** | Flavor source sets can hold `actual` implementations — code not in a source set is absent from the binary (not just hidden) | flavor source set pattern |
| **Variant listing** | Gradle task that prints all active variants | `./gradlew listFlavors` |

---

## PRECONDITIONS

Before running any integration step, verify:

1. `build-logic/convention/build.gradle.kts` exists (convention plugin module)
2. `gradle/libs.versions.toml` exists
3. `build-logic/settings.gradle.kts` exists
4. `settings.gradle.kts` at project root has `pluginManagement { includeBuild("build-logic") }`
5. If consuming from local source: `kmp-product-flavors/build-logic/` is accessible at `../../build-logic` relative to the sample

---

## INTEGRATION STEPS

### Step 1 — Version catalog: `gradle/libs.versions.toml`

Add exactly these entries:

```toml
[versions]
kmpProductFlavors = "1.0.5"

[plugins]
kmp-product-flavors = { id = "io.github.mobilebytelabs.kmp-product-flavors", version.ref = "kmpProductFlavors" }
kmp-flavors-convention = { id = "org.convention.kmp.flavors", version = "unspecified" }
```

> `kmp-flavors-convention` uses `version = "unspecified"` because it is resolved from the local `build-logic` composite build, not Maven.

---

### Step 2 — build-logic dependency: `build-logic/convention/build.gradle.kts`

Add one `compileOnly` dependency and register the plugin:

```kotlin
dependencies {
    // existing deps ...
    compileOnly("io.github.mobilebytelabs.kmpflavors:flavor-plugin")   // ADD THIS
}

gradlePlugin {
    plugins {
        // existing registrations ...
        register("kmpFlavors") {                                          // ADD THIS BLOCK
            id = "org.convention.kmp.flavors"
            implementationClass = "KMPFlavorsConventionPlugin"
        }
    }
}
```

---

### Step 3 — Composite build wiring: `build-logic/settings.gradle.kts`

Add the includeBuild that makes `flavor-plugin` JAR available when compiling convention plugins:

```kotlin
includeBuild("../../../build-logic")   // path to kmp-product-flavors/build-logic (local source)
// Remove the above line and use mavenCentral() once the plugin is published to Maven Central
```

Full file after change:

```kotlin
includeBuild("../../../build-logic")

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

---

### Step 4 — Root settings: `settings.gradle.kts`

Add the second `includeBuild` inside `pluginManagement` so the plugin ID `io.github.mobilebytelabs.kmp-product-flavors` resolves at project configuration time:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    includeBuild("../../build-logic")   // ADD THIS — path to kmp-product-flavors/build-logic
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

---

### Step 5 — Convention plugin: `build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt`

Create this file. It is the single place where all flavor/buildType/BuildConfig configuration lives for the entire project:

```kotlin
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class KMPFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")

            extensions.configure<KmpFlavorExtension> {
                // Generated object: org.openmf.kmptemplate.FlavorConfig
                buildConfigPackage.set("org.openmf.kmptemplate")
                buildConfigClassName.set("FlavorConfig")

                // ── Dimension 1: consumer ────────────────────────────────────
                // Who is this build for? Controls server URLs + URL override.
                flavorDimensions {
                    register("consumer") { priority.set(0) }
                    register("tier")     { priority.set(1) }
                }

                flavors {
                    // --- consumer dimension ---
                    register("internal") {
                        dimension.set("consumer")
                        isDefault.set(true)
                        buildConfigField("String",  "CLIENT_ID",         "\"internal\"")
                        buildConfigField("String",  "API_URL_DEBUG",     "\"https://dev.yourdomain.com\"")
                        buildConfigField("String",  "API_URL_STAGING",   "\"https://staging.yourdomain.com\"")
                        buildConfigField("String",  "API_URL_RELEASE",   "\"https://api.yourdomain.com\"")
                        buildConfigField("Boolean", "ALLOW_URL_OVERRIDE","false")
                    }
                    register("demo") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".demo")
                        bundleIdSuffix.set(".demo")
                        buildConfigField("String",  "CLIENT_ID",         "\"demo\"")
                        buildConfigField("String",  "API_URL_DEBUG",     "\"https://dev.yourdomain.com\"")
                        buildConfigField("String",  "API_URL_STAGING",   "\"https://staging.yourdomain.com\"")
                        buildConfigField("String",  "API_URL_RELEASE",   "\"https://demo.yourdomain.com\"")
                        buildConfigField("Boolean", "ALLOW_URL_OVERRIDE","true")   // demo can point at any server
                    }
                    register("clientA") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".clienta")
                        bundleIdSuffix.set(".clienta")
                        buildConfigField("String",  "CLIENT_ID",         "\"clientA\"")
                        buildConfigField("String",  "API_URL_DEBUG",     "\"https://dev.banka.com\"")
                        buildConfigField("String",  "API_URL_STAGING",   "\"https://staging.banka.com\"")
                        buildConfigField("String",  "API_URL_RELEASE",   "\"https://api.banka.com\"")
                        buildConfigField("Boolean", "ALLOW_URL_OVERRIDE","false")
                    }
                    register("clientB") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".clientb")
                        bundleIdSuffix.set(".clientb")
                        buildConfigField("String",  "CLIENT_ID",         "\"clientB\"")
                        buildConfigField("String",  "API_URL_DEBUG",     "\"https://dev.bankb.com\"")
                        buildConfigField("String",  "API_URL_STAGING",   "\"https://staging.bankb.com\"")
                        buildConfigField("String",  "API_URL_RELEASE",   "\"https://api.bankb.com\"")
                        buildConfigField("Boolean", "ALLOW_URL_OVERRIDE","false")
                    }

                    // --- tier dimension ---
                    register("advanced") {
                        dimension.set("tier")
                        isDefault.set(true)
                        buildConfigField("String",  "CLIENT_TIER",             "\"advanced\"")
                        buildConfigField("Boolean", "FEATURE_ANALYTICS",       "true")
                        buildConfigField("Boolean", "FEATURE_REPORTS",         "true")
                        buildConfigField("Boolean", "FEATURE_BULK_OPERATIONS", "true")
                    }
                    register("basic") {
                        dimension.set("tier")
                        buildConfigField("String",  "CLIENT_TIER",             "\"basic\"")
                        buildConfigField("Boolean", "FEATURE_ANALYTICS",       "false")
                        buildConfigField("Boolean", "FEATURE_REPORTS",         "false")
                        buildConfigField("Boolean", "FEATURE_BULK_OPERATIONS", "false")
                    }
                }

                // ── Build types = deployment environments ─────────────────────
                // Each binary has exactly one active server URL, determined at compile time.
                buildTypes {
                    register("debug") {
                        isDefault.set(true)
                        isDebuggable.set(true)
                        applicationIdSuffix.set(".debug")
                        buildConfigField("Boolean","ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","true")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "true")
                        buildConfigField("String", "LOG_TAG",           "\"KMPTemplate-DEBUG\"")
                    }
                    register("staging") {
                        isDebuggable.set(false)
                        applicationIdSuffix.set(".staging")
                        buildConfigField("Boolean","ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String", "LOG_TAG",           "\"KMPTemplate-STAGING\"")
                    }
                    register("release") {
                        isDebuggable.set(false)
                        isMinifyEnabled.set(true)
                        buildConfigField("Boolean","ENABLE_LOGGING",    "false")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String", "LOG_TAG",           "\"KMPTemplate\"")
                    }
                }

                // Bridge KMP config to Android Gradle Plugin (required for cmp-android)
                bridgeAgpBuildTypes.set(true)
                bridgeAgpProductFlavors.set(true)
            }
        }
    }
}
```

---

### Step 6 — Flavor enum: `build-logic/convention/src/main/kotlin/org/convention/KmpFlavors.kt`

Type-safe flavor catalog used by other convention plugins in `build-logic`:

```kotlin
package org.convention

object KmpFlavors {

    enum class Dimension(val priority: Int) {
        CONSUMER(0),
        TIER(1),
    }

    @Suppress("EnumEntryName")
    enum class ConsumerFlavor(
        val isDefault: Boolean = false,
        val applicationIdSuffix: String? = null,
        val bundleIdSuffix: String? = null,
    ) {
        internal(isDefault = true),
        demo(applicationIdSuffix = ".demo",   bundleIdSuffix = ".demo"),
        clientA(applicationIdSuffix = ".clienta", bundleIdSuffix = ".clienta"),
        clientB(applicationIdSuffix = ".clientb", bundleIdSuffix = ".clientb"),
    }

    @Suppress("EnumEntryName")
    enum class TierFlavor(val isDefault: Boolean = false) {
        advanced(isDefault = true),
        basic,
    }
}
```

---

### Step 7 — Apply convention plugin to shared module: `cmp-shared/build.gradle.kts`

Add the plugin alias to the plugins block of the shared KMP module:

```kotlin
plugins {
    // existing plugins ...
    alias(libs.plugins.kmp.flavors.convention)   // ADD THIS
}
```

> Apply to every module that needs `FlavorConfig` constants or flavor-specific source sets. Typically `cmp-shared` and any feature module that branches on flavor.

---

### Step 8 — AppVariant accessor: `cmp-shared/src/commonMain/kotlin/cmp/shared/flavor/AppVariant.kt`

Create a typed wrapper so call sites never import `FlavorConfig` directly:

```kotlin
package cmp.shared.flavor

import org.openmf.kmptemplate.FlavorConfig

object AppVariant {
    // Variant identity
    val variantName: String  get() = FlavorConfig.VARIANT_NAME
    val buildType: String    get() = FlavorConfig.BUILD_TYPE

    // Build type booleans (auto-generated by plugin)
    val isDebug: Boolean     get() = FlavorConfig.IS_DEBUG
    val isStaging: Boolean   get() = FlavorConfig.IS_STAGING
    val isRelease: Boolean   get() = FlavorConfig.IS_RELEASE

    // Consumer flavor booleans
    val isInternal: Boolean  get() = FlavorConfig.IS_INTERNAL
    val isDemo: Boolean      get() = FlavorConfig.IS_DEMO
    val isClientA: Boolean   get() = FlavorConfig.IS_CLIENT_A
    val isClientB: Boolean   get() = FlavorConfig.IS_CLIENT_B

    // Tier flavor booleans
    val isAdvanced: Boolean  get() = FlavorConfig.IS_ADVANCED
    val isBasic: Boolean     get() = FlavorConfig.IS_BASIC

    // Consumer custom fields
    val clientId: String          get() = FlavorConfig.CLIENT_ID
    val apiUrlDebug: String        get() = FlavorConfig.API_URL_DEBUG
    val apiUrlStaging: String      get() = FlavorConfig.API_URL_STAGING
    val apiUrlRelease: String      get() = FlavorConfig.API_URL_RELEASE
    val allowUrlOverride: Boolean  get() = FlavorConfig.ALLOW_URL_OVERRIDE

    // Tier custom fields
    val clientTier: String              get() = FlavorConfig.CLIENT_TIER
    val featureAnalytics: Boolean       get() = FlavorConfig.FEATURE_ANALYTICS
    val featureReports: Boolean         get() = FlavorConfig.FEATURE_REPORTS
    val featureBulkOperations: Boolean  get() = FlavorConfig.FEATURE_BULK_OPERATIONS

    // Build type custom fields
    val enableLogging: Boolean    get() = FlavorConfig.ENABLE_LOGGING
    val showDebugOverlay: Boolean get() = FlavorConfig.SHOW_DEBUG_OVERLAY
    val allowEnvSwitch: Boolean   get() = FlavorConfig.ALLOW_ENV_SWITCH
    val logTag: String            get() = FlavorConfig.LOG_TAG

    // Derived: active server URL for this binary (no runtime switch)
    val activeApiUrl: String get() = when (buildType) {
        "debug"   -> apiUrlDebug
        "staging" -> apiUrlStaging
        else      -> apiUrlRelease
    }
}
```

---

### Step 9 — expect/actual for compile-time feature exclusion

#### 9a. Declare expectations in `commonMain`

`cmp-shared/src/commonMain/kotlin/cmp/shared/flavor/ContentRepository.kt`:
```kotlin
package cmp.shared.flavor

expect object ContentRepository {
    val dataSource: String
    val requiresAuthentication: Boolean
    val allowsServerUrlOverride: Boolean
    fun getBaseUrl(): String
    fun getSampleData(): List<String>
}
```

`cmp-shared/src/commonMain/kotlin/cmp/shared/flavor/FeatureFlags.kt`:
```kotlin
package cmp.shared.flavor

expect object FeatureFlags {
    val tier: String
    val analytics: Boolean
    val reports: Boolean
    val bulkOperations: Boolean
}
```

#### 9b. Provide actuals per consumer flavor

Create one `actual` file per consumer source set. The plugin wires these source sets automatically — you only need to create the directories and files.

**`cmp-shared/src/commonInternal/kotlin/cmp/shared/flavor/ContentRepository.kt`**:
```kotlin
package cmp.shared.flavor
import org.openmf.kmptemplate.FlavorConfig

actual object ContentRepository {
    actual val dataSource: String = "remote-api"
    actual val requiresAuthentication: Boolean = true
    actual val allowsServerUrlOverride: Boolean = false
    actual fun getBaseUrl(): String = FlavorConfig.API_URL_RELEASE
    actual fun getSampleData(): List<String> = emptyList()
}
```

**`cmp-shared/src/commonDemo/kotlin/cmp/shared/flavor/ContentRepository.kt`**:
```kotlin
package cmp.shared.flavor
import org.openmf.kmptemplate.FlavorConfig

actual object ContentRepository {
    actual val dataSource: String = "remote-api-demo"
    actual val requiresAuthentication: Boolean = true
    actual val allowsServerUrlOverride: Boolean = true   // demo can redirect to any server
    actual fun getBaseUrl(): String = FlavorConfig.API_URL_RELEASE
    actual fun getSampleData(): List<String> = listOf("Demo Account 1", "Demo Account 2")
}
```

**`cmp-shared/src/commonClientA/kotlin/cmp/shared/flavor/ContentRepository.kt`** and
**`cmp-shared/src/commonClientB/kotlin/cmp/shared/flavor/ContentRepository.kt`**:
Follow the same pattern as `commonInternal`, substituting the relevant client URLs.

#### 9c. Provide actuals per tier flavor

**`cmp-shared/src/commonAdvanced/kotlin/cmp/shared/flavor/FeatureFlags.kt`**:
```kotlin
package cmp.shared.flavor

actual object FeatureFlags {
    actual val tier: String = "advanced"
    actual val analytics: Boolean = true
    actual val reports: Boolean = true
    actual val bulkOperations: Boolean = true
}
```

**`cmp-shared/src/commonBasic/kotlin/cmp/shared/flavor/FeatureFlags.kt`**:
```kotlin
package cmp.shared.flavor

actual object FeatureFlags {
    actual val tier: String = "basic"
    actual val analytics: Boolean = false
    actual val reports: Boolean = false
    actual val bulkOperations: Boolean = false
}
```

> The `basic` source set does not contain analytics/reports/bulk-ops *implementation classes* at all — they are excluded from the binary, not just gated behind a flag.

---

## GENERATED FlavorConfig API

After a successful Gradle sync, the plugin generates `org.openmf.kmptemplate.FlavorConfig` in `commonMain`. All fields are `const val`.

### Auto-generated identity fields (always present)

| Field | Type | Example value for `internalAdvancedDebug` |
|---|---|---|
| `VARIANT_NAME` | String | `"internalAdvancedDebug"` |
| `BUILD_TYPE` | String | `"debug"` |
| `IS_DEBUG` | Boolean | `true` |
| `IS_STAGING` | Boolean | `false` |
| `IS_RELEASE` | Boolean | `false` |
| `IS_INTERNAL` | Boolean | `true` |
| `IS_DEMO` | Boolean | `false` |
| `IS_CLIENT_A` | Boolean | `false` |
| `IS_CLIENT_B` | Boolean | `false` |
| `IS_ADVANCED` | Boolean | `true` |
| `IS_BASIC` | Boolean | `false` |

### Consumer-dimension custom fields

| Field | Type | internal | demo | clientA | clientB |
|---|---|---|---|---|---|
| `CLIENT_ID` | String | `"internal"` | `"demo"` | `"clientA"` | `"clientB"` |
| `API_URL_DEBUG` | String | dev URL | dev URL | clientA dev | clientB dev |
| `API_URL_STAGING` | String | staging URL | staging URL | clientA staging | clientB staging |
| `API_URL_RELEASE` | String | prod URL | demo URL | clientA prod | clientB prod |
| `ALLOW_URL_OVERRIDE` | Boolean | `false` | `true` | `false` | `false` |

### Tier-dimension custom fields

| Field | Type | advanced | basic |
|---|---|---|---|
| `CLIENT_TIER` | String | `"advanced"` | `"basic"` |
| `FEATURE_ANALYTICS` | Boolean | `true` | `false` |
| `FEATURE_REPORTS` | Boolean | `true` | `false` |
| `FEATURE_BULK_OPERATIONS` | Boolean | `true` | `false` |

### Build-type custom fields

| Field | Type | debug | staging | release |
|---|---|---|---|---|
| `ENABLE_LOGGING` | Boolean | `true` | `true` | `false` |
| `SHOW_DEBUG_OVERLAY` | Boolean | `true` | `false` | `false` |
| `ALLOW_ENV_SWITCH` | Boolean | `true` | `false` | `false` |
| `LOG_TAG` | String | `"KMPTemplate-DEBUG"` | `"KMPTemplate-STAGING"` | `"KMPTemplate"` |

---

## VARIANT MATRIX

4 consumers × 2 tiers × 3 build types = **24 total variants**

```
internalAdvancedDebug      internalAdvancedStaging      internalAdvancedRelease   ← your app
internalBasicDebug         internalBasicStaging         internalBasicRelease
demoAdvancedDebug          demoAdvancedStaging          demoAdvancedRelease       ← demo/prospects
demoBasicDebug             demoBasicStaging             demoBasicRelease
clientAAdvancedDebug       clientAAdvancedStaging       clientAAdvancedRelease    ← Bank A
clientABasicDebug          clientABasicStaging          clientABasicRelease
clientBAdvancedDebug       clientBAdvancedStaging       clientBAdvancedRelease    ← Bank B
clientBBasicDebug          clientBBasicStaging          clientBBasicRelease
```

**Store-distributed variants** (only release build type ships):
- `internalAdvancedRelease` → your App Store / Play Store listing
- `demoAdvancedRelease` → your store, separate listing (suffix `.demo`)
- `clientAAdvancedRelease`, `clientABasicRelease` → Client A's store account
- `clientBAdvancedRelease`, `clientBBasicRelease` → Client B's store account

---

## ACTIVE URL SELECTION

All 3 server URLs are compiled into every consumer flavor. The active URL is selected by build type at compile time — there is no runtime switching in distributed builds.

```
internalAdvanced + debug   → FlavorConfig.API_URL_DEBUG    = "https://dev.yourdomain.com"
internalAdvanced + staging → FlavorConfig.API_URL_STAGING  = "https://staging.yourdomain.com"
internalAdvanced + release → FlavorConfig.API_URL_RELEASE  = "https://api.yourdomain.com"
```

Use `AppVariant.activeApiUrl` (defined in Step 8) to always get the correct URL for the current binary.

---

## SOURCE SET WIRING

For variant `clientAAdvancedDebug`, the plugin wires these source sets in order:

```
commonMain
├── commonClientA/     ← Bank A URLs, ALLOW_URL_OVERRIDE=false (actual ContentRepository)
├── commonAdvanced/    ← analytics/reports/bulk-ops compiled in (actual FeatureFlags)
└── commonDebug/       ← DevSettingsScreen, ALLOW_ENV_SWITCH=true (if created)
```

Source sets that do not exist on disk are silently skipped — you only need to create directories for source sets that have actual implementations.

---

## CI/CD PIPELINE MAPPING

| Git branch / trigger | Gradle build type | Firebase channel | Store |
|---|---|---|---|
| `git push develop` | `*Debug` | App Distribution — dev | — |
| `git push staging` | `*Staging` | App Distribution — QA | — |
| `git tag` / manual | `*Release` | — | App Store / Play Store |

Build a specific variant locally:

```bash
# List all variants
./gradlew listFlavors

# Build by variant name
./gradlew -PkmpVariant=internalAdvancedDebug assemble

# Build by flavor + buildType
./gradlew -PkmpFlavor=internalAdvanced -PkmpBuildType=debug assemble
```

Set project-level defaults in `gradle.properties`:
```properties
kmpFlavor=internalAdvanced
kmpBuildType=debug
```

---

## ADDING A NEW CLIENT (e.g. clientC)

1. **`KMPFlavorsConventionPlugin.kt`** — add a new `register("clientC")` block in the `consumer` dimension with that client's URLs
2. **`KmpFlavors.kt`** — add `clientC` to `ConsumerFlavor` enum
3. **`cmp-shared/src/commonClientC/kotlin/cmp/shared/flavor/ContentRepository.kt`** — create `actual` implementation
4. Run `./gradlew sync` — the plugin auto-generates `IS_CLIENT_C`, wires `commonClientC/` source set, creates all 6 clientC variants

---

## VERIFICATION

After completing all steps, verify integration:

```bash
# 1. Check plugin resolves and config task runs
./gradlew :cmp-shared:help --task generateFlavorBuildConfig

# 2. List all registered variants
./gradlew listFlavors

# 3. Confirm FlavorConfig is generated
./gradlew :cmp-shared:kspCommonMainKotlinMetadata
# Generated file location (relative to cmp-shared):
# build/generated/kmp-flavors/commonMain/kotlin/org/openmf/kmptemplate/FlavorConfig.kt

# 4. Build one debug variant end-to-end
./gradlew :cmp-android:assembleInternalAdvancedDebug

# 5. Run shared module tests for all flavor combinations
./gradlew :cmp-shared:allTests
```

Expected output from `listFlavors`:
```
Registered variants (24):
  internalAdvancedDebug, internalAdvancedStaging, internalAdvancedRelease,
  internalBasicDebug, ...,
  clientBBasicRelease
```

---

## TROUBLESHOOTING

| Symptom | Cause | Fix |
|---|---|---|
| `Plugin with id 'io.github.mobilebytelabs.kmp-product-flavors' not found` | `includeBuild("../../build-logic")` missing from root `settings.gradle.kts` pluginManagement | Add it (Step 4) |
| `Unresolved reference: KmpFlavorExtension` in convention plugin | `compileOnly("io.github.mobilebytelabs.kmpflavors:flavor-plugin")` missing | Add to `build-logic/convention/build.gradle.kts` (Step 2) |
| `includeBuild` circular build error | `build-logic/settings.gradle.kts` missing `includeBuild("../../../build-logic")` | Add it (Step 3) |
| `FlavorConfig` not found in commonMain | Convention plugin not applied to the module | Add `alias(libs.plugins.kmp.flavors.convention)` to module's `build.gradle.kts` (Step 7) |
| AGP build type not generated | `bridgeAgpBuildTypes.set(true)` not set | Add to `KMPFlavorsConventionPlugin.kt` |
| `actual` not found compiler error | Source set directory doesn't exist or is not wired | Create `src/commonFoo/kotlin/` directory; plugin wires it automatically on next sync |

---

## FILE CHECKLIST

After completing integration, these files must exist:

```
build-logic/
├── settings.gradle.kts                          MODIFIED: includeBuild("../../../build-logic")
└── convention/
    ├── build.gradle.kts                         MODIFIED: compileOnly + plugin registration
    └── src/main/kotlin/
        ├── KMPFlavorsConventionPlugin.kt         NEW
        └── org/convention/
            └── KmpFlavors.kt                    NEW

gradle/
└── libs.versions.toml                           MODIFIED: kmpProductFlavors version + plugin aliases

settings.gradle.kts                              MODIFIED: includeBuild("../../build-logic")

cmp-shared/
├── build.gradle.kts                             MODIFIED: alias(libs.plugins.kmp.flavors.convention)
└── src/
    ├── commonMain/kotlin/cmp/shared/flavor/
    │   ├── AppVariant.kt                        NEW
    │   ├── ContentRepository.kt                 NEW (expect)
    │   └── FeatureFlags.kt                      NEW (expect)
    ├── commonInternal/kotlin/cmp/shared/flavor/
    │   └── ContentRepository.kt                 NEW (actual)
    ├── commonDemo/kotlin/cmp/shared/flavor/
    │   └── ContentRepository.kt                 NEW (actual)
    ├── commonClientA/kotlin/cmp/shared/flavor/
    │   └── ContentRepository.kt                 NEW (actual)
    ├── commonClientB/kotlin/cmp/shared/flavor/
    │   └── ContentRepository.kt                 NEW (actual)
    ├── commonAdvanced/kotlin/cmp/shared/flavor/
    │   └── FeatureFlags.kt                      NEW (actual)
    └── commonBasic/kotlin/cmp/shared/flavor/
        └── FeatureFlags.kt                      NEW (actual)
```

---

## CONFIGURATION EXTRAS

These DSL surfaces exist in `v1.0.5` but were missing from the capability index. They are documented here for completeness.

### Desktop / Web title suffixes

For Compose Multiplatform desktop and web builds, a per-flavor title suffix is appended at the window/page level:

```kotlin
kmpFlavors {
    flavors {
        register("demo") {
            dimension.set("tier")
            desktopTitleSuffix.set(" (Demo)")
            webTitleSuffix.set(" — Demo")
        }
    }
}
```

These properties are declared on `FlavorConfig` and surfaced by the `printFlavorProperties` Gradle task. Consumers wire the resolved suffix into `Window(title = ...)` (desktop) and `<title>` (web).

### Active variant override via Gradle property

Override the active flavor variant per invocation without editing build files:

```bash
./gradlew :app:assembleProdDebug -PkmpFlavor=prodAdvancedProdRelease
```

The property is read by `KmpFlavorPlugin` during `afterEvaluate` and routes which variant's `FlavorConfig` constants are baked into the build. Useful for CI matrices that build many variants from the same checkout. Default behaviour (no `-PkmpFlavor`): the variant formed by each dimension's `isDefault.set(true)` flavor.

---

## PITFALLS

### afterEvaluate ordering

The plugin defers all configuration work to `project.afterEvaluate { … }`. Consumers who reference plugin-created source sets directly inside their build script will hit ordering issues:

```kotlin
// ❌ Will fail — commonDemoMain doesn't exist yet at this point
kotlin {
    sourceSets {
        getByName("commonDemoMain").dependencies {
            implementation(libs.demo.fixtures)
        }
    }
}
```

**Workaround** — wrap the access in `afterEvaluate { }` or use the `kmpFlavors.flavors.named("demo") { dependencies { … } }` DSL instead:

```kotlin
// ✅ Correct — runs after the plugin has created the source set
afterEvaluate {
    kotlin.sourceSets.getByName("commonDemoMain").dependencies {
        implementation(libs.demo.fixtures)
    }
}
```

The `kmpFlavors { flavors { register("demo") { dependencies { … } } } }` DSL handles ordering correctly — prefer it when possible.

### Plugin application order

Apply plugins in this exact order in your module's `build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform")                            // 1. KMP first
    id("com.android.application")                       // 2. then Android plugin (or library)
    id("io.github.mobilebytelabs.kmp-product-flavors")  // 3. then this plugin
}
```

Reversing 1 and 3 makes the plugin's `KotlinMultiplatformExtension` lookup fail, producing only a `WARN` log line and no error. Confusing for first-time users.

---
