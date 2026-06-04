/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Phase 8 — ensures src/{F}/res/ is wired into AGP's per-flavor source set.
 * AGP auto-creates these once productFlavors are registered (Phase 1's AgpBridge handles).
 * This router defensively adds the srcDir if missing.
 */
internal object AndroidResPerFlavorRouter {

    fun apply(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        val androidExt = project.extensions.findByName("android") ?: return 0
        var count = 0
        for (flavor in ext.flavors) {
            runCatching {
                val sourceSets = androidExt.javaClass.methods.firstOrNull { it.name == "getSourceSets" }
                    ?.invoke(androidExt) ?: return@runCatching
                val getByName = sourceSets.javaClass.methods.firstOrNull {
                    it.name == "findByName" && it.parameterCount == 1
                } ?: return@runCatching
                val ss = getByName.invoke(sourceSets, flavor.name) ?: return@runCatching
                val getRes = ss.javaClass.methods.firstOrNull { it.name == "getRes" }
                val res = getRes?.invoke(ss) ?: return@runCatching
                val srcDir = res.javaClass.methods.firstOrNull {
                    it.name == "srcDir" && it.parameterCount == 1
                } ?: return@runCatching
                srcDir.invoke(res, project.file("src/${flavor.name}/res"))
                count++
                logger.info("[KMP Flavors] added Android res srcDir for ${flavor.name}")
            }
        }
        if (count > 0) logger.lifecycle("[KMP Flavors] AndroidResPerFlavorRouter wired $count flavors")
        return count
    }
}
