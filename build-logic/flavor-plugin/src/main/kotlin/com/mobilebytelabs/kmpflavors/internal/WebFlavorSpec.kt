/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import java.io.File

/**
 * Phase 5 — typed model of per-variant web bundling payload.
 */
internal data class WebFlavorSpec(
    val variantName: String,
    val flavorName: String,
    val buildTypeName: String,
    val title: String,
    val favicon: File?,
    val themeColor: String?,
    val manifestFile: File?,
    val outputDir: String,
    val bundleId: String,
    val appDisplayName: String,
    val isDebug: Boolean,
    val isDemo: Boolean,
) {
    companion object {
        fun from(
            flavor: FlavorConfig,
            buildType: BuildTypeConfig,
            appDisplayName: String,
            bundleIdBase: String,
            iconDir: File,
        ): WebFlavorSpec {
            val title = flavor.webTitleSuffix.orNull?.let { "$appDisplayName$it" }
                ?: "$appDisplayName (${flavor.name})"
            val favicon = iconDir.resolve("${flavor.name}.favicon.ico").takeIf { it.exists() }
            val manifest = iconDir.resolve("${flavor.name}.manifest.json").takeIf { it.exists() }
            val variantName = "${flavor.name}${buildType.name.replaceFirstChar { it.uppercase() }}"
            val isDemoField =
                (flavor.buildConfigFields.orNull?.get("IS_DEMO")?.value == "true") || (flavor.name == "demo")
            val bundleId = bundleIdBase + (flavor.bundleIdSuffix.orNull ?: flavor.applicationIdSuffix.orNull ?: ".${flavor.name}")
            return WebFlavorSpec(
                variantName = variantName,
                flavorName = flavor.name,
                buildTypeName = buildType.name,
                title = title,
                favicon = favicon,
                themeColor = null,
                manifestFile = manifest,
                outputDir = variantName,
                bundleId = bundleId,
                appDisplayName = appDisplayName,
                isDebug = (buildType.isDebuggable.orNull == true),
                isDemo = isDemoField,
            )
        }
    }
}
