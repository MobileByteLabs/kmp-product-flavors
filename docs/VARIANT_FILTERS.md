# Variant Filters & Matching Fallbacks

> **Related:** [PRODUCT_FLAVORS](PRODUCT_FLAVORS.md) · [BUILD_VARIANTS](BUILD_VARIANTS.md) · [Integration guide for kmp-project-template](KMP_PROJECT_TEMPLATE_INTEGRATION.md)

Two related-but-distinct mechanisms for controlling which variants are built and how cross-module dependencies resolve.

| Mechanism | What it does | When to use |
|---|---|---|
| `variantFilter { … }` | Excludes specific flavor combinations from the build matrix entirely (the variant is never resolved, no source sets are wired, no tasks are registered). | Excluding impossible or nonsensical combinations (e.g. `demo + prod-environment`). |
| `matchingFallbacks(…)` | Tells Gradle which alternative flavor to pick when a dependency module doesn't declare the same flavor. Affects dependency resolution, not the build matrix. | Multi-module projects where some modules declare fewer flavors than the consumer app. |

---

## Variant filters

### Recipe 1 — exclude impossible combinations

A 2×2×3 dimension matrix produces 12 variants. Many real-world combinations are nonsensical. Filter them out:

```kotlin
kmpFlavors {
    flavorDimensions {
        register("tier")        { priority.set(0) }
        register("model")       { priority.set(1) }
        register("environment") { priority.set(2) }
    }
    flavors {
        register("demo") { dimension.set("tier"); isDefault.set(true) }
        register("prod") { dimension.set("tier") }
        register("basic")    { dimension.set("model"); isDefault.set(true) }
        register("advanced") { dimension.set("model") }
        register("dev")     { dimension.set("environment"); isDefault.set(true) }
        register("staging") { dimension.set("environment") }
        register("prodEnv") { dimension.set("environment") }
    }

    variantFilter {
        // Demo builds never hit production — exclude demo + prodEnv.
        if ("demo" in flavorNames && "prodEnv" in flavorNames) exclude()

        // Prod tier never points at the dev backend.
        if ("prod" in flavorNames && "dev" in flavorNames) exclude()
    }
}
```

`./gradlew listFlavors` confirms which variants survive.

### Recipe 2 — exclude per build type

```kotlin
variantFilter {
    // Only the prod tier builds release artefacts; demo is debug-only.
    if ("demo" in flavorNames && buildType == "release") exclude()
}
```

### Recipe 3 — exclude on a sliding window

```kotlin
val today = java.time.LocalDate.now()
variantFilter {
    // Internal-only flavor expires after a date — useful for time-boxed pilots.
    if ("internal" in flavorNames && today.isAfter(java.time.LocalDate.of(2026, 12, 31))) exclude()
}
```

---

## Matching fallbacks

`matchingFallbacks(...)` lives on each flavor and tells Gradle which alternative flavor to pick when a dependency module doesn't declare the consumer's flavor.

### Why you need it

Common pattern: an app declares `demo` and `prod` flavors, but a transitive `:core-network` library declares only `prod` (because it has no demo-mode behaviour). Without fallbacks, Gradle fails to resolve the `demo` variant of the app against `:core-network`. With fallbacks, the `demo` flavor falls back to `prod`'s artifact:

```kotlin
kmpFlavors {
    flavors {
        register("demo") {
            dimension.set("tier")
            matchingFallbacks("prod")           // demo falls back to prod when target lacks demo
        }
        register("prod") {
            dimension.set("tier")
        }
    }
}
```

### Recipe 1 — single fallback

```kotlin
register("internal") {
    dimension.set("tier")
    matchingFallbacks("prod")
}
```

### Recipe 2 — chained fallbacks (priority order)

```kotlin
register("clientA") {
    dimension.set("consumer")
    matchingFallbacks("internal", "prod")  // try internal first, then prod
}
```

### Recipe 3 — AGP bridge propagation

When `bridgeAgpProductFlavors.set(true)` is also enabled, `matchingFallbacks(...)` flows through to AGP's `productFlavor.matchingFallbacks` automatically — no separate `android { … }` block needed:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

kmpFlavors {
    bridgeAgpProductFlavors.set(true)

    flavors {
        register("demo") {
            dimension.set("tier")
            matchingFallbacks("prod")
        }
    }
}

// No need for:
// android { productFlavors { create("demo") { matchingFallbacks += "prod" } } }
```

---

## Decision tree

| Symptom | Likely fix |
|---|---|
| `./gradlew listFlavors` shows a combo that should not exist | `variantFilter { … }` (Recipe 1) |
| `Could not resolve all task dependencies for configuration 'demoRuntimeClasspath'` | `matchingFallbacks(...)` on the `demo` flavor (Recipe 1) |
| AGP-side dependency resolution fails for a hand-written `android.productFlavors {}` block | Move flavor declarations into `kmpFlavors {}` and turn on `bridgeAgpProductFlavors` (Recipe 3) |
| The build matrix is too large for CI | Combine `variantFilter` exclusions (Recipe 1+2) — confirm with `./gradlew listFlavors` |

## Limitations

- `variantFilter { … }` cannot **add** variants — only exclude. To add new flavor combinations, declare more flavors in `flavors { … }`.
- `matchingFallbacks(...)` only kicks in when Gradle resolves a dependency variant; it does **not** affect the build matrix or which source sets get wired.
- Per-build-type `matchingFallbacks` belong on `BuildTypeConfig`, not `FlavorConfig`. The DSL surface mirrors AGP's split.
