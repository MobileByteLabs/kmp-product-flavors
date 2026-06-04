/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Phase 8 — routes composeResources/{flavor}/ into per-flavor source set's resource srcDirs.
 * Compose Resources' native precedence (flavor overrides default) takes care of resolution.
 */
internal object ComposeResourcesPerFlavorRouter {

    fun apply(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        if (!project.pluginManager.hasPlugin("org.jetbrains.compose")) {
            logger.info("[KMP Flavors] compose plugin not applied — skipping composeResources routing.")
            return 0
        }
        val kmp = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            ?: return 0
        var count = 0
        for (flavor in ext.flavors) {
            val ssName = "${flavor.name}Main"
            val ss = kmp.sourceSets.findByName(ssName)
                ?: run {
                    logger.info("[KMP Flavors] $ssName not found — Phase 7 source set wiring missing?")
                    continue
                }
            ss.resources.srcDir(project.projectDir.resolve("src/$ssName/composeResources"))
            count++
        }
        logger.lifecycle("[KMP Flavors] ComposeResourcesPerFlavorRouter wired $count flavors")
        return count
    }
}
