/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.BuildConfigField
import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension

/**
 * Phase 2 — typed model of per-variant xcconfig payload.
 * Includes KMPF_* runtime identity vars consumed by Phase 6's iOS actual.
 */
internal data class IosXcconfigSpec(
    val variantName: String,
    val bundleId: String,
    val xcconfigVars: Map<String, String>,
    val googleServiceFile: String? = null,
) {
    companion object {
        fun from(
            flavor: FlavorConfig,
            buildType: BuildTypeConfig,
            baseBundleId: String,
            appDisplayName: String,
            appVersion: String,
            @Suppress("UNUSED_PARAMETER") ext: KmpFlavorExtension,
        ): IosXcconfigSpec {
            val suffix = flavor.bundleIdSuffix.orNull
                ?: flavor.applicationIdSuffix.orNull
                ?: ".${flavor.name}"
            val resolvedBundleId = baseBundleId + suffix
            val isDemoField =
                (flavor.buildConfigFields.orNull?.get("IS_DEMO")?.value == "true") || (flavor.name == "demo")
            val isDebugField = (buildType.isDebuggable.orNull == true)
            val vars = buildMap<String, String> {
                // Runtime identity vars — populate Info.plist KMPF* keys at build time
                put("KMPF_FLAVOR_NAME", flavor.name)
                put("KMPF_BUILD_TYPE_NAME", buildType.name)
                put("KMPF_BUNDLE_ID", resolvedBundleId)
                put("KMPF_APP_DISPLAY_NAME", appDisplayName)
                put("KMPF_APP_VERSION", appVersion)
                put("KMPF_IS_DEBUG", if (isDebugField) "YES" else "NO")
                put("KMPF_IS_DEMO", if (isDemoField) "YES" else "NO")
                // Consumer-declared buildConfigFields → KMPF_<NAME>
                flavor.buildConfigFields.orNull?.forEach { (k, v) -> put("KMPF_$k", xcconfigValue(v)) }
                buildType.buildConfigFields.orNull?.forEach { (k, v) -> put("KMPF_$k", xcconfigValue(v)) }
            }
            return IosXcconfigSpec(
                variantName = "${flavor.name}${buildType.name.replaceFirstChar { it.uppercase() }}",
                bundleId = resolvedBundleId,
                xcconfigVars = vars,
                googleServiceFile = null,
            )
        }

        private fun xcconfigValue(field: BuildConfigField): String = when (field.type) {
            "Boolean" -> if (field.value == "true") "YES" else "NO"
            "String" -> field.value.trim('"')
            else -> field.value
        }
    }
}
