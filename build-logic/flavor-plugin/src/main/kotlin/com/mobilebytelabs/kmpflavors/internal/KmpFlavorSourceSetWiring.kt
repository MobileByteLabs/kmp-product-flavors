/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/**
 * Phase 7 — creates per-flavor and per-(target × flavor) KMP source sets.
 * Uses early plugin-application hook so KGP's hierarchy template sees them.
 */
internal object KmpFlavorSourceSetWiring {

    fun apply(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        val kmp = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            ?: run {
                logger.info("[KMP Flavors] KMP not applied; skipping source set wiring")
                return 0
            }

        val flavors = ext.flavors.toList().map { it.name }
        if (flavors.isEmpty()) return 0
        val targets = kmp.targets.mapNotNull { target ->
            when (target.platformType.name.lowercase()) {
                "common", "metadata" -> null
                else -> target.name
            }
        }.toSet()

        val hierarchy = SourceSetHierarchy.from(flavors, targets)

        // Create per-flavor cross-cutting source sets only ({F}Main).
        // Per-(target × flavor) source sets cannot dependOn the default compilation
        // source set ({T}Main) — KGP enforces this. Consumers who want per-target-
        // per-flavor code should place it inside {F}Main with platform-conditional
        // logic, or use a KMP intermediate source set.
        hierarchy.flavorSourceSets.forEach { name ->
            kmp.sourceSets.maybeCreate(name)
        }

        // Wire dependsOn edges only for flavor sets → commonMain
        hierarchy.dependsOnEdges
            .filter { (_, parent) -> parent == "commonMain" }
            .forEach { (child, parent) ->
                val parentSs = kmp.sourceSets.findByName(parent)
                val childSs = kmp.sourceSets.findByName(child)
                if (parentSs != null && childSs != null) {
                    try {
                        childSs.dependsOn(parentSs)
                    } catch (e: Exception) {
                        logger.info("[KMP Flavors] skipped dependsOn $child -> $parent: ${e.message}")
                    }
                }
            }

        // Register source set src dirs (Kotlin) — only for the {F}Main sets we created
        val ourSetNames = hierarchy.flavorSourceSets.toSet()
        kmp.sourceSets.configureEach(object : Action<KotlinSourceSet> {
            override fun execute(ss: KotlinSourceSet) {
                if (ss.name in ourSetNames) {
                    ss.kotlin.srcDir("src/${ss.name}/kotlin")
                    ss.resources.srcDir("src/${ss.name}/resources")
                }
            }
        })

        logger.lifecycle(
            "[KMP Flavors] wired ${hierarchy.flavorSourceSets.size + hierarchy.perTargetPerFlavor.size} per-flavor source sets " +
                "(${hierarchy.flavorSourceSets.size} flavor + ${hierarchy.perTargetPerFlavor.size} per-target-flavor)",
        )
        return hierarchy.flavorSourceSets.size + hierarchy.perTargetPerFlavor.size
    }
}
