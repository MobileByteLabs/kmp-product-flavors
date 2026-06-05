# KMP Product Flavors — Consumer Guide

> Narrative guide for KMP project teams adopting `io.github.mobilebytelabs.kmp-product-flavors`. Covers motivation, patterns, and anti-patterns. Pair with [QUICKSTART.md](QUICKSTART.md) for step-by-step setup, and [REFERENCE.md](REFERENCE.md) for the full DSL API.

---

## Why a flavor plugin in KMP?

### The problem this plugin solves

Kotlin Multiplatform projects compile across Android, iOS, Desktop, and Web simultaneously. The standard Gradle toolchain gives Android excellent product-flavor support via AGP. But KMP non-Android targets have no equivalent concept — there is no `sourceSets { named("freeRelease") }` for Desktop or iOS out of the box.

Before this plugin, teams working around the gap fell into one of three traps:

**Trap 1 — AGP-only flavors.** Declare product flavors in `cmp-android/build.gradle.kts` and drive iOS/Desktop variants via separate Gradle properties or environment variables. Result: the Android build understands `freeDebug`, but the iOS build has to parse `VARIANT=free` from the environment and manually set compile flags. The variant matrix lives in three places and drifts.

**Trap 2 — Per-module hacks.** Each KMP module re-implements `expect/actual` for its own variant surface. Result: `IS_DEMO` is a `BuildConfig.BOOLEAN` in the Android module, an environment-variable read in `iosMain`, and a Gradle property in `desktopMain`. None of them agree, and every module that needs flavor awareness is bespoke.

**Trap 3 — Dimension dummies.** Teams add a dummy AGP `flavorDimension` just to satisfy AGP's dimension-matching requirement, while keeping KMP flavors entirely separate. Result: the AGP layer and the KMP layer are two independent flavor systems that must be kept in sync manually, and CI permutations double.

### What this plugin does instead

`kmp-product-flavors` gives you a single DSL surface in your root `build.gradle.kts` (or a dedicated convention plugin) that:

1. Declares flavor dimensions and individual flavors in Gradle's configuration phase.
2. Generates per-variant `BuildKonfig` objects (Kotlin source files) that land in the right source sets for each platform — Android, iOS, Desktop, Web all get the same constants without environment-variable parsing.
3. Optionally bridges into AGP's product flavor model (`bridgeAgpProductFlavors`) so the Android layer stays in sync automatically.
4. Optionally bridges into AGP's build type model (`bridgeAgpBuildTypes`) to eliminate the duplicate `buildTypes {}` block in `cmp-android/build.gradle.kts`.
5. Exposes a `LocalFlavorsLoader` escape hatch for developer-machine overrides without touching committed files.

The result: **one flavor declaration, consistent behavior across all KMP targets**.

### What this plugin does NOT try to do

- Replace AGP's code-shrinking, signing, or APK-splitting capabilities. Those remain in `cmp-android/build.gradle.kts`.
- Force a particular module structure. The plugin is module-agnostic.
- Generate UI for flavor selection. That is an app-level concern.
- Manage secrets. `BuildKonfig` fields carry values; the values themselves should come from Gradle properties, environment variables, or a secrets vault (see [SECRETS_INTEGRATION.md](SECRETS_INTEGRATION.md)).

### When NOT to adopt this plugin

- Your project targets Android only. Use AGP product flavors directly.
- You have zero flavor variation (one build variant). The overhead of the plugin is not justified.
- You are building a pure library (no application modules). KMP libraries rarely need flavor-specific constants in the published artifact.

---

## Installation

### Requirements floor

| Dependency | Minimum version |
|---|---|
| Kotlin Gradle Plugin | 2.0.21+ |
| AGP | 8.2+ (optional — only needed for AGP bridge features) |
| Gradle | 8.0+ |
| JDK | 17+ |
| Compose Multiplatform | 1.7.0+ (if used) |

### Pattern A — Direct plugin apply (simplest)

Apply the plugin in the module that declares KMP targets:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors") version "2.8.1"
}

kmpFlavors {
    buildConfigPackage.set("com.example.app")
    flavorDimensions { register("tier") { priority.set(0) } }
    flavors {
        register("free") { dimension.set("tier"); isDefault.set(true) }
        register("paid") { dimension.set("tier") }
    }
    buildTypes {
        register("debug") { isDefault.set(true) }
        register("release") { isMinifyEnabled.set(true) }
    }
}
```

Or with a version catalog:

```toml
# gradle/libs.versions.toml
[versions]
kmpProductFlavors = "2.8.1"

