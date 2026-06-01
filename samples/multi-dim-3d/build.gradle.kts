/*
 * Copyright 2026 MobileByteLabs
 *
 * v2.5 — Canonical 3-dimension sample showcasing the new ergonomic
 * `dimensions { dimension(name) { flavor(name) {} } }` DSL block and the
 * variantFilter discipline required for arbitrary-N dimensions.
 *
 * Matrix shape:
 *   dimension "tier" × dimension "env" × dimension "form" = 2 × 2 × 2 = 8 candidate variants
 *   variantFilter prunes (free × prod × *) = -2 → 6 final variants
 *
 * Final variants:
 *   freeDevPhone, freeDevTablet, paidDevPhone, paidDevTablet,
 *   paidPrdPhone, paidPrdTablet
 *
 * Run:
 *   ./gradlew :samples:multi-dim-3d:assembleAllVariants
 *   ./gradlew :samples:multi-dim-3d:listFlavors
 *   ./gradlew :samples:multi-dim-3d:tasks --group="kmpFlavors variants"
 *
 * Documentation:
 *   docs/MULTI_DIM_GUIDE.md — variant-filter discipline, combinatorial-cost guidance
 *   docs/MIGRATION_v2.4_TO_v2.5.md — opt-in cookbook (opens with "You do not need to migrate.")
 */

plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

group = "io.github.mobilebytelabs.samples"
version = "2.5.0-alpha.1"

// kmpFlavors block FIRST — registers dimensions + member flavors so the per-flavor
// `common{Flavor}` source sets exist before the `kotlin { sourceSets { } }` block
// below references them. Identical ordering discipline to the matrix-mode sample.
kmpFlavors {
    // Single-point opt-in into matrix mode + per-variant BuildKonfig generation.
    buildMatrix.set(true)
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.multidim3d")
    buildConfigClassName.set("BuildKonfig")

    // v2.5 — ergonomic dimensions {} sugar. Equivalent to flat
    // flavorDimensions { register("tier"); register("env"); register("form") } +
    // flavors { register("free") { dimension.set("tier") }; ... } but tree-shaped.
    //
    // The dimension priority defaults to declaration order — `tier` is highest priority,
    // followed by `env`, followed by `form`. This drives the resolved variant naming
    // (tier-first: "freeDevPhone", not "phoneDevFree") and AGP's flavorDimensions
    // ordering when the AGP bridge propagates this to Android.
    dimensions {
        dimension("tier") {
            flavor("free") {
                isDefault.set(true)
                buildConfigField("Boolean", "IS_PREMIUM", "false")
                buildConfigField("Int", "MAX_ITEMS", "10")
            }
            flavor("paid") {
                buildConfigField("Boolean", "IS_PREMIUM", "true")
                buildConfigField("Int", "MAX_ITEMS", "1000")
            }
        }
        dimension("env") {
            flavor("dev") {
                isDefault.set(true)
                buildConfigField("String", "API_BASE_URL", "\"https://api.dev.example.com\"")
            }
            flavor("prd") {
                buildConfigField("String", "API_BASE_URL", "\"https://api.example.com\"")
            }
        }
        dimension("form") {
            flavor("phone") {
                isDefault.set(true)
                // Note: auto-derived `IS_PHONE` and `IS_TABLET` constants are already
                // emitted per RFC §3 Q23 (IS_<FLAVOR> flags). Custom buildConfigFields
                // must avoid the `IS_*` namespace — KMPF-V23 fires on collision.
                buildConfigField("Int", "MIN_SCREEN_WIDTH_DP", "320")
            }
            flavor("tablet") {
                buildConfigField("Int", "MIN_SCREEN_WIDTH_DP", "600")
            }
        }
    }

    // variantFilter discipline — required for arbitrary-N dimensions to avoid
    // combinatorial blowup. See docs/MULTI_DIM_GUIDE.md for full guidance.
    //
    // Business rule modeled here: the free tier never ships in production. This
    // prunes (free × prd × phone) + (free × prd × tablet) = 2 variants from the
    // 8-variant cross-product, yielding 6 actual buildable variants.
    variantFilter {
        if (flavorNames.contains("free") && flavorNames.contains("prd")) {
            exclude()
        }
    }

    // v2.6 Phase 3 — cross-platform analytics tags codegen. Emits per-variant
    // `AnalyticsTags.kt` with VARIANT_NAME + BUILD_TYPE + each declared customTag
    // as `const val` plus an `attachTo(target)` reflective helper. See
    // docs/ANALYTICS_INTEGRATION.md.
    analytics {
        enabled.set(true)
        customTag("environment") { variant ->
            variant.flavors.firstOrNull { it.name in listOf("dev", "prd") }?.name ?: "default"
        }
        customTag("tier") { variant ->
            variant.flavors.firstOrNull { it.name in listOf("free", "paid") }?.name ?: "default"
        }
    }

    // v2.6 Phase 3 — DI (Koin) variant module codegen. Emits per-variant `actual val
    // networkModule: Module = module { ... }` files + commonMain `expect val` +
    // `flavorDependentModules(): List<Module>` aggregator helper. See
    // docs/DI_INTEGRATION.md for the full integration pattern.
    //
    // NOTE: this sample does NOT wire the Koin runtime — the codegen output imports
    // `org.koin.core.module.Module` and `org.koin.dsl.module`, which only compile
    // when the consumer adds `io.insert-koin:koin-core`. The block stays commented
    // to keep `:samples:multi-dim-3d:build` green on the plugin's CI matrix
    // (no Koin dep on the sample classpath).
    //
    // di {
    //     koin {
    //         variantModule("network") {
    //             "free" {
    //                 singleOf("::FreeNetworkFactory")
    //                 bind("NetworkFactory")
    //             }
    //             "paid" {
    //                 singleOf("::PaidNetworkFactory")
    //                 bind("NetworkFactory")
    //             }
    //         }
    //     }
    // }
}

kotlin {
    jvm("desktop")
    // Minimum target set — the point of this sample is dimensions {} DSL ergonomics
    // + variantFilter, not target rotation. For target coverage see
    // samples/multi-target-multi-variant.
}
