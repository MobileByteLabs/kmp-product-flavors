# Product Flavors Integration Guide

Use this guide when integrating `kmp-product-flavors` into a new or existing Kotlin Multiplatform project.

## What this plugin does

Generates a `FlavorConfig` Kotlin object at compile time with typed constants for every flavor/tier/buildType combination. Wires flavor-specific source sets automatically. Bridges to Android Gradle Plugin product flavors and build types.

## Step 1 — Add the plugin

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

Root `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.mobilebytelabs.kmp-product-flavors") version "0.1.0" apply false
}
```

## Step 2 — Create a convention plugin (recommended for multi-module projects)

`build-logic/convention/build.gradle.kts`:

```kotlin
dependencies {
    compileOnly("io.github.mobilebytelabs.kmpflavors:flavor-plugin")
}
```

`build-logic/convention/src/main/kotlin/YourFlavorConventionPlugin.kt`:

```kotlin
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class YourFlavorConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")

            extensions.configure<KmpFlavorExtension> {
                buildConfigPackage.set("com.yourapp.config")
                buildConfigClassName.set("FlavorConfig")

                flavorDimensions {
                    register("consumer") { priority.set(0) }
                    register("tier")     { priority.set(1) }
                }

                flavors {
                    // consumer dimension: who is this build for
                    register("internal") {
                        dimension.set("consumer")
                        isDefault.set(true)
                        buildConfigField("String", "CLIENT_ID",        "\"internal\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.yourdomain.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.yourdomain.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://api.yourdomain.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","false")
                    }
                    register("demo") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".demo")
                        bundleIdSuffix.set(".demo")
                        buildConfigField("String", "CLIENT_ID",        "\"demo\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.yourdomain.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.yourdomain.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://demo.yourdomain.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","true")
                    }
                    register("clientA") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".clienta")
                        bundleIdSuffix.set(".clienta")
                        buildConfigField("String", "CLIENT_ID",        "\"clientA\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.banka.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.banka.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://api.banka.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","false")
                    }

                    // tier dimension: what feature set
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

                buildTypes {
                    register("debug") {
                        isDefault.set(true)
                        isDebuggable.set(true)
                        applicationIdSuffix.set(".debug")
                        buildConfigField("Boolean","ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","true")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "true")
                        buildConfigField("String", "LOG_TAG",           "\"YourApp-DEBUG\"")
                    }
                    register("staging") {
                        isDebuggable.set(false)
                        applicationIdSuffix.set(".staging")
                        buildConfigField("Boolean","ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String", "LOG_TAG",           "\"YourApp-STAGING\"")
                    }
                    register("release") {
                        isDebuggable.set(false)
                        isMinifyEnabled.set(true)
                        buildConfigField("Boolean","ENABLE_LOGGING",    "false")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String", "LOG_TAG",           "\"YourApp\"")
                    }
                }

                bridgeAgpBuildTypes.set(true)
                bridgeAgpProductFlavors.set(true)
            }
        }
    }
}
```

Register in `build-logic/convention/src/main/resources/META-INF/gradle-plugins/`:

```
# your.flavor.convention.properties
implementation-class=YourFlavorConventionPlugin
```

## Step 3 — Apply to your KMP module

`your-shared-module/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kmp.library.convention)
    alias(libs.plugins.your.flavor.convention)  // your convention plugin
}
```

## Step 4 — Create a typed accessor in commonMain

`src/commonMain/kotlin/your/app/flavor/AppVariant.kt`:

