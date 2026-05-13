/*
 * Copyright 2026 MobileByteLabs
 *
 * v2.0 matrix-mode sample. Exercises every consumer-facing surface the
 * plugin ships:
 *
 *   - buildMatrix opt-in (Q5 / Q16-C)
 *   - 4-variant matrix (2 flavors × 2 buildTypes)
 *   - per-flavor source set with expect/actual (Q11)
 *   - cross-variant isolation (Q12) — see src/commonPaid for paid-only API
 *   - per-variant dependencies (Q17) — kotlinx-coroutines-core on commonPaid only
 *   - kmpFlavors.variants public API (Q19-B) — used below to print variant metadata
 *   - variantFilter setIgnore (Q20-A) — excludes paidRelease
 *   - publishMatrix per-variant Maven publications (Q21-D)
 *
 * Run:
 *   ./gradlew :samples:matrix-mode:assembleAllVariants
 *   ./gradlew :samples:matrix-mode:tasks --group="kmpFlavors variants"
 *   ./gradlew :samples:matrix-mode:publishToMavenLocal
 */

plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
    `maven-publish`
}

group = "io.github.mobilebytelabs.samples"
version = "2.0.0-alpha.1"

// kmpFlavors block FIRST — registers flavors / build types so the per-flavor
// `common{Flavor}` source sets exist before the `kotlin { sourceSets { } }` block
// below references them. See docs/MATRIX_MODE.md "Single-point opt-in".
kmpFlavors {
    // Single-point opt-in (RFC §3 Q16-C, can also use `gradle.properties`).
    buildMatrix.set(true)
    publishMatrix.set(true)

    // Known limitation (tracked for a W5 plugin fix): when matrix mode is on
    // and `generateBuildConfig=true`, the active-variant BuildKonfig.kt is
    // emitted to `commonMain/kotlin` and inactive-variant compilations
    // inherit it via the source-set hierarchy AND their own per-variant
    // BuildKonfig.kt — resulting in `Redeclaration` at the variant compile.
    // The W5 fix lands the active-variant BuildKonfig in the JVM target's
    // main source set instead of commonMain so it's scoped correctly.
    // For now this sample disables codegen — the per-variant BuildKonfig
    // generator is independently exercised by PerVariantBuildConfigTest in
    // the plugin's unit suite.
    generateBuildConfig.set(false)

    enableBuildTypes.set(true)

    flavors {
        register("free") { isDefault.set(true) }
        register("paid")
    }
    buildTypes {
        register("debug") { isDefault.set(true) }
        register("release")
    }

    // Q20-A AGP-style filter: paidRelease isn't shipped in this sample,
    // so it's removed from the matrix entirely. listFlavors marks it
    // "(filtered out)".
    variantFilter {
        if (flavors.any { it.name == "paid" } && buildType == "release") {
            setIgnore(true)
        }
    }

    // Q19-B variant API usage: register a per-variant verification task
    // that prints the variant's metadata. Demonstrates `configureEach` and
    // `matching`.
    variants.configureEach {
        val variantName = name
        val variantFlavors = flavors
        val variantBuildType = buildType
        project.tasks.register("describe${variantName.replaceFirstChar { it.uppercase() }}") {
            group = "kmpFlavors variants"
            description = "Prints metadata for the '$variantName' variant."
            doLast {
                println("VARIANT[$variantName] flavors=$variantFlavors buildType=$variantBuildType")
            }
        }
    }
}

kotlin {
    jvm("desktop")

    sourceSets {
        // Q17 per-variant deps: kotlinx-coroutines-core is declared ONLY on
        // commonPaid, so only paid* variants can reference kotlinx.coroutines.
        // Free* variants compiling code that imports kotlinx.coroutines would
        // fail with "Unresolved reference".
        val commonPaid by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
    }
}
