/*
 * runtime-api-integration — demonstrates KmpFlavorsRuntime.activeFlavor and
 * related runtime properties consumed from commonMain via the expect/actual
 * codegen introduced in Phase 6 (v2.8).
 *
 * Run:
 *   ./gradlew :samples:runtime-api-integration:compileKotlinDesktop
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
}

kmpFlavors {
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.runtime")
    createIntermediateSourceSets.set(true)

    flavorDimensions {
        register("env") { priority.set(0) }
    }
    flavors {
        register("demo") {
            dimension.set("env")
            isDefault.set(true)
            buildConfigField("String", "ENV_NAME", "\"demo\"")
        }
        register("prod") {
            dimension.set("env")
            buildConfigField("String", "ENV_NAME", "\"production\"")
        }
    }
}
