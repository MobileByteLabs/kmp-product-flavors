# Conditional Target Sets per Variant

> **Since v2.6** — `variantFilter { excludeTargets(...) }` skips per-target
> compilations for selected variants. Operationally critical for CI-cost
> control: the `free` tier doesn't need watchOS.

## DSL

```kotlin
kmpFlavors {
    variantFilter {
        if (flavorNames.contains("free")) {
            excludeTargets("watchosArm64", "watchosX64", "tvosArm64", "tvosX64")
        }
    }
}
```

Result: `freeDev` / `freeProd` variants compile for desktop + iOS only.
`paidDev` / `paidProd` variants compile for all 6 targets.

The variant itself **stays in the resolved set** (it's not excluded by
`exclude()`). Only the per-target compilations for the named targets are
skipped — `CompilationRegistrar` queries the filter predicate and
`AggregateTasksRegistrar` likewise omits those tasks from
`assembleAll{Target}Variants`.

## Target naming

Target names must match the strings declared in `kotlin { ... }` exactly
(case-sensitive). They appear as the keys in [`KotlinMultiplatformExtension.targets`](https://kotlinlang.org/api/kotlin-gradle-plugin/kotlin-gradle-plugin-api/org.jetbrains.kotlin.gradle.dsl/-kotlin-multiplatform-extension/targets.html):

| `kotlin { ... }` declaration              | Target name           |
|-------------------------------------------|-----------------------|
| `jvm("desktop")`                          | `desktop`             |
| `iosArm64()`                              | `iosArm64`            |
| `iosSimulatorArm64()`                     | `iosSimulatorArm64`   |
| `watchosArm64()`                          | `watchosArm64`        |
| `tvosArm64()`                             | `tvosArm64`           |
| `js(IR) { browser() }`                    | `js`                  |
| `wasmJs()`                                | `wasmJs`              |

Globs (`"watchos*"`) are **not** supported in v2.6 — pass each target by name.

Inside `variantFilter { }`, the `availableTargets` property surfaces the full
declared set so consumers can inspect / debug:

```kotlin
variantFilter {
    println("Available targets: $availableTargets")  // dev only — remove before commit
    // ...
}
```

## CI cost example

For a 14-target × 9-variant matrix without `excludeTargets`:

| | Compilations |
|---|---:|
| **Without** discipline | 14 × 9 = 126 |
| **With** `excludeTargets` (free skips 4 of 14) | (4 × 10) + (5 × 14) = 110 |
| **Savings** | ~12% |

For the `samples/conditional-targets/` sample (7 targets × 4 variants):

| | Compilations |
|---|---:|
| **Without** | 28 |
| **With** (free skips watchOS/tvOS) | 20 |
| **Savings** | ~28% |

The relative savings scale with `(excluded variants × excluded targets) / total`.

## Dead source sets are intentional

Source sets like `commonFree`, `watchosArm64Free`, `tvosArm64Free` still
exist on disk after `excludeTargets("watchosArm64")` skips the
compilation. They're unused but **don't break the build** — KMP's
source-set hierarchy doesn't have a clean way to "remove" a source set
after creation, and the cost is just unused disk space.

If a stale source set produces a "Unused Kotlin Source Sets" warning, that's
a separate Tier E.1 follow-up — see `docs/SOURCE_SET_DISCIPLINE.md`.

## Common patterns

### Free tier saves Apple-platform CI minutes

```kotlin
variantFilter {
    if (flavorNames.contains("free")) {
        excludeTargets("watchosArm64", "watchosX64", "tvosArm64", "tvosX64")
    }
}
```

### Demo flavor skips all production-only targets

```kotlin
variantFilter {
    if (flavorNames.contains("demo")) {
        excludeTargets("iosArm64", "tvosArm64", "watchosArm64")
        // demo only compiles for simulator/desktop
    }
}
```

### Build-type-aware target exclusion (debug only on desktop)

```kotlin
variantFilter {
    if (buildType == "debug") {
        excludeTargets("iosArm64", "watchosArm64", "tvosArm64")
        // debug builds compile fast — desktop only
    }
}
```

## Implementation notes

- The filter runs at **configuration time**; the per-variant exclusion set
  is captured on `FlavorVariant.excludedTargets` and consumed by
  `CompilationRegistrar` + `AggregateTasksRegistrar`.
- No runtime overhead — compilations that aren't registered simply don't
  exist in Gradle's task graph.
- Active variant unchanged — `excludeTargets` only affects the per-variant
  compilations (matrix mode). The active variant's `main` compilation runs
  on every target regardless (since it's the variant the user picked to
  build right now).

## See also

- `samples/conditional-targets/` — canonical sample (4 variants × 7 targets, free skips watchOS/tvOS)
- `docs/MULTI_DIM_GUIDE.md` — combinatorial-cost guidance for arbitrary-N dimensions
- `docs/SOURCE_SET_DISCIPLINE.md` — dead-source-set rationale
- `docs/NETWORK_CONFIG.md` — companion v2.6 Phase 4 capability
- `plan-layer/.../v26-stability-parity-beyond-platform/04-targets-network.md` — originating epic plan