[plugins]
kmp-product-flavors = { id = "io.github.mobilebytelabs.kmp-product-flavors", version.ref = "kmpProductFlavors" }
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kmp.product.flavors)
}
```

**When to use Pattern A:** Small projects where all flavor logic fits in one build file; quick prototypes; projects where all KMP targets are declared in a single module.

### Pattern B — Convention plugin (recommended for multi-module projects)

For projects with multiple Gradle modules that all need flavor awareness, centralize the flavor DSL in a convention plugin:

```kotlin
// build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt
class KMPFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(KmpFlavorPlugin::class.java)
            extensions.configure<KmpFlavorExtension> {
                buildConfigPackage.set(libs.findVersion("appId").get().requiredVersion)
                enableBuildTypes.set(true)
                flavorDimensions {
                    register("contentType") { priority.set(0) }
                }
                flavors {
                    register("demo") {
                        dimension.set("contentType")
                        isDefault.set(true)
                        applicationIdSuffix.set(".demo")
                        bundleIdSuffix.set(".demo")
                        buildConfigField("Boolean", "IS_DEMO_BUILD", "true")
                        buildConfigField("String", "BASE_URL", "\"https://demo.example.org\"")
                    }
                    register("prod") {
                        dimension.set("contentType")
                        buildConfigField("Boolean", "IS_DEMO_BUILD", "false")
                        buildConfigField("String", "BASE_URL", "\"https://api.example.org\"")
                    }
                }
                buildTypes {
                    register("debug")  { isDefault.set(true) }
                    register("staging") { applicationIdSuffix.set(".staging") }
                    register("release") { isMinifyEnabled.set(true) }
                }
                LocalFlavorsLoader.applyIfPresent(this, target)
            }
        }
    }
}
```

The convention plugin is applied in each module's `build.gradle.kts` with a single line:

```kotlin
plugins {
    id("org.convention.kmpflavors")   // or whatever your build-logic plugin ID is
}
```

**Reference implementation:** The `samples/kmp-project-template/` submodule in this repo is the canonical adoption of Pattern B. See [`KMP_PROJECT_TEMPLATE_INTEGRATION.md`](KMP_PROJECT_TEMPLATE_INTEGRATION.md) for the pointer to the full adoption record.

**When to use Pattern B:** Multi-module projects; projects that fork a template; projects where you want isolation between "the plugin's DSL surface" and "each module's build files".

### Maven Central availability

The plugin publishes to Maven Central. If you bump on the same day as a release, add `mavenLocal()` first in `pluginManagement.repositories` (in `settings.gradle.kts`) while Maven Central propagation completes (typically 1–4 hours):

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()   // resolves same-day releases before Maven Central propagates
        google { /* ... */ }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then run `./gradlew :build-logic:flavor-plugin:publishToMavenLocal --no-configuration-cache` from the plugin repo to publish locally.

---

## Your First Flavor

### A minimal two-flavor project

Start with one dimension, two flavors, two build types. This covers 90% of real use cases:

```kotlin
kmpFlavors {
    buildConfigPackage.set("com.example.myapp")
    flavorDimensions { register("tier") { priority.set(0) } }
    flavors {
        register("free") {
            dimension.set("tier")
            isDefault.set(true)
            buildConfigField("Boolean", "IS_PREMIUM", "false")
            buildConfigField("String", "FEATURE_FLAGS", "\"basic\"")
        }
        register("paid") {
            dimension.set("tier")
            buildConfigField("Boolean", "IS_PREMIUM", "true")
            buildConfigField("String", "FEATURE_FLAGS", "\"all\"")
        }
    }
    buildTypes {
        register("debug")   { isDefault.set(true) }
        register("release") { isMinifyEnabled.set(true) }
    }
}
```

This produces 4 variants: `freeDebug`, `freeRelease`, `paidDebug`, `paidRelease`.

### What `BuildKonfig` looks like

After the first build, the plugin generates:

```kotlin
// build/generated/source/buildkonfig/commonMain/com/example/myapp/BuildKonfig.kt
// (for the active variant — freeDebug by default)
internal object BuildKonfig {
    const val IS_PREMIUM: Boolean = false
    const val FEATURE_FLAGS: String = "basic"
    const val IS_DEBUG: Boolean = true
    const val IS_FREE: Boolean = true
    const val IS_PAID: Boolean = false
    const val FLAVOR: String = "free"
    const val BUILD_TYPE: String = "debug"
}
```

Consume it from `commonMain`:

```kotlin
import com.example.myapp.BuildKonfig

