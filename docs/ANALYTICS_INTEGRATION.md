# Analytics Integration Guide

> **Since v2.6** — cross-platform variant-aware analytics tags via
> `kmpFlavors { analytics { customTag(name) { variant -> ... } } }`. Plugin is
> SDK-agnostic; consumer wires the result into Firebase Crashlytics / Sentry /
> Firebase Analytics / any other observability target.

The plugin emits one `AnalyticsTags` object per variant containing:

- `VARIANT_NAME` (e.g. `"freeDevPhone"`)
- `BUILD_TYPE` (e.g. `"debug"`)
- One `const val` per consumer-declared `customTag`, alphabetised by key
- `fun attachTo(target: Any)` — reflectively calls `target.setCustomKey(key, value)`
  for every tag, matching Firebase Crashlytics' signature

---

## DSL

```kotlin
kmpFlavors {
    analytics {
        enabled.set(true)
        customTag("environment") { variant ->
            variant.flavors.firstOrNull { it.name in listOf("dev", "prd") }?.name ?: "default"
        }
        customTag("tier") { variant ->
            variant.flavors.firstOrNull { it.name in listOf("free", "paid") }?.name ?: "default"
        }
    }
}
```

`customTag` resolvers receive the `FlavorVariant` and return the value as a
String. Resolvers run at **configuration time** — closures don't cross the
configuration-cache boundary (the configurator pre-resolves each tag's value
into a `Map<String, String>` before registering the task).

---

## Generated output

```kotlin
package com.example.app

/** v2.6 auto-generated cross-platform analytics metadata. */
object AnalyticsTags {
    const val VARIANT_NAME: String = "freeDev"
    const val BUILD_TYPE: String = "debug"
    const val ENVIRONMENT: String = "dev"
    const val TIER: String = "free"

    /** Reflectively attaches every tag to a Firebase-Crashlytics-shaped target. */
    fun attachTo(target: Any) {
        val method = target.javaClass.methods.firstOrNull {
            it.name == "setCustomKey" && it.parameterCount == 2
        } ?: return
        method.invoke(target, "variant_name", VARIANT_NAME)
        method.invoke(target, "build_type", BUILD_TYPE)
        method.invoke(target, "environment", ENVIRONMENT)
        method.invoke(target, "tier", TIER)
    }
}
```

---

## SDK integration

### Firebase Crashlytics (Android only — `setCustomKey` is on the JVM SDK)

```kotlin
import com.google.firebase.crashlytics.FirebaseCrashlytics

fun initCrashlytics() {
    AnalyticsTags.attachTo(FirebaseCrashlytics.getInstance())
}
```

`attachTo` finds `setCustomKey(String, String)` reflectively — no compile-time
Firebase dep on the plugin side.

### Sentry (all platforms)

Sentry doesn't expose `setCustomKey` directly; wire each tag explicitly:

```kotlin
import io.sentry.Sentry

fun initSentry() {
    Sentry.configureScope { scope ->
        scope.setTag("variant_name", AnalyticsTags.VARIANT_NAME)
        scope.setTag("build_type", AnalyticsTags.BUILD_TYPE)
        scope.setTag("environment", AnalyticsTags.ENVIRONMENT)
        scope.setTag("tier", AnalyticsTags.TIER)
    }
}
```

### Firebase Analytics (Android, user properties)

```kotlin
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.FirebaseAnalytics

fun initAnalytics() {
    val analytics: FirebaseAnalytics = Firebase.analytics
    analytics.setUserProperty("variant", AnalyticsTags.VARIANT_NAME)
    analytics.setUserProperty("tier", AnalyticsTags.TIER)
    analytics.setUserProperty("environment", AnalyticsTags.ENVIRONMENT)
}
```

### Custom observability target

Any target with `setCustomKey(String, String)` works with `attachTo` via
reflection. For other shapes, wire each `const val` explicitly.

---

## Active variant vs. inactive variants

| Variant kind | Output goes to                                | When generated |
|--------------|-----------------------------------------------|----------------|
| Active       | Each target's `main` compilation source set   | Always (`enabled.set(true)`) |
| Inactive     | Per-variant compilation source set            | Matrix mode    |

Without matrix mode, only the active variant compiles, so only one `AnalyticsTags`
object exists. With matrix mode, each variant gets its own.

---

## Tag value resolution rules

- **`VARIANT_NAME`** = `FlavorVariant.name` (e.g. `freeDevPhone`)
- **`BUILD_TYPE`** = `FlavorVariant.buildType?.name` (empty string when build
  types disabled)
- **Custom tag values** alphabetised by key, emitted as
  `const val KEY: String = "value"` (UPPER_SNAKE_CASE) plus the reflective
  `attachTo` call in original key order.

---

## See also

- `docs/DI_INTEGRATION.md` — companion Phase 3 Koin codegen
- `samples/multi-dim-3d/build.gradle.kts` — active `analytics {}` block with 2 custom tags
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/AnalyticsTagsConfig.kt` — DSL surface
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/tasks/GenerateAnalyticsTagsTask.kt` — codegen task
- `plan-layer/.../v26-stability-parity-beyond-platform/03-di-analytics.md` — originating epic plan
