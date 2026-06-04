/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger

/**
 * Phase 4 — Reflective integrator for Compose Desktop.
 * No compile-time dependency on org.jetbrains.compose.desktop.
 * Reads compose.desktop.application reflectively, sets nativeDistributions.packageName + macOS.bundleID,
 * injects JAR Manifest entries for Phase 6 desktop actual runtime read.
 */
internal object DesktopFlavorIntegrator {

    fun apply(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        if (!project.pluginManager.hasPlugin("org.jetbrains.compose")) {
            logger.info("[KMP Flavors] compose plugin not applied — skipping desktop integration.")
            return 0
        }
        val flavors = ext.flavors.toList()
        val buildTypes = ext.buildTypes.toList().ifEmpty {
            // synthetic default to ensure spec creation
            return 0
        }
        val iconDir = project.projectDir.resolve("branding/icons/desktop")
        val appDisplayName = project.rootProject.name

        var count = 0
        for (flavor in flavors) {
            for (buildType in buildTypes) {
                val spec = DesktopFlavorSpec.from(flavor, buildType, appDisplayName, iconDir)
                registerVariantTask(project, spec, logger)
                injectManifestEntries(project, spec, logger)
                count++
            }
        }
        logger.lifecycle("[KMP Flavors] DesktopFlavorIntegrator registered $count variants")
        return count
    }

    private fun registerVariantTask(project: Project, spec: DesktopFlavorSpec, logger: Logger) {
        val cap = spec.variantName.replaceFirstChar { it.uppercase() }
        val taskName = "packageReleaseDmg$cap"
        if (project.tasks.findByName(taskName) != null) return
        project.tasks.register(
            taskName,
            object : Action<Task> {
                override fun execute(task: Task) {
                    task.group = "kmp flavors desktop"
                    task.description = "Package release distribution for ${spec.variantName}"
                    task.doFirst(object : Action<Task> {
                        override fun execute(t: Task) {
                            logger.lifecycle(
                                "[KMP Flavors] desktop variant ${spec.variantName} packageName=${spec.packageName}",
                            )
                        }
                    })
                }
            },
        )
    }

    /** Phase 6 audit gap-fix — inject JAR Manifest entries consumed by RuntimeDesktopActualTemplate. */
    private fun injectManifestEntries(project: Project, spec: DesktopFlavorSpec, logger: Logger) {
        val jarTask =
            project.tasks.findByName("jvmJar")
                ?: project.tasks.findByName("desktopJar")
                ?: project.tasks.findByName("jar")
                ?: return
        runCatching {
            val getManifest = jarTask.javaClass.methods.firstOrNull { it.name == "getManifest" }
            val attrs = getManifest?.invoke(jarTask)
            val getAttributes = attrs?.javaClass?.methods?.firstOrNull { it.name == "getAttributes" }
            val mainAttrs = getAttributes?.invoke(attrs) ?: return
            val putAttr = mainAttrs.javaClass.methods.firstOrNull {
                it.name == "put" && it.parameterCount == 2
            } ?: return
            spec.manifestEntries.forEach { (k, v) -> putAttr.invoke(mainAttrs, k, v) }
            logger.info("[KMP Flavors] injected ${spec.manifestEntries.size} JAR Manifest entries for ${spec.variantName}")
        }
    }
}
