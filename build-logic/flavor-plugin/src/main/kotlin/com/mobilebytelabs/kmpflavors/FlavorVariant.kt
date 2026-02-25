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
 * Represents a resolved combination of flavors from each dimension.
 *
 * When you have multiple dimensions, each variant is a specific combination
 * of one flavor from each dimension. For example, with dimensions "tier" (free, paid)
 * and "environment" (dev, prod), you get variants like "freeDev", "freeProd", etc.
 *
 * @property name The combined variant name (e.g., "freeDev")
 * @property flavors The individual flavors that make up this variant, ordered by dimension priority
 */
data class FlavorVariant(val name: String, val flavors: List<FlavorConfig>) {
    /**
     * Merged build config fields from all flavors in this variant.
     * Later dimensions (higher priority) override earlier ones.
     */
    val mergedBuildConfigFields: Map<String, BuildConfigField> by lazy {
        flavors.fold(mutableMapOf<String, BuildConfigField>()) { acc, flavor ->
            flavor.buildConfigFields.get().forEach { (key, field) ->
                acc[key] = field
            }
            acc
        }
    }

    /**
     * Merged extras from all flavors in this variant.
     * Later dimensions (higher priority) override earlier ones.
     */
    val mergedExtras: Map<String, String> by lazy {
        flavors.fold(mutableMapOf<String, String>()) { acc, flavor ->
            flavor.extras.get().forEach { (key, value) ->
                acc[key] = value
            }
            acc
        }
    }

    /**
     * Combined application ID suffix from all flavors.
     * Joins all non-null suffixes in order.
     */
    val combinedApplicationIdSuffix: String by lazy {
        flavors.mapNotNull { it.applicationIdSuffix.orNull }
            .joinToString("")
    }

    /**
     * Combined bundle ID suffix from all flavors.
     * Joins all non-null suffixes in order.
     */
    val combinedBundleIdSuffix: String by lazy {
        flavors.mapNotNull { it.bundleIdSuffix.orNull }
            .joinToString("")
    }

    /**
     * Combined desktop window title suffix from all flavors.
     * Joins all non-null suffixes with spaces.
     */
    val combinedDesktopTitleSuffix: String by lazy {
        flavors.mapNotNull { it.desktopWindowTitleSuffix.orNull }
            .joinToString(" ")
    }

    /**
     * Combined web page title suffix from all flavors.
     * Joins all non-null suffixes with spaces.
     */
    val combinedWebTitleSuffix: String by lazy {
        flavors.mapNotNull { it.webTitleSuffix.orNull }
            .joinToString(" ")
    }

    /**
     * Combined version name suffix from all flavors.
     * Joins all non-null suffixes in order.
     */
    val combinedVersionNameSuffix: String by lazy {
        flavors.mapNotNull { it.versionNameSuffix.orNull }
            .joinToString("")
    }

    /**
     * All flavor dependencies from all flavors in this variant.
     */
    val allDependencies: List<FlavorDependency> by lazy {
        flavors.flatMap { it.flavorDependencies.get() }
    }

    /**
     * List of flavor names in this variant.
     */
    val flavorNames: List<String> by lazy {
        flavors.map { it.name }
    }

    /**
     * Merged matching fallbacks from all flavors in this variant.
     * This is used for dependency resolution when a dependency module
     * doesn't have a matching flavor configuration.
     *
     * Example: If "paid" flavor has matchingFallbacks = ["free"],
     * then when depending on a module that only has "free" flavor,
     * the dependency will resolve to the "free" variant.
     */
    val mergedMatchingFallbacks: List<String> by lazy {
        flavors.flatMap { it.matchingFallbacks.getOrElse(emptyList()) }.distinct()
    }

    /**
     * Gets the matchingFallbacks for a specific dimension.
     *
     * @param dimensionName The dimension to get fallbacks for
     * @return List of fallback flavor names for that dimension
     */
    fun getMatchingFallbacksForDimension(dimensionName: String): List<String> {
        val dimensionFlavor = flavors.find { it.dimension.orNull == dimensionName }
        return dimensionFlavor?.matchingFallbacks?.getOrElse(emptyList()) ?: emptyList()
    }
}
