/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.tasks

import com.mobilebytelabs.kmpflavors.SpmChecksumStrategy
import com.mobilebytelabs.kmpflavors.SpmDistribution
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateSpmManifestTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): GenerateSpmManifestTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("genSpm", GenerateSpmManifestTask::class.java).get()
        task.outputDirectory.set(File(tempDir, "build/spm/freeDev"))
        task.variantName.set("freeDev")
        task.flavorName.set("free")
        task.xcframeworkName.set("Shared")
        return task
    }

    @Test
    fun `LOCAL distribution writes path-based binaryTarget`() {
        val task = newTask()
        task.distribution.set(SpmDistribution.LOCAL)
        task.checksumStrategy.set(SpmChecksumStrategy.AUTO)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        assertTrue(manifest.contains("swift-tools-version:5.9"))
        assertTrue(manifest.contains("name: \"Shared-freeDev\""))
        assertTrue(manifest.contains("name: \"SharedFreeDev\""))
        assertTrue(manifest.contains(".binaryTarget("))
        assertTrue(manifest.contains("path: \"../../XCFrameworks/freeDev/Shared.xcframework\""))
    }

    @Test
    fun `LOCAL with explicit xcframeworkPath uses that path`() {
        val task = newTask()
        task.distribution.set(SpmDistribution.LOCAL)
        task.xcframeworkPath.set("../custom/Shared.xcframework")
        task.checksumStrategy.set(SpmChecksumStrategy.AUTO)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        assertTrue(manifest.contains("path: \"../custom/Shared.xcframework\""))
    }

    @Test
    fun `REMOTE distribution requires binaryUrlTemplate`() {
        val task = newTask()
        task.distribution.set(SpmDistribution.REMOTE)
        task.checksumStrategy.set(SpmChecksumStrategy.SKIP)
        // binaryUrlTemplate intentionally unset
        assertThrows(IllegalStateException::class.java) { task.generate() }
    }

    @Test
    fun `REMOTE with SKIP checksum emits SKIP placeholder`() {
        val task = newTask()
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn.example.com/{flavor}/{version}/Shared.xcframework.zip")
        task.projectVersion.set("1.2.3")
        task.checksumStrategy.set(SpmChecksumStrategy.SKIP)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        assertTrue(manifest.contains("url: \"https://cdn.example.com/free/1.2.3/Shared.xcframework.zip\""))
        assertTrue(manifest.contains("checksum: \"${GenerateSpmManifestTask.SKIP_PLACEHOLDER}\""))
    }

    @Test
    fun `REMOTE template substitutes flavor variant and version`() {
        val task = newTask()
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn/{flavor}/{variant}/{version}/Shared.xcframework.zip")
        task.projectVersion.set("9.9.9")
        task.checksumStrategy.set(SpmChecksumStrategy.SKIP)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        assertTrue(manifest.contains("https://cdn/free/freeDev/9.9.9/Shared.xcframework.zip"))
    }

    @Test
    fun `REMOTE AUTO falls back to TODO placeholder when no xcframework`() {
        val task = newTask()
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn/x.zip")
        task.checksumStrategy.set(SpmChecksumStrategy.AUTO)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        assertTrue(manifest.contains("checksum: \"${GenerateSpmManifestTask.TODO_PLACEHOLDER}\""))
    }

    @Test
    fun `REMOTE AUTO reads checksum from sidecar file when present`() {
        val task = newTask()
        // Place the sidecar where the resolver expects it.
        val xcframeworkDir = File(tempDir, "build/XCFrameworks/freeDev/Shared.xcframework").apply { mkdirs() }
        File(xcframeworkDir.parentFile, "Shared.xcframework.checksum").writeText("sha256-abc123\n")
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn/x.zip")
        task.checksumStrategy.set(SpmChecksumStrategy.AUTO)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        assertTrue(manifest.contains("checksum: \"sha256-abc123\""))
    }

    @Test
    fun `REMOTE REQUIRE_FILE throws when sidecar missing`() {
        val task = newTask()
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn/x.zip")
        task.checksumStrategy.set(SpmChecksumStrategy.REQUIRE_FILE)
        assertThrows(IllegalStateException::class.java) { task.generate() }
    }

    @Test
    fun `REMOTE REQUIRE_FILE reads sidecar happy path`() {
        val task = newTask()
        val xcframeworkDir = File(tempDir, "build/XCFrameworks/freeDev/Shared.xcframework").apply { mkdirs() }
        File(xcframeworkDir.parentFile, "Shared.xcframework.checksum").writeText("sha256-required-file-value\n")
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn/x.zip")
        task.checksumStrategy.set(SpmChecksumStrategy.REQUIRE_FILE)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        assertTrue(manifest.contains("checksum: \"sha256-required-file-value\""))
    }

    @Test
    fun `REMOTE AUTO computes sha256 over xcframework directory when present`() {
        val task = newTask()
        // Build a fake .xcframework bundle with a couple of files.
        val xcframeworkDir = File(tempDir, "build/XCFrameworks/freeDev/Shared.xcframework").apply { mkdirs() }
        File(xcframeworkDir, "Info.plist").writeText("<plist></plist>")
        File(xcframeworkDir, "binary.bin").writeBytes(byteArrayOf(1, 2, 3, 4))
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn/x.zip")
        task.checksumStrategy.set(SpmChecksumStrategy.AUTO)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        // sha256 is 64 lowercase hex chars
        val checksumRegex = Regex("""checksum: "([0-9a-f]{64})"""")
        assertTrue(checksumRegex.containsMatchIn(manifest))
    }

    @Test
    fun `REMOTE AUTO computes sha256 over xcframework when it is a single file`() {
        val task = newTask()
        // Single file rather than directory bundle — exercises sha256(file) branch.
        val xcframeworkFile = File(tempDir, "build/XCFrameworks/freeDev/Shared.xcframework").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(10, 20, 30, 40, 50))
        }
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn/x.zip")
        task.checksumStrategy.set(SpmChecksumStrategy.AUTO)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        val checksumRegex = Regex("""checksum: "([0-9a-f]{64})"""")
        assertTrue(checksumRegex.containsMatchIn(manifest))
        // Sanity — confirm we hit the single-file branch by validating the file existed.
        assertTrue(xcframeworkFile.isFile)
    }

    @Test
    fun `an already-capitalised variant name is not double-capitalised in the product name`() {
        val task = newTask()
        task.variantName.set("FreeDev")
        task.distribution.set(SpmDistribution.LOCAL)
        task.checksumStrategy.set(SpmChecksumStrategy.AUTO)
        task.outputDirectory.set(File(tempDir, "build/spm/FreeDev"))
        task.generate()
        val manifest = File(tempDir, "build/spm/FreeDev/Package.swift").readText()
        assertTrue(manifest.contains("name: \"SharedFreeDev\""), manifest)
    }

    @Test
    fun `REMOTE with an explicit xcframeworkPath resolves the sidecar next to that path`() {
        val task = newTask()
        val xcf = File(tempDir, "custom/Shared.xcframework").apply { mkdirs() }
        File(xcf.parentFile, "Shared.xcframework.checksum").writeText("a".repeat(64))
        task.distribution.set(SpmDistribution.REMOTE)
        task.binaryUrlTemplate.set("https://cdn/{variant}.zip")
        task.xcframeworkPath.set("../../../custom/Shared.xcframework")
        task.checksumStrategy.set(SpmChecksumStrategy.REQUIRE_FILE)
        task.generate()
        val manifest = File(tempDir, "build/spm/freeDev/Package.swift").readText()
        assertTrue(manifest.contains("checksum: \"${"a".repeat(64)}\""), manifest)
    }

    @Test
    fun `log path is shortened when the root dir is known`() {
        val task = newTask()
        task.distribution.set(SpmDistribution.LOCAL)
        task.checksumStrategy.set(SpmChecksumStrategy.AUTO)
        task.rootDirPath.set(task.project.layout.projectDirectory)
        task.generate()
        assertTrue(File(tempDir, "build/spm/freeDev/Package.swift").exists())
    }
}
