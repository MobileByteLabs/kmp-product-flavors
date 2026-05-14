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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task that initializes flavor source set directories.
 *
 * This task creates the directory structure for all flavor source sets,
 * making it easy to get started with flavor-specific code.
 *
 * Usage:
 * ```bash
 * ./gradlew kmpFlavorInit
 * ```
 *
 * Generated structure:
 * ```
 * src/
 * ├── commonFree/
 * │   └── kotlin/
 * ├── commonPaid/
 * │   └── kotlin/
 * ├── androidFree/
 * │   └── kotlin/
 * ├── androidPaid/
 * │   └── kotlin/
 * └── ...
 * ```
 */
abstract class InitFlavorSourceSetsTask : DefaultTask() {

    /**
     * Set of all flavor names.
     */
    @get:Input
    abstract val flavorNames: SetProperty<String>

    /**
     * Set of platform prefixes (e.g., "android", "ios", "desktop").
     */
    @get:Input
    abstract val platformPrefixes: SetProperty<String>

    /**
     * Set of intermediate prefixes (e.g., "web", "native").
     */
    @get:Input
    @get:Optional
    abstract val intermediatePrefixes: SetProperty<String>

    /**
     * Whether to create intermediate source sets.
     */
    @get:Input
    abstract val createIntermediates: Property<Boolean>

    /**
     * Whether to create placeholder .gitkeep files.
     */
    @get:Input
    abstract val createGitKeep: Property<Boolean>

    /**
     * Whether to create example Kotlin files.
     */
    @get:Input
    abstract val createExampleFiles: Property<Boolean>

    /**
     * Whether to drop a `README.md` into each flavor source directory explaining
     * its intent (e.g. `commonDemoMain/README.md` describing what code belongs
     * there, with a link back to the plugin's docs).
     *
     * Lower-friction onboarding signal than an empty directory with only a
     * `.gitkeep`. Convention: `true` since v1.1.0.
     */
    @get:Input
    abstract val createReadmePerSourceSet: Property<Boolean>

    /**
     * v2.2 Phase 0K — drop a buildable sample `Sample.kt` into `commonMain/{packageName}/`
     * showing `BuildKonfig.VARIANT_NAME` + `BuildKonfig.IS_<FLAVOR>` consumption. Lower-friction
     * onboarding than the per-source-set placeholder examples.
     *
     * No-op when [examplePackage] is unset (no place to land the file).
     *
     * Convention: `true` in v2.2+ (consumer opts out via `set(false)`).
     */
    @get:Input
    abstract val generateSampleCode: Property<Boolean>

    /**
     * BuildKonfig class name to reference in the sample code. Wired by `KmpFlavorPlugin`
     * from `extension.buildConfigClassName`. Defaults to `BuildKonfig` to match the v1.1.5
     * extension convention.
     */
    @get:Input
    @get:Optional
    abstract val buildConfigClassName: Property<String>

    /**
     * The package name for example files.
     */
    @get:Input
    @get:Optional
    abstract val examplePackage: Property<String>

    /**
     * The source directory (usually project/src).
     */
    @get:OutputDirectory
    abstract val sourceDirectory: DirectoryProperty

    init {
        group = "kmp flavors"
        description = "Initializes flavor source set directories"

        // Set conventions
        createIntermediates.convention(true)
        createGitKeep.convention(true)
        createExampleFiles.convention(false)
        createReadmePerSourceSet.convention(true)
        // v2.2 Phase 0K — sample BuildKonfig consumption code, on by default.
        generateSampleCode.convention(true)
        buildConfigClassName.convention("BuildKonfig")
    }

