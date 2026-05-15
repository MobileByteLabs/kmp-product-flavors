/*
 * Copyright 2026 MobileByteLabs
 *
 * Sample module demonstrating KMP Product Flavors plugin usage.
 *
 * This sample uses 2 dimensions:
 * - tier: free, paid
 * - environment: dev, staging, prod
 *
 * Run with different flavors:
 *   ./gradlew :sample:build -PkmpFlavor=freeDev
 *   ./gradlew :sample:build -PkmpFlavor=paidProd
 *   ./gradlew :sample:listFlavors
 */

plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

kotlin {
    // Desktop JVM target
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Source set dependencies
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
            }
        }
    }
}

kmpFlavors {
    // v2.2 Phase 0A introduces an auto-enable heuristic that flips `buildMatrix` to
    // true when a module has ≥2 non-Android targets + ≥2 flavors. This sample is the
    // v1.x active-variant demo (its `commonMain/App.kt` references `AppConfig` which
    // matrix mode moves out of commonMain). Opt out of Phase 0 auto-detection to keep
    // the v1.x behavior. The separate `samples/matrix-mode/` exercises matrix mode.
    autoEnable.set(false)

    // Enable BuildConfig generation
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.sample")
    buildConfigClassName.set("AppConfig")

    // Create intermediate source sets (nativeMain for iOS)
    createIntermediateSourceSets.set(true)

    // Define dimensions
    flavorDimensions {
        register("tier") {
            priority.set(0) // First in variant name
        }
        register("environment") {
            priority.set(1) // Second in variant name
        }
    }

    // Define flavors
    flavors {
        // Tier dimension
        register("free") {
            dimension.set("tier")
            isDefault.set(true)

            // Build config fields
            buildConfigField("Boolean", "IS_PREMIUM", "false")
            buildConfigField("Int", "MAX_ITEMS", "10")
            buildConfigField("Boolean", "SHOW_ADS", "true")
        }

        register("paid") {
            dimension.set("tier")

            buildConfigField("Boolean", "IS_PREMIUM", "true")
            buildConfigField("Int", "MAX_ITEMS", "1000")
            buildConfigField("Boolean", "SHOW_ADS", "false")
        }

        // Environment dimension
        register("dev") {
            dimension.set("environment")
            isDefault.set(true)

            buildConfigField("String", "BASE_URL", "\"https://dev-api.example.com\"")
            buildConfigField("Boolean", "DEBUG_MODE", "true")
            buildConfigField("String", "LOG_LEVEL", "\"VERBOSE\"")
        }

        register("staging") {
            dimension.set("environment")

            buildConfigField("String", "BASE_URL", "\"https://staging-api.example.com\"")
            buildConfigField("Boolean", "DEBUG_MODE", "true")
            buildConfigField("String", "LOG_LEVEL", "\"DEBUG\"")
        }

        register("prod") {
            dimension.set("environment")

            buildConfigField("String", "BASE_URL", "\"https://api.example.com\"")
            buildConfigField("Boolean", "DEBUG_MODE", "false")
            buildConfigField("String", "LOG_LEVEL", "\"ERROR\"")
        }
    }
}
