/*
 * Copyright 2026 MobileByteLabs
 *
 * v2.6 Phase 4 — sample exercising `variantFilter { excludeTargets(...) }` for
 * CI cost discipline. The `free` tier never ships for watchOS or tvOS; the
 * `paid` tier compiles for all 6 targets.
 *
 * Resulting matrix (after exclusion):
 *   free × dev    → desktop, iosArm64, iosSimulatorArm64                       (3 compilations)
 *   free × prod   → desktop, iosArm64, iosSimulatorArm64                       (3 compilations)
 *   paid × dev    → desktop, iosArm64, iosSimulatorArm64, watchos*, tvos*      (7 compilations)
 *   paid × prod   → desktop, iosArm64, iosSimulatorArm64, watchos*, tvos*      (7 compilations)
 * Total: 20 compilations vs. 28 without `excludeTargets` (~28% CI minute savings).
 *
 * Run:
 *   ./gradlew :samples:conditional-targets:listFlavors
 *   ./gradlew :samples:conditional-targets:tasks --all --group="kmpFlavors variants"
 *
 * Documentation:
 *   docs/CONDITIONAL_TARGETS.md — full pattern + CI cost guidance + dead-source-set rationale
 *   docs/NETWORK_CONFIG.md     — companion v2.6 Phase 4 capability
 */

plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

group = "io.github.mobilebytelabs.samples"
version = "2.6.0-alpha.1"

kmpFlavors {
    buildMatrix.set(true)
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.condtargets")

    dimensions {
        dimension("tier") {
            flavor("free") {
                isDefault.set(true)
                buildConfigField("Boolean", "IS_PREMIUM", "false")
            }
            flavor("paid") {
                buildConfigField("Boolean", "IS_PREMIUM", "true")
            }
        }
        dimension("env") {
            flavor("dev") {
                isDefault.set(true)
                buildConfigField("String", "API_BASE_URL", "\"https://api.dev.example.com\"")
            }
            flavor("prod") {
                buildConfigField("String", "API_BASE_URL", "\"https://api.example.com\"")
            }
        }
    }

    // v2.6 Phase 4 — `free` tier never ships for watchOS/tvOS. The variant
    // stays in the resolved set; only per-target compilations on the named
    // targets are skipped. See docs/CONDITIONAL_TARGETS.md.
    variantFilter {
        if (flavorNames.contains("free")) {
            excludeTargets("watchosArm64", "watchosX64", "tvosArm64", "tvosX64")
        }
    }
}

kotlin {
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    watchosArm64()
    watchosX64()
    tvosArm64()
    tvosX64()
}
