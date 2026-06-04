/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Phase 9 — copies per-flavor google-services.json from branding/firebase/{F}/ to src/{F}/.
 * No-op when branding/firebase/ missing (Firebase is opt-in).
 */
internal object AndroidFirebaseFlavorRouter {

    fun apply(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        val brandingDir = project.projectDir.resolve("branding/firebase")
        if (!brandingDir.exists()) {
            logger.info("[KMP Flavors] no branding/firebase/ — skipping Firebase Android router")
            return 0
        }
        var count = 0
        for (flavor in ext.flavors) {
            val src = brandingDir.resolve("${flavor.name}/google-services.json")
            if (!src.exists()) {
                logger.warn("[KMP Flavors] missing branding/firebase/${flavor.name}/google-services.json")
                continue
            }
            val dst = project.projectDir.resolve("src/${flavor.name}/google-services.json")
            dst.parentFile.mkdirs()
            src.copyTo(dst, overwrite = true)
            count++
            logger.lifecycle("[KMP Flavors] copied google-services.json for ${flavor.name}")
        }
        return count
    }
}
