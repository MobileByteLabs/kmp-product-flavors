/*
 * Copyright 2026 MobileByteLabs
 *
 * v2.4 baseline: stability-plan Phase 1 sample — exercised EVERY alpha-tagged
 * surface against 3 flavors × 3 buildTypes × 6 non-Android targets = 54
 * compilations.
 *
 * v2.5 expansion: matrix grows to 3 flavors × 3 buildTypes × 12 non-Android
 * targets = 108 compilations after enabling watchOS×3 + tvOS×3 + linuxX64 +
 * mingwX64 below. CI sharding in .github/workflows/sample-target-coverage.yml
 * splits the matrix per-OS-runner so each runner sees a tractable subset.
 *
 * Compared to samples/matrix-mode/ (2 flavors × 2 buildTypes × 1 target),
 * this sample is the actual stress test the plugin needs before GA.
 *
 * Run:
 *   ./gradlew :samples:multi-target-multi-variant:assembleAllVariants
 *   ./gradlew :samples:multi-target-multi-variant:tasks --group="kmpFlavors variants"
 *   ./gradlew :samples:multi-target-multi-variant:detektFreeDebugDesktop
 *   ./gradlew :samples:multi-target-multi-variant:switchVariantAndReload --to=paidStaging
 *   ./gradlew :samples:multi-target-multi-variant:publishToMavenLocal
 *
 * Per-OS aggregate tasks (v2.5):
 *   macOS:    assembleAllIos{X64,Arm64,SimulatorArm64}Variants
 *             assembleAllWatchos{X64,Arm64,SimulatorArm64}Variants
 *             assembleAllTvos{X64,Arm64,SimulatorArm64}Variants
 *   Linux:    assembleAll{Desktop,Js,WasmJs,LinuxX64}Variants
 *   Windows:  assembleAllMingwX64Variants
 */

plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
    `maven-publish`
}

group = "io.github.mobilebytelabs.samples"
version = "2.4.0-alpha.0"

kmpFlavors {
    // Note: this sample originally set `autoEnable.set(false)` as a workaround
    // for a matrix-mode duplicate-BuildKonfig-codegen regression observed at
    // sample-creation time (PR #80). Verified resolved (no longer reproducible)
    // as of 2026-05-16 in the stability-plan investigation — the 54-compilation
    // matrix (3 flavors × 3 buildTypes × 6 non-Android targets) now builds
    // clean via `assembleAllVariants` with `autoEnable=true` (default). The
    // workaround has been removed; the CI workflow at
    // .github/workflows/sample-multi-target.yml locks in the green path.

    // Single-point opt-in via the DSL (`gradle.properties` form also works).
    buildMatrix.set(true)
    publishMatrix.set(true)
    enableBuildTypes.set(true)

    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.multitarget")
    buildConfigClassName.set("BuildKonfig")

    // RFC §10 closer — common{BuildType} source sets shared across sibling-buildType
    // variants. Surfaces the "Invalid Source Set Dependency Across Trees" KGP
    // warning as a known limitation (cosmetic only).
    createIntermediateBuildTypeSourceSets.set(true)

    // v2.3 Phase 1 — Detekt per-(variant × target). Requires detektPerVariant=true
    // (auto-fired by Phase 0C when Detekt plugin is detected — not applied in this
    // sample, so the Phase 0C check no-ops + this flag has no effect here. It's
    // declared to exercise the DSL surface; effect lights up when consumers
    // apply Detekt too).
    detektPerVariantPerTarget.set(true)

    // v2.4 Phase 2 path-(b) — variant-scoped Gradle build cache namespacing.
    // Injects kmpFlavorVariant as @Input on every compileKotlin* task in matrix
    // mode. Prerequisite: buildMatrix=true (set above).
    variantCacheNamespacing.set(true)

    // v2.3 Phase 7 — per-variant Compose hot-reload Option A. No-op without
    // org.jetbrains.compose applied (not applied in this sample). Flag declared
    // to exercise the DSL surface; lights up when consumers apply CMP too.
    composeHotReloadPerVariant.set(true)

    flavors {
        // Note: don't name custom buildConfigField the same as an auto-derived
        // IS_<FLAVOR> / IS_<BUILDTYPE> constant. The codegen will produce
        // duplicate `const val` entries. Use a different prefix (e.g. PREMIUM_*)
        // for custom flags. Track surfacing this as KMPF-V23 in stability-plan
        // Phase 6A backlog.
        register("free") {
            isDefault.set(true)
            buildConfigField("Int", "MAX_ITEMS", "10")
            buildConfigField("String", "TIER_NAME", "\"free\"")
        }
        register("paid") {
            buildConfigField("Int", "MAX_ITEMS", "1000")
            buildConfigField("String", "TIER_NAME", "\"paid\"")
        }
        register("enterprise") {
            buildConfigField("Int", "MAX_ITEMS", "100000")
            buildConfigField("String", "TIER_NAME", "\"enterprise\"")
        }
    }

    buildTypes {
        register("debug") { isDefault.set(true) }
        register("staging")
        register("release")
    }

    // v2.4 Phase 5 — variant-conditional dependency excludes. Strips a
    // hypothetical "premium-sdk" dep from every free* variant's classpath.
    // The dep isn't actually declared in this sample, so it's a no-op at
    // runtime — but it exercises the DSL + the applyVariantExcludes path,
    // surfacing any cross-variant classpath regressions in CI.
    variants
        .matching { it.flavors.contains("free") }
        .configureEach {
            dependencies {
                exclude(group = "com.example", module = "premium-sdk")
            }
        }
}

kotlin {
    // v2.4 baseline: 6 non-Android targets (54-compilation matrix).
    jvm("desktop")
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) { browser() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // v2.5 expansion: 6 additional non-Android targets (108-compilation matrix total).
    // PlatformDetector has supported all of these since v1.1.0; v2.5 closes the
    // sample-coverage gap and validates per-variant Compose resources + aggregate
    // task generation for each. See docs/SUPPORTED_TARGETS.md.
    watchosX64()
    watchosArm64()
    watchosSimulatorArm64()
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        // commonMain reads BuildKonfig set by the active-variant codegen +
        // the Phase 5 exclude scopes. No per-flavor deps here — this sample
        // is about exercising the matrix shape, not real consumer integration.
    }
}
