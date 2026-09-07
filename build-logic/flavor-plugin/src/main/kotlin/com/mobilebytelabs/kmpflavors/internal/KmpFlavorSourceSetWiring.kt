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
        // v2.9 — do NOT create standalone `{F}Main` source sets any more.
        //
        // They were created here, given srcDirs and wired `dependsOn(commonMain)`, but
        // NOTHING ever depended on them, so `src/{flavor}Main/` was silently never
        // compiled despite being documented in CONSUMER_GUIDE.md / SOURCE_SET_DISCIPLINE.md
        // (FlavorMainSourceSetLivenessTest is the canary). The orphans also appeared in
        // KGP's cross-tree diagnostics under a `'null' Tree`.
        //
        // The directories are now folded into the source sets that are actually compiled:
        // `common{Flavor}` for the active flavor (SourceSetConfigurator) and
        // `{variant}VariantMain` for matrix variants (KmpFlavorPlugin).

        // No dependsOn edges are wired here any more — see the note above. Wiring an
        // orphan `{F}Main -> commonMain` edge is precisely what produced
        //   w: ⚠️ Invalid Source Set Dependency Across Trees … Source Sets from 'null' Tree
        // because the child belonged to no compilation at all.

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
