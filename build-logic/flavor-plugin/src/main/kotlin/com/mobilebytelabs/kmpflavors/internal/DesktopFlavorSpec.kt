/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import java.io.File

/**
 * Phase 4 — typed model of per-variant Compose Desktop packaging payload.
 */
internal data class DesktopFlavorSpec(
    val variantName: String,
    val flavorName: String,
    val buildTypeName: String,
    val packageName: String,
    val macOsBundleId: String?,
    val iconFileByOs: Map<String, File>,
    val manifestEntries: Map<String, String>,
) {
    companion object {
        fun from(flavor: FlavorConfig, buildType: BuildTypeConfig, appDisplayName: String, iconDir: File): DesktopFlavorSpec {
            val name = flavor.desktopWindowTitleSuffix.orNull?.let { "$appDisplayName$it" }
                ?: "$appDisplayName-${flavor.name}"
            val bundle = flavor.bundleIdSuffix.orNull?.let { suffix ->
                appDisplayName.lowercase().replace(" ", "") + suffix
            }
            val icons = mapOf(
                "macos" to iconDir.resolve("${flavor.name}.icns"),
                "windows" to iconDir.resolve("${flavor.name}.ico"),
                "linux" to iconDir.resolve("${flavor.name}.png"),
            ).filterValues { it.exists() }
            val variantName = "${flavor.name}${buildType.name.replaceFirstChar { it.uppercase() }}"
            val manifest = mapOf(
                "KMPF-Flavor" to flavor.name,
                "KMPF-BuildType" to buildType.name,
                "KMPF-BundleId" to (bundle ?: name),
                "KMPF-AppDisplayName" to name,
                "KMPF-IsDemo" to (flavor.name == "demo").toString(),
                "KMPF-IsDebug" to (buildType.isDebuggable.orNull == true).toString(),
            )
            return DesktopFlavorSpec(
                variantName = variantName,
                flavorName = flavor.name,
                buildTypeName = buildType.name,
                packageName = name,
                macOsBundleId = bundle,
                iconFileByOs = icons,
                manifestEntries = manifest,
            )
        }
    }
}
