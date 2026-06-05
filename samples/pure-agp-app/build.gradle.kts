/*
 * pure-agp-app — demonstrates the plugin applied to a pure `com.android.library`
 * module (no KMP plugin required). Shows that signingConfigs {} and per-flavor
 * versionCode/Name work in a non-KMP Android project.
 *
 * Run:
 *   ./gradlew :samples:pure-agp-app:listFlavors
 *   ./gradlew :samples:pure-agp-app:validateFlavors
 */
plugins {
    id("com.android.library")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

android {
    namespace = "com.example.pureagp"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}

kmpFlavors {
    flavorDimensions {
        register("contentType") { priority.set(0) }
    }
    flavors {
        register("demo") {
            dimension.set("contentType")
            isDefault.set(true)
            applicationIdSuffix.set(".demo")
            versionCode.set(1)
            versionName.set("0.1.0-demo")
        }
        register("prod") {
            dimension.set("contentType")
            versionCode.set(100)
            versionName.set("2.8.0")
        }
    }
}
