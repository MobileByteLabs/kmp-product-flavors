/*
 * web-flavor-integration — demonstrates KMP JS + WasmJs per-flavor Webpack
 * DefinePlugin constant injection.
 *
 * Run:
 *   ./gradlew :samples:web-flavor-integration:listFlavors
 *   ./gradlew :samples:web-flavor-integration:compileKotlinJs
 */
plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    wasmJs {
        browser()
        binaries.executable()
    }
}

kmpFlavors {
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.web")

    flavorDimensions {
        register("env") { priority.set(0) }
    }
    flavors {
        register("staging") {
            dimension.set("env")
            isDefault.set(true)
            buildConfigField("String", "API_BASE", "\"https://staging.example.com\"")
            buildConfigField("Boolean", "ENABLE_DEBUG_TOOLS", "true")
        }
        register("production") {
            dimension.set("env")
            buildConfigField("String", "API_BASE", "\"https://api.example.com\"")
            buildConfigField("Boolean", "ENABLE_DEBUG_TOOLS", "false")
        }
    }
}
