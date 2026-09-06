/*
 * spm-distribution — END-TO-END Swift Package Manager distribution.
 *
 * This is the proving ground for the v2.9 SPM path. It is the only sample that wires all
 * three pieces a real iOS consumer needs, which is why it exists: `docs/IOS_DISTRIBUTION.md`
 * promised `samples/spm-distribution/` from v1.1.1 and it was never shipped, so the
 * generated manifests had no sample proving they resolve.
 *
 *   1. an XCFramework PRODUCER — KGP's own `XCFramework()` aggregator
 *   2. the generated `Package.swift` pointing at that producer's output
 *   3. the generated flavor-aware Xcode Run-Script that assembles + stages the slice
 *
 * NOTE ON COCOAPODS: there is none, and none is needed. SPM is the default and the only
 * supported path for distributing the KMP framework itself.
 *
 * Run:
 *   ./gradlew :samples:spm-distribution:generateSpmManifest
 *   ./gradlew :samples:spm-distribution:generateSpmEmbedScript
 *   ./gradlew :samples:spm-distribution:assembleSharedReleaseXCFramework   # macOS only
 */
import com.mobilebytelabs.kmpflavors.SpmDistribution
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

kotlin {
    // The XCFramework aggregator. Without a producer like this the plugin refuses to
    // emit a manifest (spm.requireXcframework) rather than writing a `binaryTarget`
    // whose path nothing builds.
    val xcf = XCFramework("Shared")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
        }
    }
}

kmpFlavors {
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.spm")

    // Build types drive the Xcode-configuration → Kotlin-build-type mapping baked into
    // the generated embed script. `staging` is deliberately DEBUGGABLE despite not being
    // named "*Debug" — that is the case a hand-written `*Debug` glob gets wrong.
    enableBuildTypes.set(true)
    buildTypes {
        register("debug") { isDebuggable.set(true) }
        register("staging") { isDebuggable.set(true) }
        register("release") { isDebuggable.set(false) }
    }

    flavorDimensions {
        register("tier") { priority.set(0) }
    }
    flavors {
        register("free") {
            dimension.set("tier")
            isDefault.set(true)
            buildConfigField("Boolean", "TIER_PAID", "false")
        }
        register("paid") {
            dimension.set("tier")
            buildConfigField("Boolean", "TIER_PAID", "true")
        }
    }

    spm {
        // generateManifest is TRUE by default since v2.9 — set explicitly here only to
        // document the default at the point a reader is looking for it.
        generateManifest.set(true)
        xcframeworkName.set("Shared")
        distribution.set(SpmDistribution.LOCAL)

        // A real consumer keeps its Xcode project at the repo root (`cmp-ios/`), which is
        // where the script lands by default. This is a sample inside the plugin's own
        // multi-project build, so keep the generated script within the sample.
        embedScriptPath.set("samples/spm-distribution/cmp-ios/scripts/embed-xcframework.sh")
    }
}
