/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobilebytelabs.kmpflavors

import com.mobilebytelabs.kmpflavors.tasks.GenerateSpmManifestTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Linux-CI compatible tests for the SPM manifest generator.
 *
 * No Xcode toolchain is required — assertions are string-match against the produced
 * `Package.swift`. macOS-only validation via `swift package describe` is run in a
 * separate sample-build step on macos-latest runners.
 */
class GenerateSpmManifestTaskTest {

    @Test
    fun `LOCAL distribution emits binaryTarget with path`(@TempDir tempDir: Path) {
        val manifest = generate(tempDir) {
            variantName.set("freeDevDebug")
            flavorName.set("free")
            xcframeworkName.set("Shared")
            distribution.set(SpmDistribution.LOCAL)
            checksumStrategy.set(SpmChecksumStrategy.SKIP)
        }

        assertTrue(manifest.contains("// swift-tools-version:5.9"))
        assertTrue(manifest.contains("name: \"Shared-freeDevDebug\""))
        assertTrue(manifest.contains(".binaryTarget("))
        assertTrue(manifest.contains("name: \"SharedFreeDevDebug\""))
        assertTrue(manifest.contains("path: \"../../XCFrameworks/freeDevDebug/Shared.xcframework\""))
        assertFalse(manifest.contains("url:"))
        assertFalse(manifest.contains("checksum:"))
    }

    @Test
    fun `REMOTE distribution interpolates flavor and variant placeholders`(@TempDir tempDir: Path) {
        val manifest = generate(tempDir) {
            variantName.set("paidProdRelease")
            flavorName.set("paid")
            xcframeworkName.set("Shared")
            distribution.set(SpmDistribution.REMOTE)
            binaryUrlTemplate.set("https://cdn.example.com/{flavor}/{variant}/Shared.xcframework.zip")
            checksumStrategy.set(SpmChecksumStrategy.SKIP)
            projectVersion.set("1.2.3")
        }

        assertTrue(manifest.contains("url: \"https://cdn.example.com/paid/paidProdRelease/Shared.xcframework.zip\""))
        assertTrue(manifest.contains("checksum: \"<SKIP-checksum>\""))
        assertTrue(manifest.contains("name: \"SharedPaidProdRelease\""))
    }

    @Test
    fun `REMOTE without binaryUrlTemplate fails task execution`(@TempDir tempDir: Path) {
        val ex = runCatching {
            generate(tempDir) {
                variantName.set("freeDebug")
                flavorName.set("free")
                xcframeworkName.set("Shared")
                distribution.set(SpmDistribution.REMOTE)
                checksumStrategy.set(SpmChecksumStrategy.SKIP)
            }
        }.exceptionOrNull()

        assertTrue(
            ex?.message?.contains("binaryUrlTemplate must be set") == true ||
                (ex?.cause?.message?.contains("binaryUrlTemplate must be set") == true),
            "Expected IllegalStateException about binaryUrlTemplate; got: ${ex?.message}",
        )
    }

    private fun generate(tempDir: Path, configure: GenerateSpmManifestTask.() -> Unit): String {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.version = "1.0.0"
        val task = project.tasks.register(
            "generateSpmManifestTest",
            GenerateSpmManifestTask::class.java,
        ).get()
        val outDir = File(tempDir.toFile(), "build/spm")
        task.outputDirectory.set(outDir)
        task.configure()
        task.generate()
        return File(outDir, "Package.swift").readText()
    }
}
