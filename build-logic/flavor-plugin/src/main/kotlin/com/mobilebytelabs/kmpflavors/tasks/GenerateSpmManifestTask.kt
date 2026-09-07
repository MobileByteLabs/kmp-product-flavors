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

package com.mobilebytelabs.kmpflavors.tasks

import com.mobilebytelabs.kmpflavors.SpmChecksumStrategy
import com.mobilebytelabs.kmpflavors.SpmDistribution
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

/**
 * Generates a per-flavor `Package.swift` SPM manifest under
 * `<projectDir>/build/spm/<variant>/Package.swift`.
 *
 * Decision D6: per-flavor manifest (one `Package.swift` per active flavor variant).
 * Unified manifest with conditional product targets is roadmapped for v1.3.0+.
 *
 * Linux-CI compatible — no Xcode toolchain required at task time. Generated manifests
 * are validated by string-match in fixtures and (optionally) by `swift package describe`
 * when run on macOS in CI.
 */
abstract class GenerateSpmManifestTask : DefaultTask() {

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val flavorName: Property<String>

    @get:Input
    abstract val xcframeworkName: Property<String>

    @get:Input
    abstract val distribution: Property<SpmDistribution>

    @get:Input
    @get:Optional
    abstract val binaryUrlTemplate: Property<String>

    @get:Input
    @get:Optional
    abstract val xcframeworkPath: Property<String>

    @get:Input
    @get:Optional
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val checksumStrategy: Property<SpmChecksumStrategy>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Repo root + module dir captured at CONFIGURATION time.
     *
     * `Task.project` must never be touched from a `@TaskAction` — Gradle's configuration
     * cache rejects it ("Invocation of 'Task.project' by task ... at execution time is
     * unsupported"). This task did exactly that until v2.9; it went unnoticed because SPM
     * generation was opt-in and no sample exercised it under the configuration cache.
     */
    @get:Internal
    abstract val rootDirPath: DirectoryProperty

    @TaskAction
    fun generate() {
        val variant = variantName.get()
        val flavor = flavorName.get()
        val name = xcframeworkName.get()
        val outDir = outputDirectory.get().asFile.apply { mkdirs() }

        val manifest = when (distribution.get()) {
            SpmDistribution.LOCAL -> renderLocal(name, variant)
            SpmDistribution.REMOTE -> renderRemote(name, variant, flavor)
        }

        val target = File(outDir, "Package.swift")
        target.writeText(manifest)
        logger.lifecycle("[KMP Flavors] Wrote SPM manifest → ${displayPath(target)}")
    }

    private fun renderLocal(name: String, variant: String): String {
        val path = xcframeworkPath.orNull
            ?: "../../XCFrameworks/$variant/$name.xcframework"
        return buildString {
            appendHeader(name, variant)
            appendLine("    targets: [")
            appendLine("        .binaryTarget(")
            appendLine("            name: \"${productNameForVariant(name, variant)}\",")
            appendLine("            path: \"$path\"")
            appendLine("        )")
            appendLine("    ]")
            appendLine(")")
        }
    }

    private fun renderRemote(name: String, variant: String, flavor: String): String {
        val urlTemplate = binaryUrlTemplate.orNull
            ?: error("[KMP Flavors] spm.binaryUrlTemplate must be set when distribution = REMOTE")
        val version = projectVersion.orNull ?: "0.0.0"
        val resolvedUrl = urlTemplate
            .replace("{flavor}", flavor)
            .replace("{variant}", variant)
            .replace("{version}", version)

        val checksum = resolveChecksum(variant, name)

        return buildString {
            appendHeader(name, variant)
            appendLine("    targets: [")
            appendLine("        .binaryTarget(")
            appendLine("            name: \"${productNameForVariant(name, variant)}\",")
            appendLine("            url: \"$resolvedUrl\",")
            appendLine("            checksum: \"$checksum\"")
            appendLine("        )")
            appendLine("    ]")
            appendLine(")")
        }
    }

    private fun StringBuilder.appendHeader(name: String, variant: String) {
        appendLine("// swift-tools-version:5.9")
        appendLine("// Generated by kmp-product-flavors — do not edit by hand.")
        appendLine("// Variant: $variant")
        appendLine()
        appendLine("import PackageDescription")
        appendLine()
        appendLine("let package = Package(")
        appendLine("    name: \"$name-$variant\",")
        appendLine("    products: [")
        appendLine("        .library(")
        appendLine("            name: \"${productNameForVariant(name, variant)}\",")
        appendLine("            targets: [\"${productNameForVariant(name, variant)}\"]")
        appendLine("        )")
        appendLine("    ],")
    }

    private fun productNameForVariant(name: String, variant: String): String = "$name${variant.replaceFirstChar { it.uppercase() }}"

    private fun resolveChecksum(variant: String, name: String): String {
        val strategy = checksumStrategy.get()

        val expected = xcframeworkPath.orNull
            ?: "../../XCFrameworks/$variant/$name.xcframework"
        // Resolve RELATIVE TO THE MANIFEST, because that is exactly what the path in the
        // emitted `binaryTarget(path:)` means. The previous resolution joined the path
        // against the MODULE dir after stripping "../../", which silently pointed at
        // <module>/XCFrameworks/... while the manifest itself said
        // <module>/build/XCFrameworks/... — so checksum lookups could miss the very binary
        // the manifest referenced.
        val xcframework = File(outputDirectory.get().asFile, expected).canonicalFile
        val sidecar = File(xcframework.parentFile, "${xcframework.name}.checksum")

        return when (strategy) {
            SpmChecksumStrategy.SKIP -> SKIP_PLACEHOLDER

            SpmChecksumStrategy.REQUIRE_FILE -> {
                if (!sidecar.exists()) {
                    error(
                        "[KMP Flavors] checksumStrategy=REQUIRE_FILE but ${displayPath(sidecar)} " +
                            "is missing. Generate it via `swift package compute-checksum <xcframework>`.",
                    )
                }
                sidecar.readText().trim()
            }

            SpmChecksumStrategy.AUTO -> when {
                sidecar.exists() -> sidecar.readText().trim()

                xcframework.exists() -> sha256(xcframework)

                else -> {
                    logger.warn(
                        "[KMP Flavors] checksumStrategy=AUTO but neither sidecar nor XCFramework " +
                            "is present at task time — emitting <TODO> placeholder.",
                    )
                    TODO_PLACEHOLDER
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        return if (file.isDirectory) {
            // For directories (an .xcframework is a directory bundle), hash file contents in path order.
            file.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(file).invariantSeparatorsPath }
                .forEach { md.update(it.readBytes()) }
            md.digest().toHex()
        } else {
            md.digest(file.readBytes()).toHex()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val TODO_PLACEHOLDER = "<TODO-checksum>"
        const val SKIP_PLACEHOLDER = "<SKIP-checksum>"
    }

    /**
     * Pretty relative path for logs, without touching `Task.project` at execution time.
     *
     * Deliberately pure string work: `File.relativeTo` throws when two paths share no root,
     * and guarding that with `runCatching` produced a branch unreachable on POSIX — which
     * the 100% Kover floor then (correctly) refused to accept as covered.
     */
    private fun displayPath(file: File): String {
        val root = rootDirPath.orNull?.asFile?.path ?: return file.path
        return file.path.removePrefix(root).removePrefix(File.separator)
    }
}
