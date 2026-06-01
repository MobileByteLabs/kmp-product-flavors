# Migration: v2.5 → v2.6

**You do not need to migrate.**

All v2.5 DSL surfaces are fully supported in v2.6. Version floor unchanged
(Gradle 8.0+ / KGP 2.0.21+ / AGP 8.0+ / JDK 17+ / CMP 1.7.0+ — see
[`COMPATIBILITY_MATRIX.md`](COMPATIBILITY_MATRIX.md)). This document is a
**cookbook** for consumers who want to adopt the new v2.6 features — not a
required migration path.

---

## When to adopt the new v2.6 features

| Feature | Adopt if | Skip if |
|---|---|---|
| `di { koin { variantModule() } }` | You write per-flavor expect/actual Koin modules manually today | Your Koin setup is already minimal or you don't use Koin |
| `analytics { customTag() }` | You wire variant tags into Firebase / Sentry manually | You don't use observability tooling |
| `variantFilter { excludeTargets() }` | You have a `free` (or `demo`) tier that doesn't need every target | All variants ship every target |
| `buildKonfig { network { baseUrl() } }` | You hardcode base URLs in per-flavor `BuildKonfig` fields today | You use a different config-management pattern |

---

## Adopting the v2.6 DSLs

### Per-variant Koin DI modules

```kotlin
// build.gradle.kts
kmpFlavors {
    di {
        koin {
            variantModule("network") {
                "free" {
                    single("FreeNetworkFactory()")
                    bind("NetworkFactory")
                }
                "paid" {
                    single("PaidNetworkFactory()")
                    bind("NetworkFactory")
                }
            }
        }
    }
}
```

```kotlin
// In consumer code:
fun initKoin() = startKoin {
    modules(MyAppModules.all + flavorDependentModules())   // plugin-generated
}
```

See [`DI_INTEGRATION.md`](DI_INTEGRATION.md) for the full pattern + integration recipes.

### Cross-platform analytics tags

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

```kotlin
// Consumer code (Android):
AnalyticsTags.attachTo(FirebaseCrashlytics.getInstance())
```

See [`ANALYTICS_INTEGRATION.md`](ANALYTICS_INTEGRATION.md).

### Conditional target sets

```kotlin
kmpFlavors {
    variantFilter {
        if (flavorNames.contains("free")) {
            excludeTargets("watchosArm64", "watchosX64", "tvosArm64", "tvosX64")
        }
    }
}
```

Reduces compilation count by `(excluded variants × excluded targets)`.
See [`CONDITIONAL_TARGETS.md`](CONDITIONAL_TARGETS.md).

### Variant-aware base URLs (Ktor)

```kotlin
kmpFlavors {
    buildKonfig {
        network {
            baseUrl(
                "free" to "https://api.free.example.com",
                "paid" to "https://api.paid.example.com",
            )
            timeout(seconds = 30)
        }
    }
}
```

```kotlin
// Consumer code:
val client = HttpClient {
    install(DefaultRequest) { url(BuildKonfig.Network.BASE_URL) }
    install(HttpTimeout) {
        requestTimeoutMillis = (BuildKonfig.Network.TIMEOUT_SECONDS * 1000).toLong()
    }
}
```

See [`NETWORK_CONFIG.md`](NETWORK_CONFIG.md).

---

## New validator codes in v2.6

| Code | Severity | Trigger |
|---|---|---|
| `KMPF-V29` | ERROR | `buildKonfig.network.baseUrl()` references a flavor not registered in any dimension |
| `KMPF-V30` | ERROR | Some resolved variant's active flavor has no `baseUrl` mapped |

Full catalog: [`ERROR_CODES.md`](ERROR_CODES.md).

---

## Quality bonuses (no consumer-side changes required)

- **Coverage gate** runs on PRs touching `build-logic/**` — surfaces regressions before merge.
- **AGP matrix CI** validates the reflective `finalizeDsl` + `beforeVariants` paths against AGP 8.0, 8.5, 8.10, 9.0-rc.
- **KMP↔AGP variantFilter parity** — `kmpFlavors.variantFilter { exclude() }` now propagates to AGP via `beforeVariants` (closes the v2.5 asymmetry).

These ship transparently; nothing to opt into.

---

## See also

- [`MIGRATION_v2.4_TO_v2.5.md`](MIGRATION_v2.4_TO_v2.5.md) — predecessor migration cookbook (also opens "You do not need to migrate.")
- [`KMP_AGP_PARITY.md`](KMP_AGP_PARITY.md) — how v2.6 closes the v2.5 KMP↔AGP asymmetry
- [`COVERAGE_GUIDE.md`](COVERAGE_GUIDE.md) — coverage gate (contributors only)
- [`SOURCE_SET_DISCIPLINE.md`](SOURCE_SET_DISCIPLINE.md) — research finding on inactive source-set warnings (deferred Tier E.1 fix)
