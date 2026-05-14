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

    // W5.1 fix: when matrix mode is on, the active-variant BuildKonfig is
    // wired into each non-Android target's `main` defaultSourceSet (e.g.
    // `desktopMain`) instead of commonMain. That scopes it to only the
    // active-variant compilation, so inactive-variant compilations see
    // ONLY their own per-variant BuildKonfig.
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.matrixmode")
    buildConfigClassName.set("BuildKonfig")

    enableBuildTypes.set(true)

    flavors {
        register("free") {
            isDefault.set(true)
            buildConfigField("Boolean", "IS_PREMIUM", "false")
            buildConfigField("Int", "MAX_ITEMS", "10")
        }
        register("paid") {
            buildConfigField("Boolean", "IS_PREMIUM", "true")
            buildConfigField("Int", "MAX_ITEMS", "1000")
        }
    }
    buildTypes {
        // Don't declare explicit `IS_DEBUG` here — the generator
        // auto-derives `IS_<BUILDTYPE>` constants, so an explicit field
        // with the same name would collide.
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