fun provideApiClient(): ApiClient {
    return ApiClient(
        baseUrl = if (BuildKonfig.IS_PREMIUM) PREMIUM_API else FREE_API,
        debug = BuildKonfig.IS_DEBUG
    )
}
```

### The active variant and source sets

The plugin wires each flavor into a matching source set:
- `freeMain/` — sources compiled only for the `free` flavor
- `paidMain/` — sources compiled only for the `paid` flavor
- `debugMain/` — sources compiled only for the `debug` build type
- `freeDebugMain/` — sources compiled only for the `free` + `debug` combination

Only the **active variant** (default: `freeDebug`, or whichever has `isDefault.set(true)`) compiles via the normal `compileKotlin*` tasks. Other variants compile via dedicated variant tasks. Run `./gradlew listFlavors` to see the full matrix and `./gradlew listActiveVariant` to see which is active.

### Switching the active variant

```bash
# Via Gradle property at build time:
./gradlew compileKotlinAndroid -PactiveVariant=paidRelease

# Via local.properties (for IDE hot-reload):
echo "activeVariant=paidRelease" >> local.properties
```

Or use `LocalFlavorsLoader.applyIfPresent(...)` in your convention plugin to let developers maintain a `LocalFlavors.kt` file that overrides flavors for their machine without affecting committed files.

### Matrix mode

When a module declares more than one flavor dimension, the plugin generates the full Cartesian product. See [MULTI_DIM_GUIDE.md](MULTI_DIM_GUIDE.md) for multi-dimension setup and [MATRIX_MODE.md](MATRIX_MODE.md) for compilation-task naming.

---

## BuildConfig Patterns

### Typed fields

The most common fields are Boolean and String. The plugin also supports Int, Long, and Float:

```kotlin
buildConfigField("Boolean", "IS_INTERNAL_BUILD", "true")
buildConfigField("String",  "API_HOST",          "\"api.internal.example.com\"")
buildConfigField("Int",     "MAX_RETRY_COUNT",   "5")
buildConfigField("Long",    "SESSION_TIMEOUT_MS","300_000L")
```

### Reading secrets safely

Never hard-code sensitive values in the flavor DSL. Use Gradle properties resolved at build time:

```kotlin
// build.gradle.kts or convention plugin
val apiKey: String = providers.gradleProperty("MY_API_KEY")
    .orElse(providers.environmentVariable("MY_API_KEY"))
    .getOrElse("")

