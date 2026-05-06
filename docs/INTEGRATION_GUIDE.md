# Integration Guide

How to integrate `kmp-product-flavors` into a KMP project using a convention plugin.

## 1. Add the plugin to your version catalog

`gradle/libs.versions.toml`:
```toml
[versions]
kmpProductFlavors = "0.1.0"

[plugins]
kmp-product-flavors = { id = "io.github.mobilebytelabs.kmp-product-flavors", version.ref = "kmpProductFlavors" }
kmp-flavors-convention = { id = "org.convention.kmp.flavors", version = "unspecified" }
```

## 2. Wire the plugin in your build-logic

`build-logic/convention/build.gradle.kts` — add dependency and register plugin:

```kotlin
dependencies {
    compileOnly("io.github.mobilebytelabs.kmpflavors:flavor-plugin")
}

gradlePlugin {
    plugins {
        register("kmpFlavors") {
            id = "org.convention.kmp.flavors"
            implementationClass = "KMPFlavorsConventionPlugin"
        }
    }
}
```

`build-logic/settings.gradle.kts` — make the plugin JAR available during convention plugin compilation:

```kotlin
// If using the plugin from local source (composite build):
includeBuild("../../build-logic")   // path to kmp-product-flavors/build-logic

// If using from Maven:
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
```

`settings.gradle.kts` (root of consumer project) — make plugin ID resolvable:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    includeBuild("../../build-logic")   // path to kmp-product-flavors/build-logic (local source)
    // OR: add mavenCentral() and use the published plugin ID
    repositories { mavenCentral(); google(); gradlePluginPortal() }
}
```

## 3. Create KMPFlavorsConventionPlugin.kt

`build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt`:

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
                buildConfigPackage.set("com.yourapp.config")
                buildConfigClassName.set("FlavorConfig")

                flavorDimensions {
                    register("consumer") { priority.set(0) }
                    register("tier")     { priority.set(1) }
                }

                flavors {
                    register("internal") {
                        dimension.set("consumer")
                        isDefault.set(true)
                        buildConfigField("String", "CLIENT_ID",        "\"internal\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.yourapp.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.yourapp.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://api.yourapp.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","false")
                    }
                    register("demo") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".demo")
                        bundleIdSuffix.set(".demo")
                        buildConfigField("String", "CLIENT_ID",        "\"demo\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.yourapp.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.yourapp.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://demo.yourapp.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","true")
                    }
                    register("clientA") {
                        dimension.set("consumer")
                        applicationIdSuffix.set(".clienta")
                        bundleIdSuffix.set(".clienta")
                        buildConfigField("String", "CLIENT_ID",        "\"clientA\"")
                        buildConfigField("String", "API_URL_DEBUG",    "\"https://dev.clienta.com\"")
                        buildConfigField("String", "API_URL_STAGING",  "\"https://staging.clienta.com\"")
                        buildConfigField("String", "API_URL_RELEASE",  "\"https://api.clienta.com\"")
                        buildConfigField("Boolean","ALLOW_URL_OVERRIDE","false")
                    }
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
                        buildConfigField("String", "LOG_TAG",           "\"App-DEBUG\"")
                    }
                    register("staging") {
                        isDebuggable.set(false)
                        applicationIdSuffix.set(".staging")
                        buildConfigField("Boolean","ENABLE_LOGGING",    "true")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String", "LOG_TAG",           "\"App-STAGING\"")
                    }
                    register("release") {
                        isDebuggable.set(false)
                        isMinifyEnabled.set(true)
                        buildConfigField("Boolean","ENABLE_LOGGING",    "false")
                        buildConfigField("Boolean","SHOW_DEBUG_OVERLAY","false")
                        buildConfigField("Boolean","ALLOW_ENV_SWITCH",  "false")
                        buildConfigField("String", "LOG_TAG",           "\"App\"")
                    }
                }

                bridgeAgpBuildTypes.set(true)
                bridgeAgpProductFlavors.set(true)
            }
        }
    }
}
```

## 4. Add AppVariant accessor in commonMain

```kotlin
// src/commonMain/kotlin/your/app/flavor/AppVariant.kt
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
    val apiUrlDebug: String   get() = FlavorConfig.API_URL_DEBUG
    val apiUrlStaging: String get() = FlavorConfig.API_URL_STAGING
    val apiUrlRelease: String get() = FlavorConfig.API_URL_RELEASE
    val allowUrlOverride: Boolean get() = FlavorConfig.ALLOW_URL_OVERRIDE
    val featureAnalytics: Boolean      get() = FlavorConfig.FEATURE_ANALYTICS
    val featureReports: Boolean        get() = FlavorConfig.FEATURE_REPORTS
    val featureBulkOperations: Boolean get() = FlavorConfig.FEATURE_BULK_OPERATIONS
    val enableLogging: Boolean    get() = FlavorConfig.ENABLE_LOGGING
    val showDebugOverlay: Boolean get() = FlavorConfig.SHOW_DEBUG_OVERLAY
    val allowEnvSwitch: Boolean   get() = FlavorConfig.ALLOW_ENV_SWITCH
    val logTag: String            get() = FlavorConfig.LOG_TAG

    val activeApiUrl: String get() = when (buildType) {
        "debug"   -> apiUrlDebug
        "staging" -> apiUrlStaging
        else      -> apiUrlRelease
    }
}
```

## 5. Use expect/actual for compile-time feature exclusion

```kotlin
// commonMain
expect object FeatureFlags { val analytics: Boolean; val reports: Boolean }

// commonAdvanced — actual: true
// commonBasic    — actual: false
```

See the `samples/kmp-project-template` sample for a complete working example.
