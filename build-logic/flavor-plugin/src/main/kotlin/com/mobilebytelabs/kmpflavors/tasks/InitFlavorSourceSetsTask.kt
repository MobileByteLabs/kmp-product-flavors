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
}
