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

import com.mobilebytelabs.kmpflavors.internal.DependencyConfigurator
import com.mobilebytelabs.kmpflavors.internal.FlavorVariantResolver
import com.mobilebytelabs.kmpflavors.internal.PlatformDetector
import com.mobilebytelabs.kmpflavors.internal.SourceSetConfigurator
import com.mobilebytelabs.kmpflavors.tasks.GenerateBuildConfigTask
import com.mobilebytelabs.kmpflavors.tasks.ListFlavorsTask
import com.mobilebytelabs.kmpflavors.tasks.ValidateFlavorsTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP Product Flavors Gradle Plugin.
 *
 * This plugin brings Android-style product flavor support to Kotlin Multiplatform projects.
 * It enables conditional compilation, source set management, and build config generation
 * based on the active flavor variant.
 *
 * ## Usage
 *
 * ```kotlin
 * plugins {
 *     kotlin("multiplatform")
 *     id("io.github.anthropic.kmp-product-flavors")
 * }
 *
 * kmpFlavors {
 *     generateBuildConfig.set(true)
 *     buildConfigPackage.set("com.example.app")
 *
 *     flavorDimensions {
 *         register("tier") { priority.set(0) }
 *         register("environment") { priority.set(1) }
 *     }
 *
 *     flavors {
 *         register("free") {
 *             dimension.set("tier")
 *             isDefault.set(true)
 *             buildConfigField("Boolean", "IS_PREMIUM", "false")
 *         }
 *         register("paid") {
 *             dimension.set("tier")
 *             buildConfigField("Boolean", "IS_PREMIUM", "true")
 *         }
 *         register("dev") {
 *             dimension.set("environment")
 *             isDefault.set(true)
 *             buildConfigField("String", "BASE_URL", "\"https://dev-api.example.com\"")
 *         }
 *         register("prod") {
 *             dimension.set("environment")
 *             buildConfigField("String", "BASE_URL", "\"https://api.example.com\"")
 *         }
 *     }
 * }
 * ```
 *
 * ## Gradle Properties
 *
 * - `kmpFlavor`: Override the active flavor variant (e.g., `-PkmpFlavor=paidProd`)
 *
 * ## Tasks
 *
 * - `generateFlavorBuildConfig`: Generates the BuildConfig Kotlin object
 * - `validateFlavors`: Validates the flavor configuration
 * - `listFlavors`: Lists all available flavor variants
 */
class KmpFlavorPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Register the extension
        val extension = project.extensions.create(
            "kmpFlavors",
            KmpFlavorExtension::class.java,
        )

        // Defer configuration until after project evaluation
        project.afterEvaluate {
            configurePlugin(project, extension)
        }
    }

    private fun configurePlugin(project: Project, extension: KmpFlavorExtension) {
        val logger = project.logger

        // Find KMP extension
        val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        if (kotlin == null) {
            logger.warn("[KMP Flavors] Kotlin Multiplatform plugin not found. Skipping flavor configuration.")
            return
        }

        val flavors = extension.flavors.toList()
        val dimensions = extension.flavorDimensions.toList()

        // Skip if no flavors configured
        if (flavors.isEmpty()) {
            logger.info("[KMP Flavors] No flavors configured. Skipping.")
            return
        }

        logger.lifecycle("[KMP Flavors] Configuring ${flavors.size} flavors across ${dimensions.size} dimensions")

        // Resolve all variants
        val allVariants = FlavorVariantResolver.resolveAllVariants(dimensions, flavors)
        if (allVariants.isEmpty()) {
            logger.warn("[KMP Flavors] No variants resolved. Check dimension assignments.")
            return
        }

        // Determine active variant
        val activeVariant = resolveActiveVariant(project, extension, dimensions, flavors, allVariants)
        logger.lifecycle("[KMP Flavors] Active variant: ${activeVariant.name}")

        // Detect platforms
        val platforms = PlatformDetector.detect(kotlin, logger)
        val createIntermediates = extension.createIntermediateSourceSets.get()

        // Wire intermediate source sets if needed
        if (createIntermediates) {
            PlatformDetector.wireIntermediateSourceSets(kotlin, platforms)
        }

        // Resolve platform source sets
        val platformSourceSets = PlatformDetector.resolveSourceSets(kotlin, platforms)

        // Configure flavor source sets
        val sourceSetConfigurator = SourceSetConfigurator(logger)
        sourceSetConfigurator.configure(
            kotlin = kotlin,
            activeVariant = activeVariant,
            allFlavors = flavors,
            platforms = platforms,
            platformSourceSets = platformSourceSets,
            createIntermediates = createIntermediates,
        )

        // Configure dependencies
        val dependencyConfigurator = DependencyConfigurator(logger)
        dependencyConfigurator.configure(project, activeVariant)

        // Register tasks
        registerTasks(project, extension, allVariants, activeVariant, flavors, dimensions, platforms)

        // Wire build config generation to compilation if enabled
        if (extension.generateBuildConfig.get()) {
            wireGenerateBuildConfigToCompilation(project, kotlin)
        }
    }

    private fun resolveActiveVariant(
        project: Project,
        extension: KmpFlavorExtension,
        dimensions: List<FlavorDimension>,
        flavors: List<FlavorConfig>,
        allVariants: List<FlavorVariant>,
    ): FlavorVariant {
        // Priority: 1) Gradle property, 2) Extension property, 3) Default variant
        val gradleProperty = project.findProperty("kmpFlavor")?.toString()
        val extensionProperty = extension.activeFlavor.orNull

        val activeName = gradleProperty ?: extensionProperty

        return if (activeName != null) {
            FlavorVariantResolver.resolveVariantByName(activeName, allVariants)
                ?: throw GradleException(
                    "[KMP Flavors] Unknown variant '$activeName'. " +
                        "Available variants: ${allVariants.joinToString(", ") { it.name }}",
                )
        } else {
            FlavorVariantResolver.resolveDefaultVariant(dimensions, flavors)
                ?: allVariants.first()
        }
    }

    private fun registerTasks(
        project: Project,
        extension: KmpFlavorExtension,
        allVariants: List<FlavorVariant>,
        activeVariant: FlavorVariant,
        flavors: List<FlavorConfig>,
        dimensions: List<FlavorDimension>,
        platforms: List<com.mobilebytelabs.kmpflavors.internal.PlatformGroup>,
    ) {
        // Generate BuildConfig task
        if (extension.generateBuildConfig.get()) {
            project.tasks.register(
                "generateFlavorBuildConfig",
                GenerateBuildConfigTask::class.java,
            ) {
                packageName.set(extension.buildConfigPackage)
                className.set(extension.buildConfigClassName)
                variantName.set(activeVariant.name)
                allFlavorNames.set(flavors.map { it.name }.toSet())
                activeFlavorNames.set(activeVariant.flavorNames.toSet())
                buildConfigFields.set(activeVariant.mergedBuildConfigFields)
                outputDirectory.set(
                    project.layout.buildDirectory.dir("generated/kmpFlavors/commonMain/kotlin"),
                )
            }
        }

        // Validate flavors task
        project.tasks.register(
            "validateFlavors",
            ValidateFlavorsTask::class.java,
        ) {
            dimensionNames.set(dimensions.map { it.name }.toSet())
            flavorDimensions.set(flavors.associate { it.name to it.dimension.orNull })
            flavorDefaults.set(flavors.associate { it.name to it.isDefault.getOrElse(false) })
            validVariantNames.set(allVariants.map { it.name }.toSet())
            activeVariantName.set(activeVariant.name)
            allFlavorNames.set(flavors.map { it.name })
        }

        // List flavors task
        val variantsData = allVariants.associate { it.name to it.flavorNames }
        val activeVariantNameValue = activeVariant.name
        val dimensionsData = dimensions.associate { it.name to it.priority.getOrElse(0) }
        val platformsData = platforms.filter { !it.isIntermediate }.map { it.prefix }

        project.tasks.register("listFlavors", ListFlavorsTask::class.java).configure {
            this.variants.set(variantsData)
            this.activeVariant.set(activeVariantNameValue)
            this.dimensions.set(dimensionsData)
            this.platforms.set(platformsData)
        }
    }

    private fun wireGenerateBuildConfigToCompilation(
        project: Project,
        kotlin: KotlinMultiplatformExtension,
    ) {
        val generateTask = project.tasks.named(
            "generateFlavorBuildConfig",
            GenerateBuildConfigTask::class.java,
        )

        // Add generated source directory to commonMain
        kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(
            generateTask.flatMap { it.outputDirectory },
        )

        // Make Kotlin compilation depend on generation
        project.tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
            dependsOn(generateTask)
        }
    }
}
