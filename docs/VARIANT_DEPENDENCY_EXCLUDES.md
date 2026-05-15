# Variant-conditional dependency exclusions (v2.4+)

> **Status: 🚢 shipped 2026-05-15** as part of the v2.4 cycle. The v2.3 plan flagged this as survey-gated docs-only; v2.4 graduates to the full implementation. The DSL described below is live + functional.

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

## Implementation notes

`VariantDependenciesScope` collects exclude requests via the `exclude(group, module)` method. At plugin-apply time, `DependencyConfigurator.applyVariantExcludes(...)` walks every registered variant's excludes + applies them to the variant's compile + runtime classpath configurations per (variant × target).

Excludes are scoped per-variant: a `free` variant's exclude does NOT affect the `paid` variant's classpath. Cross-contamination is impossible because each variant has its own `KotlinCompilation` with its own dependency configurations.

Pass an empty `group` or `module` for wildcard matching (Gradle's standard exclude semantics). Passing both empty triggers KMPF-V22 WARNING at apply time.

## Manual workaround (only needed pre-v2.4)

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

## Tracked in

- [`v2.3 plan` Phase 3](https://github.com/MobileByteLabs/kmp-product-flavors/tree/development/plan-layer/plans/2026-05-14-kmp-product-flavors-v2.3-plan.md) — full design + gating rationale.
- This document — consumer-facing reference + voting instructions.
