/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Phase 9 — appends KMPF_FIREBASE_CONFIG_FILE to each per-variant xcconfig (Phase 2 output).
 * Consumer's iOS Run Script reads $KMPF_FIREBASE_CONFIG_FILE to copy the correct plist.
 */
internal object IosFirebaseFlavorRouter {

    fun apply(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        val brandingDir = project.projectDir.resolve("branding/firebase")
        if (!brandingDir.exists()) {
            logger.info("[KMP Flavors] no branding/firebase/ — skipping Firebase iOS router")
            return 0
        }
        val xcconfigDir = project.layout.buildDirectory.dir("generated/iosFlavorConfigs").get().asFile
        if (!xcconfigDir.exists()) {
            logger.info("[KMP Flavors] no iosFlavorConfigs dir — Phase 2 not run yet")
            return 0
        }
        val flavors = ext.flavors.toList()
        val buildTypes = ext.buildTypes.toList()
        var count = 0
        for (flavor in flavors) for (buildType in buildTypes) {
            val plist = brandingDir.resolve("${flavor.name}/GoogleService-Info.plist")
            if (!plist.exists()) {
                logger.warn(
                    "[KMP Flavors] missing branding/firebase/${flavor.name}/GoogleService-Info.plist"
                )
                continue
            }
            val variantName = "${flavor.name}${buildType.name.replaceFirstChar { it.uppercase() }}"
            val xcconfig = xcconfigDir.resolve("$variantName.xcconfig")
            if (!xcconfig.exists()) {
                logger.warn("[KMP Flavors] xcconfig $variantName missing — Phase 2 not generated")
                continue
            }
            val line =
                "\nKMPF_FIREBASE_CONFIG_FILE = branding/firebase/${flavor.name}/GoogleService-Info.plist\n"
            if (!xcconfig.readText().contains("KMPF_FIREBASE_CONFIG_FILE")) {
                xcconfig.appendText(line)
                count++
                logger.info("[KMP Flavors] injected KMPF_FIREBASE_CONFIG_FILE into ${xcconfig.name}")
            }
        }
        return count
    }
}
