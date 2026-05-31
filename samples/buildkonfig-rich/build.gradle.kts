/*
 * Copyright 2026 MobileByteLabs
 *
 * v2.5 — Canonical sample exercising all four `buildKonfig {}` DSL features
 * end-to-end:
 *
 *   1. secret(id)              — vault-integrated per-flavor secret
 *                                (placeholder emission in v2.5; real wiring v2.5.x)
 *   2. enum(dimension)         — auto-generated sealed-class dimension enum
 *   3. customField(name, type, value) — sealed-class + List<T> custom fields
 *   4. perTarget(name) { ... } — per-target conditional codegen as nested object
 *
 * Run:
 *   ./gradlew :samples:buildkonfig-rich:build
 *   cat samples/buildkonfig-rich/build/generated/kmpFlavors/freeDevPhone/kotlin/com/example/buildkonfigrich/BuildKonfig.kt
 *
 * See docs/SECRETS_INTEGRATION.md for the consumer contract that vault-integrated
 * secrets honor (schema v2.1+ requirement).
 */

plugins {
    kotlin("multiplatform")
    id("io.github.mobilebytelabs.kmp-product-flavors")
}

group = "io.github.mobilebytelabs.samples"
version = "2.5.0-alpha.1"

kmpFlavors {
    buildMatrix.set(true)
    generateBuildConfig.set(true)
    buildConfigPackage.set("com.example.buildkonfigrich")
    buildConfigClassName.set("BuildKonfig")

    // v2.5 dimensions {} DSL sugar — see samples/multi-dim-3d for full discussion.
    dimensions {
        dimension("tier") {
            flavor("free") { isDefault.set(true) }
            flavor("paid")
        }
        dimension("env") {
            flavor("dev") { isDefault.set(true) }
            flavor("prod")
        }
    }

    // v2.5 BuildKonfig DSL — all four features in one sample.
    buildKonfig {
        // (1) Vault-integrated secrets (placeholder emission per v2.5 scope decision).
        // Real value flow requires consumer's secrets-manifest.yaml schema v2.1+ and
        // is gated by FrameworkSchemaCheckTask (emits KMPF-V26 WARN on schema v2.0).
        // See docs/SECRETS_INTEGRATION.md.
        secret("api-key")

        // (2) Dimension enum — emits `sealed class Tier { Free; Paid }` + active val.
        enum("tier")
        enum("env")

        // (3) Custom-type fields — sealed-class type reference + List<T>.
        customField(
            name = "scopes",
            typeDescriptor = "List<String>",
            value = "listOf(\"read\", \"write\")",
        )

        // (4) Per-target conditional codegen. v2.5 emits a nested object
        // `PerTarget.IosMain { ... }` — consumer code accesses via
        // `BuildKonfig.PerTarget.IosMain.BUNDLE_ID_SUFFIX`.
        perTarget("iosMain") {
            field("BUNDLE_ID_SUFFIX", "String", "\".dev\"")
        }
    }
}

kotlin {
    jvm("desktop")
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}