    @TaskAction
    fun init() {
        val srcDir = sourceDirectory.get().asFile
        val flavors = flavorNames.get()
        val platforms = platformPrefixes.get()
        val intermediates = if (createIntermediates.get()) {
            intermediatePrefixes.getOrElse(emptySet())
        } else {
            emptySet()
        }

        logger.lifecycle("[KMP Flavors] Initializing source directories...")

        var createdCount = 0

        // Create common<Flavor> directories
        for (flavor in flavors) {
            val capitalizedFlavor = flavor.replaceFirstChar { it.uppercaseChar() }
            createdCount += createSourceSetDir(srcDir, "common$capitalizedFlavor")
        }

        // Create intermediate<Flavor> directories (if enabled)
        for (intermediate in intermediates) {
            for (flavor in flavors) {
                val capitalizedFlavor = flavor.replaceFirstChar { it.uppercaseChar() }
                createdCount += createSourceSetDir(srcDir, "$intermediate$capitalizedFlavor")
            }
        }

        // Create <platform><Flavor> directories
        for (platform in platforms) {
            for (flavor in flavors) {
                val capitalizedFlavor = flavor.replaceFirstChar { it.uppercaseChar() }
                createdCount += createSourceSetDir(srcDir, "$platform$capitalizedFlavor")
            }
        }

        // v2.2 Phase 0K — drop a buildable Sample.kt in commonMain showing BuildKonfig
        // consumption. Skipped silently when generateSampleCode=false OR no examplePackage.
        if (generateSampleCode.get()) {
            writeCommonMainSample(srcDir, flavors)
        }

        logger.lifecycle("[KMP Flavors] Created $createdCount source directories")
        logger.lifecycle("[KMP Flavors] Source sets ready at: ${srcDir.absolutePath}")
    }

    private fun createSourceSetDir(srcDir: File, sourceSetName: String): Int {
        val kotlinDir = File(srcDir, "$sourceSetName/kotlin")
        val resourcesDir = File(srcDir, "$sourceSetName/resources")

        var created = 0

        if (!kotlinDir.exists()) {
            kotlinDir.mkdirs()
            logger.info("[KMP Flavors] Created: $sourceSetName/kotlin")
            created++

            // Create .gitkeep if enabled
            if (createGitKeep.get()) {
                File(kotlinDir, ".gitkeep").createNewFile()
            }

            // Create example file if enabled
            if (createExampleFiles.get()) {
                createExampleKotlinFile(kotlinDir, sourceSetName)
            }

            // Drop a per-source-set README explaining intent (G18).
            if (createReadmePerSourceSet.get()) {
                writeSourceSetReadme(File(srcDir, sourceSetName), sourceSetName)
            }
        }

        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs()
            logger.info("[KMP Flavors] Created: $sourceSetName/resources")

            // Create .gitkeep if enabled
            if (createGitKeep.get()) {
                File(resourcesDir, ".gitkeep").createNewFile()
            }
        }

