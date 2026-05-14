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

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorDimension
import com.mobilebytelabs.kmpflavors.FlavorVariant
import com.mobilebytelabs.kmpflavors.VariantFilter
import org.gradle.api.Action

/**
 * Resolves the variant matrix from dimensions and flavors.
 *
 * This object computes the cartesian product of all dimensions to produce
 * all possible variants. For example:
 * - Dimension "tier" with flavors [free, paid]
 * - Dimension "environment" with flavors [dev, staging, prod]
 *
 * Results in 6 variants: freeDev, freeStaging, freeProd, paidDev, paidStaging, paidProd
 */
object FlavorVariantResolver {

    /**
     * Resolves all possible variants from the given dimensions and flavors.
     *
     * @param dimensions The flavor dimensions, sorted by priority
     * @param flavors All configured flavors
     * @param variantFilters Optional list of filter actions to exclude variants
     * @return List of all possible variant combinations (after filtering)
     */
    fun resolveAllVariants(
        dimensions: Collection<FlavorDimension>,
        flavors: Collection<FlavorConfig>,
        variantFilters: List<Action<VariantFilter>> = emptyList(),
        buildTypes: Collection<BuildTypeConfig> = emptyList(),
        enableBuildTypes: Boolean = false,
    ): List<FlavorVariant> {
        if (flavors.isEmpty()) {
            return emptyList()
        }

        // Step 1 — compute flavor-only variants.
        val flavorOnlyVariants: List<FlavorVariant> = if (dimensions.isEmpty()) {
            flavors.map { flavor ->
                FlavorVariant(name = flavor.name, flavors = listOf(flavor))
            }
        } else {
            val sortedDimensions = dimensions.sortedBy { it.priority.getOrElse(0) }
            // KMPF-V03 (validator-caught): if a dimension has no flavors the matrix can't
            // produce variants. Return an empty list here so the validator surfaces the
            // structured finding instead of an opaque IllegalStateException at resolution time.
            val flavorsByDimension = sortedDimensions.map { dimension ->
                flavors.filter { it.dimension.orNull == dimension.name }
            }
            if (flavorsByDimension.any { it.isEmpty() }) {
                emptyList()
            } else {
                cartesianProduct(flavorsByDimension).map { flavorList ->
                    FlavorVariant(name = buildVariantName(flavorList), flavors = flavorList)
                }
            }
        }

        // Step 2 — if enableBuildTypes is true and at least one buildType is declared,
        // expand the flavor-only matrix by buildType.
        val expanded: List<FlavorVariant> = if (enableBuildTypes && buildTypes.isNotEmpty()) {
            flavorOnlyVariants.flatMap { flavorVariant ->
                buildTypes.map { buildType ->
                    FlavorVariant(
                        name = flavorVariant.name + buildType.name.replaceFirstChar { it.uppercaseChar() },
                        flavors = flavorVariant.flavors,
                        buildType = buildType,
                    )
                }
            }
        } else {
            flavorOnlyVariants
        }

        // Apply variant filters
        return applyVariantFilters(expanded, variantFilters)
    }

    /**
     * Applies variant filter actions to exclude unwanted variants.
     *
     * @param variants All variants before filtering
     * @param filters List of filter actions to apply
     * @return Filtered list of variants
     */
    private fun applyVariantFilters(variants: List<FlavorVariant>, filters: List<Action<VariantFilter>>): List<FlavorVariant> {
        if (filters.isEmpty()) {
            return variants
        }

        return variants.filter { variant ->
            val filter = VariantFilter(
                variantName = variant.name,
                flavorNames = variant.flavorNames,
                flavors = variant.flavors,
                buildType = variant.buildType?.name,
            )

            // Apply all filter actions
            filters.forEach { action ->
                action.execute(filter)
            }

            // Keep variant if not excluded
            !filter.isExcluded()
        }
    }

    /**
     * Resolves the default variant based on default flavors from each dimension.
     *
     * @param dimensions The flavor dimensions
     * @param flavors All configured flavors
     * @return The default variant, or null if no defaults are configured
     */
    fun resolveDefaultVariant(
        dimensions: Collection<FlavorDimension>,
        flavors: Collection<FlavorConfig>,
        buildTypes: Collection<BuildTypeConfig> = emptyList(),
        enableBuildTypes: Boolean = false,
    ): FlavorVariant? {
        if (flavors.isEmpty()) {
            return null
        }

        val flavorOnly: FlavorVariant = if (dimensions.isEmpty()) {
            val defaultFlavor = flavors.find { it.isDefault.getOrElse(false) }
                ?: flavors.firstOrNull()
                ?: return null
            FlavorVariant(name = defaultFlavor.name, flavors = listOf(defaultFlavor))
        } else {
            val sortedDimensions = dimensions.sortedBy { it.priority.getOrElse(0) }
            val defaultFlavors = sortedDimensions.mapNotNull { dimension ->
                val dimensionFlavors = flavors.filter { it.dimension.orNull == dimension.name }
                dimensionFlavors.find { it.isDefault.getOrElse(false) }
                    ?: dimensionFlavors.firstOrNull()
            }
            if (defaultFlavors.size != sortedDimensions.size) return null
            FlavorVariant(name = buildVariantName(defaultFlavors), flavors = defaultFlavors)
        }

        if (!enableBuildTypes || buildTypes.isEmpty()) return flavorOnly

        val defaultBuildType = buildTypes.find { it.isDefault.getOrElse(false) }
            ?: buildTypes.firstOrNull()
            ?: return flavorOnly

        return FlavorVariant(
            name = flavorOnly.name + defaultBuildType.name.replaceFirstChar { it.uppercaseChar() },
            flavors = flavorOnly.flavors,
            buildType = defaultBuildType,
        )
    }

    /**
     * Finds a variant by name (case-insensitive).
     *
     * @param name The variant name to search for
     * @param allVariants All available variants
     * @return The matching variant, or null if not found
     */
    fun resolveVariantByName(name: String, allVariants: List<FlavorVariant>): FlavorVariant? = allVariants.find { it.name.equals(name, ignoreCase = true) }

    /**
     * Builds the variant name from a list of flavors.
     * The first flavor name is lowercase, subsequent names have their first letter capitalized.
     *
     * Example: [free, dev] -> "freeDev"
     */
    private fun buildVariantName(flavors: List<FlavorConfig>): String {
        if (flavors.isEmpty()) return ""
        if (flavors.size == 1) return flavors.first().name

        return flavors.mapIndexed { index, flavor ->
            if (index == 0) {
                flavor.name.lowercase()
            } else {
                flavor.name.replaceFirstChar { it.uppercaseChar() }
            }
        }.joinToString("")
    }

    /**
     * Computes the cartesian product of multiple lists.
     *
     * Example: [[a, b], [1, 2, 3]] -> [[a, 1], [a, 2], [a, 3], [b, 1], [b, 2], [b, 3]]
     */
    private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return emptyList()

        return lists.fold(listOf(emptyList())) { acc, list ->
            acc.flatMap { existing ->
                list.map { element ->
                    existing + element
                }
            }
        }
    }
}
