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
 * Represents a complete build variant, combining flavors with an optional build type.
 *
 * A build variant is the combination of:
 * 1. A flavor variant (combination of flavors from each dimension)
 * 2. An optional build type (debug, release, etc.)
 *
 * For example, with flavors [free, paid] and build types [debug, release]:
 * - freeDebug, freeRelease, paidDebug, paidRelease
 *
 * Without build types, the build variant is just the flavor variant:
 * - free, paid
 *
 * @property name The full build variant name (e.g., "freeDebug")
 * @property flavorVariant The underlying flavor variant
 * @property buildType The build type, or null if build types are disabled
 */
data class BuildVariant(val name: String, val flavorVariant: FlavorVariant, val buildType: BuildTypeConfig?) {
    /**
     * All flavors in this build variant.
     */
    val flavors: List<FlavorConfig> get() = flavorVariant.flavors

    /**
     * List of flavor names in this build variant.
     */
    val flavorNames: List<String> get() = flavorVariant.flavorNames

    /**
     * Whether this variant uses a debuggable build type.
     */
    val isDebuggable: Boolean get() = buildType?.isDebuggable?.getOrElse(false) ?: false

    /**
     * Whether this variant uses minification.
     */
    val isMinifyEnabled: Boolean get() = buildType?.isMinifyEnabled?.getOrElse(false) ?: false

    /**
     * Merged build config fields from flavors and build type.
     * Build type fields override flavor fields.
     */
    val mergedBuildConfigFields: Map<String, BuildConfigField> by lazy {
        val fields = flavorVariant.mergedBuildConfigFields.toMutableMap()
        buildType?.buildConfigFields?.get()?.forEach { (key, field) ->
            fields[key] = field
        }
        fields
    }

    /**
     * Combined application ID suffix from flavors and build type.
     */
    val combinedApplicationIdSuffix: String by lazy {
        buildString {
            append(flavorVariant.combinedApplicationIdSuffix)
            buildType?.applicationIdSuffix?.orNull?.let { append(it) }
        }
    }

    /**
     * Combined bundle ID suffix from flavors and build type.
     */
    val combinedBundleIdSuffix: String by lazy {
        buildString {
            append(flavorVariant.combinedBundleIdSuffix)
            buildType?.bundleIdSuffix?.orNull?.let { append(it) }
        }
    }

    /**
     * Combined version name suffix from flavors and build type.
     */
    val combinedVersionNameSuffix: String by lazy {
        buildString {
            append(flavorVariant.combinedVersionNameSuffix)
            buildType?.versionNameSuffix?.orNull?.let { append(it) }
        }
    }

    /**
     * All dependencies from flavors and build type.
     */
    val allDependencies: List<FlavorDependency> by lazy {
        val deps = flavorVariant.allDependencies.toMutableList()
        buildType?.buildTypeDependencies?.get()?.let { deps.addAll(it) }
        deps
    }

    /**
     * Merged matching fallbacks from flavors and build type.
     */
    val mergedMatchingFallbacks: List<String> by lazy {
        val fallbacks = flavorVariant.mergedMatchingFallbacks.toMutableList()
        buildType?.matchingFallbacks?.getOrElse(emptyList())?.let { fallbacks.addAll(it) }
        fallbacks.distinct()
    }

    companion object {
        /**
         * Creates a build variant from a flavor variant without a build type.
         */
        fun fromFlavorVariant(flavorVariant: FlavorVariant): BuildVariant = BuildVariant(
            name = flavorVariant.name,
            flavorVariant = flavorVariant,
            buildType = null,
        )

        /**
         * Creates a build variant by combining a flavor variant with a build type.
         */
        fun fromFlavorVariantAndBuildType(flavorVariant: FlavorVariant, buildType: BuildTypeConfig): BuildVariant {
            val name = if (flavorVariant.name.isEmpty()) {
                buildType.name
            } else {
                "${flavorVariant.name}${buildType.name.replaceFirstChar { it.uppercaseChar() }}"
            }
            return BuildVariant(
                name = name,
                flavorVariant = flavorVariant,
                buildType = buildType,
            )
        }
    }
}
