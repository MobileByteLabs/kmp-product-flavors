# Variant-conditional dependency exclusions (v2.3+ — survey-gated)

> **Status: docs-only as of v2.3.** The DSL surface below is the planned design; the implementation is gated on ≥5 consumer requests per the v2.3 plan's Phase 3 acceptance criteria. Comment on [this tracking discussion](https://github.com/MobileByteLabs/kmp-product-flavors/discussions) if you want this shipped.

---

## Motivation

AGP supports per-variant dependency exclusions on Android consumers:

```kotlin
configurations {
    freeDebugRuntimeClasspath {
        exclude(group = "com.example", module = "premium-sdk")
    }
}
```

KMP / matrix mode currently doesn't expose this. Consumers who want to drop a transitive on the free variant must either fork the upstream library or accept the bloat. v2.1's `dependencies { … }` block in `flavors { register("free") { … } }` **adds** deps per flavor but doesn't **remove** them. v2.2 + v2.3 fill every other AGP-parity gap; this one is the last remaining feature gap.

---

## Proposed DSL surface

```kotlin
kmpFlavors {
    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
}

// Variant-conditional excludes via the v2.0 Q19 variants API.
kmpFlavors.variants
    .matching { it.flavors.contains("free") }
    .configureEach {
        dependencies {
            exclude(group = "com.example", module = "premium-sdk")
            exclude(group = "com.example", module = "premium-analytics")
        }
    }
```

The `dependencies { … }` block on `KmpFlavorVariant` is the v2.3 Phase 3 new surface. It uses Gradle's standard `exclude(group, module)` shape so adopters familiar with `configurations.runtimeClasspath { exclude(...) }` get a familiar API.

---

## Why it's not shipped yet

The v2.3 plan's gating section requires **≥5 GitHub Discussions or Issues requesting per-variant dep exclusions** before the full implementation ships. Without explicit consumer demand, the DSL surface would be speculative — building a feature that adds maintenance burden but nobody actually uses.

If the survey gate doesn't clear before v2.4 cuts, the docs continue to track the design + how to manually achieve the equivalent today.

---

## Manual workaround today

For Android consumers, the AGP bridge already routes KMP flavors into AGP's `productFlavors`. So `configurations.freeDebugRuntimeClasspath { exclude(...) }` works on the Android target without any plugin support.

For non-Android targets (Desktop, iOS, JS, WasmJs), the manual path requires touching the variant's compilation directly:

```kotlin
// build.gradle.kts
afterEvaluate {
    kmpFlavors.variants.matching { it.flavors.contains("free") }.configureEach {
        val variantName = name
        kotlin.targets.matching { it.platformType.name != "androidJvm" }.configureEach {
            val targetCompilation = compilations.findByName(variantName) ?: return@configureEach
            project.configurations
                .findByName(targetCompilation.compileDependencyConfigurationName)
                ?.exclude(group = "com.example", module = "premium-sdk")
        }
    }
}
```

That's the boilerplate that Phase 3 collapses into a one-line `dependencies { exclude(...) }`.

---

## How to vote

If you want this shipped, comment on or open a [GitHub Discussion](https://github.com/MobileByteLabs/kmp-product-flavors/discussions). Include:

- What library / module you'd exclude (concrete example helps prioritise).
- Which variant(s) the exclude scopes to.
- Target platform(s) — Desktop / iOS / JS / WasmJs (Android already works via the AGP bridge).

5 such requests trip the v2.3 plan's gate and Phase 3 graduates from docs-only to a v2.3.x release.

---

## Tracked in

- [`v2.3 plan` Phase 3](https://github.com/MobileByteLabs/kmp-product-flavors/tree/development/plan-layer/plans/2026-05-14-kmp-product-flavors-v2.3-plan.md) — full design + gating rationale.
- This document — consumer-facing reference + voting instructions.