buildConfigField("String", "MY_API_KEY", "\"$apiKey\"")
```

Then inject via CI environment variables or a local `gradle.properties` (gitignored). For a vault-backed approach, see [SECRETS_INTEGRATION.md](SECRETS_INTEGRATION.md).

### Per-target fields

Some fields only make sense on a specific platform. Use the `perTarget` variant:

```kotlin
kmpFlavors {
    flavors {
        register("internal") {
            buildConfigField("String", "ANALYTICS_ENDPOINT", "\"https://events.internal.example.com\"")
            // Android-only field:
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"internal-project\"", target = "android")
        }
    }
}
```

### Enum dimensions

For more than two values on a dimension, register flavors normally — there is no "enum dimension" concept, but the pattern is the same:

```kotlin
flavorDimensions { register("region") { priority.set(1) } }
flavors {
    register("us")   { dimension.set("region"); buildConfigField("String", "CDN_HOST", "\"cdn-us.example.com\"") }
    register("eu")   { dimension.set("region"); buildConfigField("String", "CDN_HOST", "\"cdn-eu.example.com\"") }
    register("apac") { dimension.set("region"); buildConfigField("String", "CDN_HOST", "\"cdn-apac.example.com\"") }
}
```

### Narrow surfaces over giant config objects

Anti-pattern: dumping everything into `BuildKonfig`:

```kotlin
// BAD — 40-field BuildKonfig object, every consumer imports the whole thing
buildConfigField("String", "AUTH_BASE_URL", ...)
buildConfigField("String", "ANALYTICS_BASE_URL", ...)
buildConfigField("String", "CDN_BASE_URL", ...)
buildConfigField("String", "CRASH_REPORTING_DSN", ...)
// ... 36 more
```

Prefer: each module that needs configuration declares only the fields relevant to it, in its own convention plugin or `build.gradle.kts`:

```kotlin
// core/network/build.gradle.kts
kmpFlavors {
    buildConfigPackage.set("com.example.core.network")
    flavors {
        register("demo") { buildConfigField("String", "AUTH_BASE_URL", "\"https://demo-auth.example.com\"") }
        register("prod") { buildConfigField("String", "AUTH_BASE_URL", "\"https://auth.example.com\"") }
    }
}
```

The plugin supports per-module `buildConfigPackage` so each module gets its own isolated `BuildKonfig` object. This follows the same principle as module-scoped Dagger/Koin modules: keep the surface local, prevent implicit coupling.

---

## Signing & Versioning

### Per-flavor signing (v2.8+ DSL)

v2.8 introduces `signingConfigs {}` DSL to co-locate signing intent with flavor intent:

```kotlin
kmpFlavors {
    flavors {
        register("internal") {
            signingConfigs {
                debug {
                    storeFile.set(file("keystores/internal.keystore"))
                    storePassword.set(providers.environmentVariable("INTERNAL_KEYSTORE_PASS").getOrElse(""))
                    keyAlias.set("internal")
                    keyPassword.set(providers.environmentVariable("INTERNAL_KEY_PASS").getOrElse(""))
                }
            }
        }
        register("release") {
            signingConfigs {
                release {
                    storeFile.set(file("keystores/release.keystore"))
                    storePassword.set(providers.environmentVariable("RELEASE_KEYSTORE_PASS").getOrElse(""))
                    keyAlias.set("upload")
                    keyPassword.set(providers.environmentVariable("RELEASE_KEY_PASS").getOrElse(""))
                }
            }
        }
    }
}
```

**Never hard-code passwords.** Use `providers.environmentVariable(...)` or Gradle properties loaded from a gitignored `local.properties`.

### Per-flavor version codes (v2.8+ DSL)

Useful when different store tracks require non-overlapping version codes:

```kotlin
register("internal") {
    versionCode.set(providers.environmentVariable("INTERNAL_VERSION_CODE").map { it.toInt() }.orElse(1))
    versionName.set("internal-${project.version}")
}
register("prod") {
    versionCode.set(providers.environmentVariable("PROD_VERSION_CODE").map { it.toInt() }.orElse(1))
    versionName.set("${project.version}")
}
```

### Bridging via Fastlane (existing infrastructure path)

If your project already has Fastlane lanes with signing configuration, you do NOT need to declare `signingConfigs {}` in the plugin DSL. The Fastlane-managed signing path is a valid alternative:

- Fastlane reads keystore/certificate info from environment secrets (set via CI).
- AGP picks up signing from `android.signingConfigs` in `cmp-android/build.gradle.kts` (populated by Fastlane or a `local.properties` helper).
- The plugin's `bridgeAgpBuildTypes` propagates build type naming; signing remains managed by Fastlane.

See [`KMP_PROJECT_TEMPLATE_INTEGRATION.md`](KMP_PROJECT_TEMPLATE_INTEGRATION.md) for the kmp-project-template reference, which uses this path.

### iOS signing

iOS signing is not managed by this plugin — it remains in `Fastfile` / `match` / Xcode project settings. The plugin generates the `BuildKonfig` object that iOS native code can read at runtime to select the correct endpoint/feature flags; signing is out of scope.

---

## Anti-patterns

### A. Reading BuildKonfig from commonMain in matrix mode

In matrix mode (multi-dimension or `assembleAllVariants`), each variant generates its own `BuildKonfig.kt` in a variant-specific source set. If you import `BuildKonfig` from `commonMain`, you are reading the **active-variant** constant at IDE time, which may not match the variant being compiled in CI.

**Symptom:** CI compiles `paidRelease`, your code reads `BuildKonfig.IS_DEMO_BUILD = false` because that's the active variant on your machine, but the CI artifact has `IS_DEMO_BUILD = false` for the wrong reason.

**Fix:** Only consume `BuildKonfig` from within the variant's source set context, or use the generated class via Kotlin's `expect/actual` pattern. See [MATRIX_MODE.md](MATRIX_MODE.md) and `samples/buildkonfig-rich/` for examples.

### B. Mirroring AGP's productFlavors block manually

If you enable `bridgeAgpProductFlavors.set(true)`, the plugin propagates dimensions and flavors to AGP. Do NOT also declare them in `android { productFlavors { ... } }` inside `cmp-android/build.gradle.kts` — you will get duplicate dimension declarations and AGP will fail the build.

**Fix:** Enable the bridge and remove the manual `android.productFlavors` block. Trust the plugin to propagate.

### C. Putting secret values in BuildConfig fields directly

The flavor DSL accepts arbitrary string values in `buildConfigField(...)`. If you put a secret value directly in the DSL, that value lands in the compiled Kotlin source and is committed to git.

**Wrong:**
```kotlin
buildConfigField("String", "API_SECRET", "\"my-secret-key-abc123\"")   // committed to git!
```

**Right:**
```kotlin
val secret = providers.gradleProperty("API_SECRET")
    .orElse(providers.environmentVariable("API_SECRET"))
    .getOrElse("")
