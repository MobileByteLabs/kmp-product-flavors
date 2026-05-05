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

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorVariant
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/**
 * Configures flavor-specific source sets and their dependencies.
 *
 * This class is responsible for:
 * 1. Creating source set entries for ALL flavors (so IDE recognizes all directories)
 * 2. Wiring dependsOn relationships for only the ACTIVE variant's flavors
 */
class SourceSetConfigurator(private val logger: Logger) {

    /**
     * Configures all flavor source sets.
     *
     * @param kotlin The KMP extension
     * @param activeVariant The currently active variant
     * @param allFlavors All configured flavors
     * @param platforms Detected platforms
     * @param platformSourceSets Map of platforms to their source sets
     * @param createIntermediates Whether to create intermediate flavor source sets
     */
    @Suppress("UNUSED_PARAMETER") // platformSourceSets kept for API compatibility
    fun configure(
        kotlin: KotlinMultiplatformExtension,
        activeVariant: FlavorVariant,
        allFlavors: Collection<FlavorConfig>,
        platforms: List<PlatformGroup>,
        platformSourceSets: Map<PlatformGroup, KotlinSourceSet>,
        createIntermediates: Boolean,
    ) {
        val sourceSets = kotlin.sourceSets
        val commonMain = sourceSets.getByName("commonMain")
        val activeFlavorNames = activeVariant.flavorNames.toSet()

        logger.lifecycle("[KMP Flavors] Configuring source sets for ${allFlavors.size} flavors")
        logger.lifecycle("[KMP Flavors] Active variant: ${activeVariant.name} (flavors: ${activeFlavorNames.joinToString()})")

        // Get leaf platforms (non-intermediate)
        val leafPlatforms = platforms.filter { !it.isIntermediate }
        val intermediatePlatforms = platforms.filter { it.isIntermediate }

        for (flavor in allFlavors) {
            val flavorName = flavor.name
            val isActiveFlavor = flavorName in activeFlavorNames
            val capitalizedFlavor = flavorName.replaceFirstChar { it.uppercaseChar() }

            // 1. Create common<Flavor> source set
            val commonFlavorName = "common$capitalizedFlavor"
            val commonFlavor = createSourceSet(sourceSets, commonFlavorName)

            if (isActiveFlavor) {
                commonFlavor.dependsOn(commonMain)
                logger.info("[KMP Flavors] Wired $commonFlavorName -> commonMain")
            }

            // 2. Create intermediate<Flavor> source sets (if enabled)
            // Note: Intermediate flavor source sets only depend on commonFlavor
            // (NOT on intermediateMain as it may be a compilation default source set)
            if (createIntermediates) {
                for (intermediate in intermediatePlatforms) {
                    val intermediateFlavorName = "${intermediate.prefix}$capitalizedFlavor"
                    val intermediateFlavor = createSourceSet(sourceSets, intermediateFlavorName)

                    if (isActiveFlavor) {
                        // Wire only to commonFlavor (not intermediateMain to avoid compilation default dependency)
                        intermediateFlavor.dependsOn(commonFlavor)
                        logger.info("[KMP Flavors] Wired $intermediateFlavorName -> $commonFlavorName")
                    }
                }
            }

            // 3. Create <platform><Flavor> source sets for leaf platforms
            // Note: Platform flavor source sets should NOT depend on platformMain
            // (e.g., desktopFree cannot depend on desktopMain as it's a compilation default source set)
            // Instead, they depend on commonFlavor or intermediate flavor source sets only
            for (platform in leafPlatforms) {
                val platformFlavorName = "${platform.prefix}$capitalizedFlavor"
                val platformFlavor = createSourceSet(sourceSets, platformFlavorName)

                if (isActiveFlavor) {
                    // Wire to intermediate flavor or common flavor (NOT to platformMain)
                    if (createIntermediates && platform.parent != null) {
                        val parentIntermediate = intermediatePlatforms.find { it.prefix == platform.parent }
                        if (parentIntermediate != null) {
                            val intermediateFlavorName = "${parentIntermediate.prefix}$capitalizedFlavor"
                            val intermediateFlavor = sourceSets.findByName(intermediateFlavorName)
                            if (intermediateFlavor != null) {
                                platformFlavor.dependsOn(intermediateFlavor)
                                logger.info("[KMP Flavors] Wired $platformFlavorName -> $intermediateFlavorName")
                            }
                        }
                    } else {
                        // No intermediate parent, wire directly to commonFlavor
                        platformFlavor.dependsOn(commonFlavor)
                        logger.info("[KMP Flavors] Wired $platformFlavorName -> $commonFlavorName")
                    }
                }
            }
        }
    }

    /**
     * Creates or retrieves a source set with proper directories configured.
     */
    private fun createSourceSet(sourceSets: org.gradle.api.NamedDomainObjectContainer<KotlinSourceSet>, name: String): KotlinSourceSet = sourceSets.maybeCreate(name).apply {
        kotlin.srcDir("src/$name/kotlin")
        resources.srcDir("src/$name/resources")
    }
}
