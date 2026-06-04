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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Phase 12 — auto-wires pbxproj base xcconfig + Info.plist for zero-setup iOS adoption.
 * Idempotent.
 */
abstract class KmpFlavorsBootstrapXcodeTask : DefaultTask() {

    @get:Input
    abstract val xcconfigRelativePath: Property<String>

    @get:OutputFile
    abstract val pbxprojFile: RegularFileProperty

    @get:OutputFile
    abstract val infoPlistFile: RegularFileProperty

    init {
        group = "kmp flavors"
        description = "Auto-wires pbxproj base xcconfig + Info.plist KMPF custom keys for zero-setup iOS adoption."
    }

    @TaskAction
    fun run() {
        val pbxproj = pbxprojFile.get().asFile
        val infoPlist = infoPlistFile.get().asFile
        val xcconfigRel = xcconfigRelativePath.get()

        // ────────── pbxproj ──────────
        if (pbxproj.exists()) {
            val doc = runCatching { PbxprojParser(pbxproj.readText()).parse() }
                .getOrElse {
                    logger.warn("[KMP Flavors] pbxproj parse failed; skipping bootstrap: ${it.message}")
                    return
                }

            val existingRef = doc.objects.values
                .filterIsInstance<PbxObject.PBXFileReference>()
                .firstOrNull { it.path == xcconfigRel }
            val refId = existingRef?.id
                ?: PbxObjectIdGenerator.forFileRef(project.name, xcconfigRel)

            // Step 1: ensure XCBuildConfiguration entries have baseConfigurationReference
            var pbxprojChanged = false
            for ((_, obj) in doc.objects) {
                if (obj is PbxObject.XCBuildConfiguration && obj.baseConfigurationReference != refId) {
                    obj.baseConfigurationReference = refId
                    obj.raw["baseConfigurationReference"] = refId
                    pbxprojChanged = true
                }
            }

            // Step 2: if no PBXFileReference exists for the xcconfig, create it
            if (existingRef == null) {
                doc.objects[refId] = PbxObject.PBXFileReference(
                    id = refId,
                    raw = mutableMapOf(
                        "isa" to "PBXFileReference",
                        "lastKnownFileType" to "text.xcconfig",
                        "path" to xcconfigRel,
                        "sourceTree" to "<group>",
                    ),
                    annotation = "iOSApp.xcconfig",
                    path = xcconfigRel,
                    sourceTree = "<group>",
                )
                pbxprojChanged = true
            }

            if (pbxprojChanged) {
                writeAtomic(pbxproj, PbxprojWriter(doc).write())
                logger.lifecycle("[KMP Flavors] bootstrap: updated ${pbxproj.name}")
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
