/*
 * ios-flavor-integration — demonstrates per-flavor iOS xcconfig generation via
 * `:kmpFlavorsXcodeIntegrate`. Shows iOS-side KmpFlavorsRuntime expect/actual
 * codegen and per-flavor source sets wiring.
 *
 * Run:
 *   ./gradlew :samples:ios-flavor-integration:listFlavors
 *   ./gradlew :samples:ios-flavor-integration:compileKotlinIosSimulatorArm64
 */
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

kmpFlavors {
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.ios")
    createIntermediateSourceSets.set(true)

    flavorDimensions {
        register("environment") { priority.set(0) }
    }
    flavors {
        register("demo") {
            dimension.set("environment")
            isDefault.set(true)
            buildConfigField("String", "API_URL", "\"https://demo.example.com\"")
            buildConfigField("Boolean", "ENV_DEMO", "true")
        }
        register("prod") {
            dimension.set("environment")
            buildConfigField("String", "API_URL", "\"https://api.example.com\"")
            buildConfigField("Boolean", "ENV_DEMO", "false")
        }
    }
}
