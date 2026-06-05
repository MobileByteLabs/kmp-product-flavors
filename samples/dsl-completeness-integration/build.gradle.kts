/*
 * dsl-completeness-integration — the "kitchen sink" v2.8 sample.
 * Exercises every public DSL surface added through v2.8:
 *   - flavorDimensions {} + flavors {} (multi-dimensional)
 *   - signingConfigs {} (v2.8 Wave A1 new feature)
 *   - per-flavor versionCode / versionName (v2.8 Wave A1 new feature)
 *   - buildConfigField (string, boolean, int)
 *   - applicationIdSuffix
 *   - createIntermediateSourceSets
 *
 * Run:
 *   ./gradlew :samples:dsl-completeness-integration:listFlavors
 *   ./gradlew :samples:dsl-completeness-integration:compileKotlinDesktop
 */
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser()
    }
}

kmpFlavors {
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.dsl")
    createIntermediateSourceSets.set(true)

    // v2.8 new: signingConfigs DSL block
    signingConfigs {
        register("release") {
            storeFile.set(file("release.keystore"))
            storePasswordFromEnv("STORE_PASSWORD")
            keyAlias.set("releaseKey")
            keyPasswordFromEnv("KEY_PASSWORD")
        }
    }

    flavorDimensions {
        register("tier") { priority.set(0) }
        register("env") { priority.set(1) }
    }

    flavors {
        register("free") {
            dimension.set("tier")
            isDefault.set(true)
            buildConfigField("Boolean", "TIER_PREMIUM", "false")
            buildConfigField("Int", "RATE_LIMIT", "10")
        }
        register("premium") {
            dimension.set("tier")
            buildConfigField("Boolean", "TIER_PREMIUM", "true")
            buildConfigField("Int", "RATE_LIMIT", "1000")
        }

        register("demo") {
            dimension.set("env")
            isDefault.set(true)
            applicationIdSuffix.set(".demo")
            buildConfigField("String", "API_URL", "\"https://demo.example.com\"")
            buildConfigField("Boolean", "ENV_DEMO", "true")
            versionCode.set(1)
            versionName.set("2.8.0-demo")
        }
        register("prod") {
            dimension.set("env")
            buildConfigField("String", "API_URL", "\"https://api.example.com\"")
            buildConfigField("Boolean", "ENV_DEMO", "false")
            signingConfig.set("release")
            versionCode.set(2800)
            versionName.set("2.8.0")
        }
    }
}