buildConfigField("String", "API_SECRET", "\"$secret\"")   // resolved at build time from env
```

See [SECRETS_INTEGRATION.md](SECRETS_INTEGRATION.md) for vault-backed approaches.

### D. Conflating dimension name with flavor name

A common confusion: the dimension is the axis of variation; the flavor is a point on that axis.

**Wrong:**
```kotlin
flavorDimensions { register("free") { } }   // "free" is a flavor, not a dimension name
flavors {
    register("free") { dimension.set("free") }   // confusing — same name for both
    register("paid") { dimension.set("free") }   // reads as "paid is in the free dimension"?
}
```

**Right:**
```kotlin
flavorDimensions { register("tier") { } }   // dimension name describes the axis
flavors {
    register("free") { dimension.set("tier") }   // "free" is a point on the "tier" axis
    register("paid") { dimension.set("tier") }   // "paid" is another point on the "tier" axis
}
```

### E. Duplicating adoption docs in two places

When you adopt this plugin in your project, maintain your adoption record in your project's repository (not in this plugin repo). The plugin repo owns the abstract spec (`consumer.md`, `QUICKSTART.md`, `REFERENCE.md`); your project owns the concrete realization.

If you are a kmp-project-template fork: your adoption record is `docs/ADOPTION_KMP_PRODUCT_FLAVORS.md` in your fork. Update it when you bump the plugin version. Do NOT open a PR to this plugin repo with your per-project adoption notes — that is noise on the plugin's issue tracker.

If you are a non-template consumer: author a similar `docs/ADOPTION_KMP_PRODUCT_FLAVORS.md` in your project, following the structure in the kmp-project-template reference implementation.

### F. Editing the convention plugin directly for fork-local overrides

If you fork `kmp-project-template` and need a local override (e.g., a developer-machine API endpoint different from the registered `demo` flavor), do NOT edit `KMPFlavorsConventionPlugin.kt`. That file is shared and will conflict on upstream pulls.

**Right:** Use the `LocalFlavorsLoader.applyIfPresent(this, target)` hook already in the convention plugin. Create a `LocalFlavors.kt` file (gitignored) that overrides specific fields:

```kotlin
// LocalFlavors.kt (gitignored)
fun KmpFlavorExtension.applyLocalFlavors() {
    flavors.named("demo").configure {
        buildConfigField("String", "BASE_URL", "\"http://localhost:8080\"")
    }
}
```

`LocalFlavorsLoader` will find and apply this file automatically on build.

---

## Related

- [QUICKSTART.md](QUICKSTART.md) — step-by-step setup (5 minutes to first variant compilation)
- [REFERENCE.md](REFERENCE.md) — full DSL API reference
- [KMP_PROJECT_TEMPLATE_INTEGRATION.md](KMP_PROJECT_TEMPLATE_INTEGRATION.md) — pointer to the convention-plugin reference adoption
- [SECRETS_INTEGRATION.md](SECRETS_INTEGRATION.md) — how to use secrets with BuildConfig fields
- [MULTI_DIM_GUIDE.md](MULTI_DIM_GUIDE.md) — multi-dimension flavor matrices
- [MATRIX_MODE.md](MATRIX_MODE.md) — understanding the full variant matrix + compilation tasks
- [samples/buildkonfig-rich/](../samples/) — minimal BuildKonfig multi-field showcase
- [samples/multi-dim-3d/](../samples/) — 3-dimension matrix example
- [samples/conditional-targets/](../samples/) — flavor-conditional platform targets
