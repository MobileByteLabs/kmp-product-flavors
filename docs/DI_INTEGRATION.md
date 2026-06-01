# DI Integration Guide — Koin

> **Since v2.6** — codegens variant-scoped Koin modules from
> `kmpFlavors { di { koin { variantModule(name) { ... } } } }`. Plugin is
> Koin-dep-free; consumer brings their own `io.insert-koin:koin-core`.

The plugin emits three things per declared module:

1. A commonMain `expect val ${name}Module: Module`
2. One `actual val ${name}Module: Module = module { ... }` per flavor body
   the consumer declared
3. A commonMain helper `fun flavorDependentModules(): List<Module>` that
   concatenates every declared module — drop into your `startKoin {}` call.

---

## DSL

```kotlin
kmpFlavors {
    di {
        koin {
            variantModule("analytics") {
                "free" {
                    singleOf("::FreeAnalyticsHelper")
                    bind("AnalyticsHelper")
                }
                "paid" {
                    singleOf("::PaidAnalyticsHelper")
                    bind("AnalyticsHelper")
                }
            }
            variantModule("network") {
                "free" {
                    single("FreeNetworkFactory()")
                }
                "paid" {
                    single("PaidNetworkFactory()")
                    bind("NetworkFactory")
                    raw("named(\"premium\")")
                }
            }
        }
    }
}
```

Available helpers inside the per-flavor scope:

| Helper                    | Emits                                            |
|---------------------------|--------------------------------------------------|
| `singleOf("::Ref")`       | `    singleOf(::Ref)`                            |
| `single("body")`          | `    single { body }`                            |
| `bind("Type")`            | `    bind<Type>()`                               |
| `raw("custom line")`      | `    custom line` (escape hatch for any DSL)     |

---

## Generated output (per variantModule)

`commonMain/.../FlavorDependentModules.kt`:

```kotlin
package com.example.di

import org.koin.core.module.Module

expect val analyticsModule: Module
expect val networkModule: Module

fun flavorDependentModules(): List<Module> = listOf(
    analyticsModule,
    networkModule
)
```

`freeMain/.../AnalyticsKoinActual.kt`:

```kotlin
package com.example.di

import org.koin.core.module.Module
import org.koin.dsl.module

actual val analyticsModule: Module = module {
    singleOf(::FreeAnalyticsHelper)
    bind<AnalyticsHelper>()
}
```

(One mirror per flavor whose body the consumer declared. Flavors with no
`"flavor" { ... }` block get no actual; the standard KMP expect/actual
compile error applies if the active variant's flavor is undeclared.)

---

## Integration patterns

### Pattern A — vanilla `startKoin` (most common)

```kotlin
fun initKoin() {
    startKoin {
        modules(MyAppModules.all + flavorDependentModules())
    }
}
```

### Pattern B — custom aggregator (preserves consumer-controlled list)

```kotlin
object KoinModules {
    val all: List<Module> = listOf(coreModule, databaseModule, networkModule) +
        flavorDependentModules()
}

fun initKoin() {
    startKoin { modules(KoinModules.all) }
}
```

This matches `kmp-project-template`'s `KoinModules.allModules` pattern
(D8 — consumer keeps full control over module ordering).

### Pattern C — Compose `koinInject`

Generated modules work transparently with `koinInject<T>()` from
`org.koin:koin-compose-multiplatform` once registered via patterns A or B.

```kotlin
@Composable
fun MyScreen() {
    val analytics: AnalyticsHelper = koinInject()
    // ...
}
```

---

## Active variant vs. inactive variants

| Variant kind | Output goes to                                | When generated |
|--------------|-----------------------------------------------|----------------|
| Active       | Each target's `main` compilation source set   | Always         |
| Inactive     | Per-variant compilation source set (matrix mode only) | Matrix mode |

Matrix mode (`kmpFlavors { buildMatrix.set(true) }`) builds all variants in
parallel — each gets its own actual val. Without matrix mode, only the
active variant compiles.

---

## Adding Koin to your build

The plugin imports `org.koin.core.module.Module` + `org.koin.dsl.module` in
generated files. Add Koin to commonMain:

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.insert-koin:koin-core:3.5.6")
        }
    }
}
```

For Compose integration:

```kotlin
commonMain.dependencies {
    implementation("io.insert-koin:koin-compose-multiplatform:1.2.0")
}
```

---

## See also

- `docs/ANALYTICS_INTEGRATION.md` — companion Phase 3 cross-platform analytics tag codegen
- `samples/multi-dim-3d/build.gradle.kts` — commented `di {}` block (uncomment to exercise)
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/DiDsl.kt` — DSL entry point
- `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/tasks/GenerateKoinModulesTask.kt` — codegen task
- `plan-layer/.../v26-stability-parity-beyond-platform/03-di-analytics.md` — originating epic plan
