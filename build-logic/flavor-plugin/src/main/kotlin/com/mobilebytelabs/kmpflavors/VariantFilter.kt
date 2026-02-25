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

/**
 * Context for filtering variants during resolution.
 *
 * This class provides information about a variant being evaluated and allows
 * excluding it from the final variant list.
 *
 * Example usage:
 * ```kotlin
 * kmpFlavors {
 *     variantFilter {
 *         // Exclude freeProd variant
 *         if (flavorNames.containsAll(listOf("free", "prod"))) {
 *             exclude()
 *         }
 *
 *         // Or using variant name
 *         if (variantName == "freeProd") {
 *             exclude()
 *         }
 *     }
 * }
 * ```
 *
 * @property variantName The full variant name (e.g., "freeDev", "paidProd")
 * @property flavorNames The list of flavor names in this variant
 * @property flavors The list of FlavorConfig objects in this variant
 */
class VariantFilter(val variantName: String, val flavorNames: List<String>, val flavors: List<FlavorConfig>) {
    private var excluded = false

    /**
     * Marks this variant to be excluded from the build.
     * Once excluded, the variant will not appear in the list of available variants.
     */
    fun exclude() {
        excluded = true
    }

    /**
     * Returns whether this variant has been marked for exclusion.
     */
    internal fun isExcluded(): Boolean = excluded

    /**
     * Checks if this variant contains a specific flavor.
     *
     * @param flavorName The flavor name to check
     * @return true if the variant contains the flavor
     */
    fun hasFlavor(flavorName: String): Boolean = flavorNames.contains(flavorName)

    /**
     * Checks if this variant contains all the specified flavors.
     *
     * @param names The flavor names to check
     * @return true if the variant contains all the specified flavors
     */
    fun hasAllFlavors(vararg names: String): Boolean = flavorNames.containsAll(names.toList())

    /**
     * Checks if this variant contains any of the specified flavors.
     *
     * @param names The flavor names to check
     * @return true if the variant contains at least one of the specified flavors
     */
    fun hasAnyFlavor(vararg names: String): Boolean = names.any { it in flavorNames }

    /**
     * Gets the flavor from a specific dimension.
     *
     * @param dimensionName The dimension name
     * @return The flavor name from that dimension, or null if not found
     */
    fun getFlavorFromDimension(dimensionName: String): String? = flavors.find { it.dimension.orNull == dimensionName }?.name
}