```kotlin
package your.app.flavor

import com.yourapp.config.FlavorConfig

object AppVariant {
    val variantName: String  get() = FlavorConfig.VARIANT_NAME
    val buildType: String    get() = FlavorConfig.BUILD_TYPE
    val isDebug: Boolean     get() = FlavorConfig.IS_DEBUG
    val isStaging: Boolean   get() = FlavorConfig.IS_STAGING
    val isRelease: Boolean   get() = FlavorConfig.IS_RELEASE
    val isInternal: Boolean  get() = FlavorConfig.IS_INTERNAL
    val isDemo: Boolean      get() = FlavorConfig.IS_DEMO
    val isAdvanced: Boolean  get() = FlavorConfig.IS_ADVANCED
    val isBasic: Boolean     get() = FlavorConfig.IS_BASIC
    val clientId: String     get() = FlavorConfig.CLIENT_ID
    val clientTier: String   get() = FlavorConfig.CLIENT_TIER

    val apiUrlDebug: String    get() = FlavorConfig.API_URL_DEBUG
    val apiUrlStaging: String  get() = FlavorConfig.API_URL_STAGING
    val apiUrlRelease: String  get() = FlavorConfig.API_URL_RELEASE
    val allowUrlOverride: Boolean get() = FlavorConfig.ALLOW_URL_OVERRIDE

    val featureAnalytics: Boolean       get() = FlavorConfig.FEATURE_ANALYTICS
    val featureReports: Boolean         get() = FlavorConfig.FEATURE_REPORTS
    val featureBulkOperations: Boolean  get() = FlavorConfig.FEATURE_BULK_OPERATIONS

    val enableLogging: Boolean      get() = FlavorConfig.ENABLE_LOGGING
    val showDebugOverlay: Boolean   get() = FlavorConfig.SHOW_DEBUG_OVERLAY
    val allowEnvSwitch: Boolean     get() = FlavorConfig.ALLOW_ENV_SWITCH
    val logTag: String              get() = FlavorConfig.LOG_TAG

    val activeApiUrl: String
        get() = when (buildType) {
            "debug"   -> apiUrlDebug
            "staging" -> apiUrlStaging
            else      -> apiUrlRelease
        }
}
```

## Step 5 — Use expect/actual for compile-time feature exclusion

For features that differ by flavor, use `expect`/`actual` instead of `if` checks. This ensures the code is **absent from the binary**, not just skipped at runtime.

`commonMain`:

```kotlin
expect object FeatureFlags {
    val tier: String
    val analytics: Boolean
    val reports: Boolean
    val bulkOperations: Boolean
}
```

`commonAdvanced/` (actual):

```kotlin
actual object FeatureFlags {
    actual val tier: String = "advanced"
    actual val analytics: Boolean = true
    actual val reports: Boolean = true
    actual val bulkOperations: Boolean = true
}
```

`commonBasic/` (actual):

```kotlin
actual object FeatureFlags {
    actual val tier: String = "basic"
    actual val analytics: Boolean = false
    actual val reports: Boolean = false
    actual val bulkOperations: Boolean = false
}
```

The plugin automatically wires `commonAdvanced/` into the source set hierarchy for `*Advanced*` variants and `commonBasic/` for `*Basic*` variants.

## Step 6 — URL override for demo consumer

The `demo` consumer should compile in a server URL settings screen. Place it in `commonDemo/` so it only exists in demo binaries:

```
src/
├── commonMain/kotlin/your/app/settings/
│   └── SettingsScreen.kt         ← shows general settings only
└── commonDemo/kotlin/your/app/settings/
    └── ServerUrlScreen.kt        ← exists only in demo builds
```

In your Koin/DI module, conditionally bind the URL provider:

```kotlin
// commonMain
expect fun serverUrlModule(): Module

// commonDemo actual
actual fun serverUrlModule() = module {
    single<ServerUrlProvider> { UserOverridableUrlProvider(get()) }
}

// commonInternal / commonClientA / commonClientB actual
actual fun serverUrlModule() = module {
    single<ServerUrlProvider> { CompiledUrlProvider(FlavorConfig.API_URL_RELEASE) }
}
```

## Step 7 — Dev settings screen for debug builds

The `debug` build type has `ALLOW_ENV_SWITCH = true`. Wire a developer settings screen in `commonDebug/` that lets QA testers switch between dev/staging/prod URLs at runtime:

```
src/
└── commonDebug/kotlin/your/app/devsettings/
    └── DevSettingsScreen.kt   ← env switcher, not present in staging or release
```

