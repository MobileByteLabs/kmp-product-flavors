/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig

/**
 * Phase 6 — typed model fed into the 6 runtime API template renderers.
 * Note: the emitted source files are VARIANT-AGNOSTIC at codegen time — they read
 * platform-native sources at compile/runtime to get the per-variant values.
 */
internal data class RuntimeApiSpec(val packageName: String)

internal data class RuntimeVariantHint(
    val flavorName: String,
    val buildTypeName: String,
    val bundleId: String,
    val appDisplayName: String,
    val appVersion: String,
    val isDemo: Boolean,
    val isDebug: Boolean,
) {
    companion object {
        /**
         * @param baseBundleId    the project's base application id (e.g. `org.mifos.kmp.template`).
         * @param appDisplayName  the app display name (typically `rootProject.name`).
         * @param baseAppVersion  the project version string (typically `project.version`).
         *
         * `bundleId` resolves to `baseBundleId` + the active flavor's id suffix (iOS
         * `bundleIdSuffix`, else Android `applicationIdSuffix`) and `appVersion` appends
         * the flavor's `versionNameSuffix` — the same resolution IosXcconfigSpec uses, so
         * the runtime object reports the active variant's real identity, never a stub.
         */
        fun from(flavor: FlavorConfig, buildType: BuildTypeConfig, baseBundleId: String, appDisplayName: String, baseAppVersion: String): RuntimeVariantHint {
            val isDemoField =
                (flavor.buildConfigFields.orNull?.get("IS_DEMO")?.value == "true") || (flavor.name == "demo")
            val idSuffix = flavor.bundleIdSuffix.orNull ?: flavor.applicationIdSuffix.orNull ?: ""
            val versionSuffix = flavor.versionNameSuffix.orNull ?: ""
            return RuntimeVariantHint(
                flavorName = flavor.name,
                buildTypeName = buildType.name,
                bundleId = baseBundleId + idSuffix,
                appDisplayName = appDisplayName,
                appVersion = baseAppVersion + versionSuffix,
                isDemo = isDemoField,
                isDebug = (buildType.isDebuggable.orNull == true),
            )
        }
    }
}