        return created
    }

    private fun writeSourceSetReadme(sourceSetDir: File, sourceSetName: String) {
        val readme = File(sourceSetDir, "README.md")
        if (readme.exists()) return
        val content = buildString {
            appendLine("# `$sourceSetName/`")
            appendLine()
            appendLine("This directory holds **${classifySourceSet(sourceSetName)}**.")
            appendLine()
            appendLine("## What goes here")
            appendLine()
            appendLine(describePurpose(sourceSetName))
            appendLine()
            appendLine("## Source set hierarchy")
            appendLine()
            appendLine("`$sourceSetName/` is wired by the `kmp-product-flavors` plugin via")
            appendLine("`SourceSetConfigurator`. The plugin only adds the `dependsOn(...)` edges")
            appendLine("for the **active** flavor variant; non-active flavor source sets exist as")
            appendLine("directories (so the IDE recognises them) but are not on the compile path.")
            appendLine()
            appendLine("## See also")
            appendLine()
            appendLine("- [docs/PRODUCT_FLAVORS.md](../../../../docs/PRODUCT_FLAVORS.md)")
            appendLine("- [docs/BUILD_VARIANTS.md](../../../../docs/BUILD_VARIANTS.md)")
            appendLine("- This file was generated by `./gradlew kmpFlavorInit`. To opt out, set")
            appendLine("  `kmpFlavorInit { createReadmePerSourceSet.set(false) }`.")
        }
        readme.writeText(content)
        logger.info("[KMP Flavors] Wrote README for $sourceSetName")
    }

    private fun classifySourceSet(name: String): String = when {
        name.endsWith("Test") -> "**flavor-specific test code** (only on the test classpath when this flavor is active)"
        name.startsWith("common") -> "**flavor-specific common code** shared across all platforms"
        else -> "**flavor-specific platform code** for `${name.takeWhile { it.isLowerCase() }}`"
    }

    private fun describePurpose(name: String): String = when {
        name.endsWith("Test") ->
            "Tests that exercise behaviour specific to this flavor — fakes, fixtures, or assertions " +
                "that only make sense for this variant. Sibling production code lives in `${name.removeSuffix("Test")}/`."

        name.startsWith("common") ->
            "Code that is conditional on the flavor but platform-agnostic — DI bindings, feature " +
                "flags backed by `FlavorConfig.IS_*` constants, and types whose `actual` declarations " +
                "diverge per flavor."

        else ->
            "Platform-specific implementations that diverge per flavor (analytics integrations, " +
                "payment SDKs, signing key references). Use `expect`/`actual` against `commonMain` " +
                "for code that needs a flavored variant per platform."
    }

    private fun createExampleKotlinFile(kotlinDir: File, sourceSetName: String) {
        val packageName = examplePackage.orNull ?: return

        // Create package directory
        val packageDir = File(kotlinDir, packageName.replace('.', File.separatorChar))
        packageDir.mkdirs()

        // Determine file name based on source set
        val fileName = "${sourceSetName.replaceFirstChar { it.uppercaseChar() }}Example.kt"
        val file = File(packageDir, fileName)

        if (!file.exists()) {
            val content = buildString {
                appendLine("/*")
                appendLine(" * Example file for $sourceSetName source set.")
                appendLine(" * Add your flavor-specific code here.")
                appendLine(" */")
                appendLine()
                appendLine("package $packageName")
                appendLine()
                appendLine("/**")
                appendLine(" * Example class for $sourceSetName.")
                appendLine(" * Replace this with your actual implementation.")
                appendLine(" */")
                appendLine("object ${sourceSetName.replaceFirstChar { it.uppercaseChar() }}Example {")
                appendLine("    fun greet(): String = \"Hello from $sourceSetName!\"")
                appendLine("}")
                appendLine()
            }

            file.writeText(content)
            logger.info("[KMP Flavors] Created example: ${file.relativeTo(kotlinDir.parentFile.parentFile)}")
        }
    }

    /**
     * v2.2 Phase 0K — drop `commonMain/{packageName}/Sample.kt` with sample BuildKonfig
     * consumption. Idempotent (skipped silently if file already exists). Skipped when
     * [examplePackage] is unset.
     */
    private fun writeCommonMainSample(srcDir: File, flavors: Set<String>) {
        val packageName = examplePackage.orNull?.takeIf { it.isNotBlank() }
        if (packageName == null) {
            logger.info(
                "[KMP Flavors] Phase 0K — examplePackage unset; skipping Sample.kt generation.",
            )
            return
        }
        val className = buildConfigClassName.getOrElse("BuildKonfig")
        val commonMainKotlin = File(srcDir, "commonMain/kotlin")
        commonMainKotlin.mkdirs()
        val packageDir = File(commonMainKotlin, packageName.replace('.', File.separatorChar))
        packageDir.mkdirs()
        val sampleFile = File(packageDir, "Sample.kt")
        if (sampleFile.exists()) {
            logger.info("[KMP Flavors] Phase 0K — commonMain/Sample.kt already exists; skipping.")
            return
        }
        val firstFlavor = flavors.firstOrNull()?.uppercase() ?: "DEFAULT"
        val flavorAccessors = flavors.sorted().joinToString("\n    ") {
            "// val is${it.replaceFirstChar { c -> c.uppercase() }}: Boolean = $className.IS_${it.uppercase()}"
        }
        val content = buildString {
            appendLine("/*")
            appendLine(" * Generated by `./gradlew kmpFlavorInit` (v2.2 Phase 0K sample-code generation).")
            appendLine(" *")
            appendLine(" * This file shows how to consume the generated `$className` constants.")
            appendLine(" * Delete or replace once you've integrated `$className` references into your real code.")
            appendLine(" */")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendLine("object Sample {")
            appendLine("    /**")
            appendLine("     * Returns a human-readable description of the active variant.")
            appendLine("     * Per-flavor compile-time constants live on `$className.IS_<FLAVOR>`.")
            appendLine("     */")
            appendLine("    fun describe(): String = \"Running \${$className.VARIANT_NAME}\"")
            appendLine()
            appendLine("    // Uncomment the per-flavor accessors below to add compile-time flavor checks:")
            appendLine("    $flavorAccessors")
            appendLine("}")
            appendLine()
        }
        sampleFile.writeText(content)
        logger.lifecycle(
            "[KMP Flavors] Phase 0K — wrote Sample.kt with $className consumption stub: " +
                "${sampleFile.relativeTo(srcDir.parentFile)}",
        )
    }
}