```kotlin
// DevSettingsScreen.kt
@Composable
fun DevSettingsScreen() {
    val urls = listOf(
        "Dev"     to FlavorConfig.API_URL_DEBUG,
        "Staging" to FlavorConfig.API_URL_STAGING,
        "Prod"    to FlavorConfig.API_URL_RELEASE,
    )
    // render URL selector, persist selected URL to DataStore
}
```

## Building & selecting variants

```bash
# default (internalAdvancedDebug)
./gradlew assemble

# specific variant
./gradlew -PkmpVariant=clientABasicRelease assemble

# split properties
./gradlew -PkmpFlavor=clientABasic -PkmpBuildType=release assemble

# list all variants
./gradlew listFlavors

# validate configuration
./gradlew validateFlavors
```

Or set defaults in `gradle.properties`:

```properties
kmpFlavor=internalAdvanced
kmpBuildType=debug
```

## Generated FlavorConfig

After running `./gradlew generateFlavorBuildConfig`, the generated file is at:

```
build/generated/kmpFlavors/commonMain/kotlin/com/yourapp/config/FlavorConfig.kt
```

```kotlin
object FlavorConfig {
    const val VARIANT_NAME = "internalAdvancedDebug"
    const val BUILD_TYPE   = "debug"
    const val IS_DEBUG     = true
    const val IS_STAGING   = false
    const val IS_RELEASE   = false
    const val IS_INTERNAL  = true
    const val IS_DEMO      = false
    const val IS_ADVANCED  = true
    const val IS_BASIC     = false
    const val CLIENT_ID    = "internal"
    const val CLIENT_TIER  = "advanced"
    const val API_URL_DEBUG    = "https://dev.yourdomain.com"
    const val API_URL_STAGING  = "https://staging.yourdomain.com"
    const val API_URL_RELEASE  = "https://api.yourdomain.com"
    const val ALLOW_URL_OVERRIDE      = false
    const val FEATURE_ANALYTICS       = true
    const val FEATURE_REPORTS         = true
    const val FEATURE_BULK_OPERATIONS = true
    const val ENABLE_LOGGING     = true
    const val SHOW_DEBUG_OVERLAY = true
    const val ALLOW_ENV_SWITCH   = true
    const val LOG_TAG            = "YourApp-DEBUG"
}
```

## Adding a new client

1. Add to `KMPFlavorsConventionPlugin.kt`:

```kotlin
register("clientC") {
    dimension.set("consumer")
    applicationIdSuffix.set(".clientc")
    bundleIdSuffix.set(".clientc")
    buildConfigField("String", "CLIENT_ID",        "\"clientC\"")
    buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.bankc.com\"")
    buildConfigField("String", "API_URL_STAGING",  "\"https://staging.bankc.com\"")
    buildConfigField("String", "API_URL_RELEASE",  "\"https://api.bankc.com\"")
    buildConfigField("Boolean","ALLOW_URL_OVERRIDE","false")
}
```

2. Create its actual source set:

```kotlin
// src/commonClientC/kotlin/your/app/flavor/ContentRepository.kt
actual object ContentRepository {
    actual val dataSource: String = "remote-api"
    actual val requiresAuthentication: Boolean = true
    actual val allowsServerUrlOverride: Boolean = false
    actual fun getBaseUrl(): String = FlavorConfig.API_URL_RELEASE
    actual fun getSampleData(): List<String> = emptyList()
}
```

3. Sync. The plugin registers `commonClientC/`, generates `IS_CLIENT_C = true/false`, and adds it to the Android product flavor list automatically.

## Checklist before publishing a client release

- [ ] Replace placeholder URLs (`dev.banka.com`, `api.banka.com`) with real client endpoints
- [ ] Override `applicationId` in app module with client's package name
- [ ] Add client's `google-services.json` for the release variant
- [ ] Configure iOS provisioning profile for client's bundle ID
- [ ] Set client's signing keystore in CI secrets
- [ ] Verify `ALLOW_URL_OVERRIDE = false` in release variant
- [ ] Run `./gradlew validateFlavors` before submitting to store
