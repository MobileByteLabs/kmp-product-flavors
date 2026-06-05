/*
 * desktop-flavor-integration — demonstrates Compose Desktop per-flavor
 * nativeDistributions wiring and per-flavor JAR Manifest entries.
 *
 * Run:
 *   ./gradlew :samples:desktop-flavor-integration:listFlavors
 *   ./gradlew :samples:desktop-flavor-integration:compileKotlinDesktop
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
}

kmpFlavors {
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.desktop")

    flavorDimensions {
        register("channel") { priority.set(0) }
    }
    flavors {
        register("free") {
            dimension.set("channel")
            isDefault.set(true)
            buildConfigField("String", "UPDATE_URL", "\"https://free-updates.example.com\"")
            buildConfigField("Boolean", "TIER_PRO", "false")
            versionCode.set(1)
            versionName.set("2.8.0-free")
        }
        register("pro") {
            dimension.set("channel")
            buildConfigField("String", "UPDATE_URL", "\"https://pro-updates.example.com\"")
            buildConfigField("Boolean", "TIER_PRO", "true")
            versionCode.set(100)
            versionName.set("2.8.0")
        }
    }
}
