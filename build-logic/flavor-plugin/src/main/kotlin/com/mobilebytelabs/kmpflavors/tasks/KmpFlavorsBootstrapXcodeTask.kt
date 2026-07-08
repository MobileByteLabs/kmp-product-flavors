/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.tasks

import com.mobilebytelabs.kmpflavors.internal.InfoPlistUpdater
import com.mobilebytelabs.kmpflavors.internal.pbxproj.PbxObject
import com.mobilebytelabs.kmpflavors.internal.pbxproj.PbxObjectIdGenerator
import com.mobilebytelabs.kmpflavors.internal.pbxproj.PbxprojParser
import com.mobilebytelabs.kmpflavors.internal.pbxproj.PbxprojWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Auto-wires the iOS `project.pbxproj` so each product-flavor Xcode build configuration
 * uses its OWN per-variant xcconfig as its base configuration (`Configs/{variant}.xcconfig`,
 * produced by [GenerateIosFlavorXcconfigsTask]).
 *
 * This deliberately does NOT emit a `$(CONFIGURATION)` umbrella `#include` — Xcode does not
 * expand build settings inside an xcconfig `#include`, so the umbrella never loaded the
 * per-variant values. Wiring each config to its own file directly is the working shape.
 *
 * Idempotent. Run once per consumer to bootstrap the project; re-running is a no-op when the
 * base configs already point at the per-variant files. (The generated per-variant xcconfigs
 * `#include` the consumer's identity config, so repointing any matching build configuration
 * is safe regardless of whether it is a project- or target-level configuration.)
 */
abstract class KmpFlavorsBootstrapXcodeTask : DefaultTask() {

    /** The flavor x build-type variant names whose Xcode build configs get per-variant base configs. */
    @get:Input
    abstract val variantNames: SetProperty<String>

    /** Last path segment of the Configs dir — the PBXGroup the file references are added to. Default: `Configs`. */
    @get:Input
    abstract val configsGroupName: Property<String>

    /** Registering project name — deterministic seed for generated pbxproj object ids. */
    @get:Input
    abstract val projectName: Property<String>

    @get:OutputFile
    abstract val pbxprojFile: RegularFileProperty

    @get:OutputFile
    abstract val infoPlistFile: RegularFileProperty

    init {
        group = "kmp-flavors"
        description = "Wire each product-flavor Xcode build config to its own Configs/{variant}.xcconfig base config."
    }

    @TaskAction
    fun run() {
        val pbxproj = pbxprojFile.get().asFile
        val infoPlist = infoPlistFile.get().asFile
        val variants = variantNames.get()
        val groupName = configsGroupName.get()
        val seed = projectName.get()

        // ────────── pbxproj ──────────
        if (pbxproj.exists()) {
            val doc = runCatching { PbxprojParser(pbxproj.readText()).parse() }
                .getOrElse {
                    logger.warn("[KMP Flavors] pbxproj parse failed; skipping bootstrap: ${it.message}")
                    return
                }

            val configsGroup = doc.objects.values
                .filterIsInstance<PbxObject.PBXGroup>()
                .firstOrNull { it.path == groupName }

            var changed = false
            for (variant in variants) {
                val relPath = "$variant.xcconfig"

                val existingRef = doc.objects.values
                    .filterIsInstance<PbxObject.PBXFileReference>()
                    .firstOrNull { it.path == relPath }
                val refId = existingRef?.id ?: PbxObjectIdGenerator.forFileRef(seed, relPath)

                if (existingRef == null) {
                    doc.objects[refId] = PbxObject.PBXFileReference(
                        id = refId,
                        raw = linkedMapOf(
                            "isa" to "PBXFileReference",
                            "fileEncoding" to "4",
                            "lastKnownFileType" to "text.xcconfig",
                            "path" to relPath,
                            "sourceTree" to "<group>",
                        ),
                        annotation = relPath,
                        path = relPath,
                        sourceTree = "<group>",
                    )
                    changed = true
                }

                // Ensure the file reference is a child of the Configs group.
                if (configsGroup != null && refId !in configsGroup.children) {
                    configsGroup.children.add(refId)
                    configsGroup.raw["children"] = configsGroup.children
                    changed = true
                }

                // Repoint every matching build configuration at its own per-variant file.
                for (obj in doc.objects.values) {
                    if (obj is PbxObject.XCBuildConfiguration &&
                        obj.name == variant &&
                        obj.baseConfigurationReference != refId
                    ) {
                        obj.baseConfigurationReference = refId
                        obj.raw["baseConfigurationReference"] = refId
                        changed = true
                    }
                }
            }

            if (changed) {
                writeAtomic(pbxproj, PbxprojWriter(doc).write())
                logger.lifecycle("[KMP Flavors] bootstrap: wired ${variants.size} per-variant base configs in ${pbxproj.name}")
            } else {
                logger.info("[KMP Flavors] bootstrap: ${pbxproj.name} already wired")
            }
        }

        // ────────── Info.plist ──────────
        if (infoPlist.exists()) {
            val bundleChanged = InfoPlistUpdater.setBundleIdInterpolation(infoPlist)
            val kmpfChanged = InfoPlistUpdater.ensureKmpfKeys(infoPlist)
            if (bundleChanged || kmpfChanged) {
                logger.lifecycle("[KMP Flavors] bootstrap: updated ${infoPlist.name}")
            } else {
                logger.info("[KMP Flavors] bootstrap: ${infoPlist.name} already wired")
            }
        }
    }

    private fun writeAtomic(file: File, content: String) {
        val tempFile = File.createTempFile("pbxproj-", ".tmp", file.parentFile)
        tempFile.writeText(content)
        Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
