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
import com.mobilebytelabs.kmpflavors.FlavorDimension
import com.mobilebytelabs.kmpflavors.FlavorVariant

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
     * @return List of all possible variant combinations
     */
    fun resolveAllVariants(
        dimensions: Collection<FlavorDimension>,
        flavors: Collection<FlavorConfig>,
    ): List<FlavorVariant> {
        if (flavors.isEmpty()) {
            return emptyList()
        }

        // If no dimensions, each flavor is its own variant
        if (dimensions.isEmpty()) {
            return flavors.map { flavor ->
                FlavorVariant(
                    name = flavor.name,
                    flavors = listOf(flavor),
                )
            }
        }

        // Sort dimensions by priority
        val sortedDimensions = dimensions.sortedBy { it.priority.getOrElse(0) }

        // Group flavors by dimension
        val flavorsByDimension = sortedDimensions.map { dimension ->
            val dimensionFlavors = flavors.filter { it.dimension.orNull == dimension.name }
            if (dimensionFlavors.isEmpty()) {
                throw IllegalStateException(
                    "Dimension '${dimension.name}' has no flavors assigned to it. " +
                        "Make sure each flavor has dimension.set(\"${dimension.name}\").",
                )
            }
            dimensionFlavors
        }

        // Compute cartesian product
        val combinations = cartesianProduct(flavorsByDimension)

        // Create variants from combinations
        return combinations.map { flavorList ->
            val variantName = buildVariantName(flavorList)
            FlavorVariant(
                name = variantName,
                flavors = flavorList,
            )
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
    ): FlavorVariant? {
        if (flavors.isEmpty()) {
            return null
        }

        // If no dimensions, return the first default flavor or first flavor
        if (dimensions.isEmpty()) {
            val defaultFlavor = flavors.find { it.isDefault.getOrElse(false) }
                ?: flavors.firstOrNull()
                ?: return null
            return FlavorVariant(
                name = defaultFlavor.name,
                flavors = listOf(defaultFlavor),
            )
        }

        // Sort dimensions by priority
        val sortedDimensions = dimensions.sortedBy { it.priority.getOrElse(0) }

        // Get default flavor from each dimension
        val defaultFlavors = sortedDimensions.mapNotNull { dimension ->
            val dimensionFlavors = flavors.filter { it.dimension.orNull == dimension.name }
            dimensionFlavors.find { it.isDefault.getOrElse(false) }
                ?: dimensionFlavors.firstOrNull()
        }

        if (defaultFlavors.size != sortedDimensions.size) {
            return null
        }

        val variantName = buildVariantName(defaultFlavors)
        return FlavorVariant(
            name = variantName,
            flavors = defaultFlavors,
        )
    }

    /**
     * Finds a variant by name (case-insensitive).
     *
     * @param name The variant name to search for
     * @param allVariants All available variants
     * @return The matching variant, or null if not found
     */
    fun resolveVariantByName(
        name: String,
        allVariants: List<FlavorVariant>,
    ): FlavorVariant? {
        return allVariants.find { it.name.equals(name, ignoreCase = true) }
    }

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
