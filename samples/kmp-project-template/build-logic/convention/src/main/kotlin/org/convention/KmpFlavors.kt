/*
 * Copyright 2026 Mifos Initiative
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

package org.convention

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension

/**
 * Centralized KMP Flavor configuration for the entire project.
 *
 * This object defines the product flavors and dimensions available across
 * all modules in the project, providing a single source of truth for
 * flavor configuration across Android, iOS, Desktop, and Web targets.
 *
 * ## Compatibility
 *
 * This configuration is designed to be compatible with the existing
 * [AppFlavor] enum used for Android product flavors, but extends
 * flavor support to all Kotlin Multiplatform targets.
 *
 * ## Usage
 *
 * The flavors are automatically applied by [KMPFlavorsConventionPlugin].
 * Modules can access flavor information via generated BuildConfig:
 *
 * ```kotlin
 * // In commonMain
 * if (FlavorConfig.IS_DEMO) {
 *     // Use mock data
 * } else {
 *     // Use production API
 * }
 * ```
 */
object KmpFlavors {

    /**
     * Flavor dimensions used across the project.
     *
     * Each dimension represents a different axis of variation.
     * The priority determines the order when combining dimensions
     * to create variant names (lower priority = comes first).
     */
    enum class Dimension(val priority: Int) {
        /** Content type dimension - demo vs production data */
        CONTENT_TYPE(0),
    }

    /**
     * Product flavors available in the project.
     *
     * These flavors match the existing [FlavorDimension] and [AppFlavor]
     * enums for Android compatibility, but add multiplatform support.
     *
     * @property dimension The dimension this flavor belongs to
     * @property isDefault Whether this is the default flavor for its dimension
     * @property applicationIdSuffix Suffix added to Android applicationId
     * @property bundleIdSuffix Suffix added to iOS bundle identifier
     * @property versionNameSuffix Suffix added to version name
     */
    @Suppress("EnumEntryName")
    enum class Flavor(
        val dimension: Dimension,
        val isDefault: Boolean = false,
        val applicationIdSuffix: String? = null,
        val bundleIdSuffix: String? = null,
        val versionNameSuffix: String? = null,
    ) {
        /**
         * Demo flavor - uses mock/local data.
         *
         * Use this flavor during development or for showcase purposes.
         * The app will use local mock data instead of connecting to
         * production backend servers.
         */
        demo(
            dimension = Dimension.CONTENT_TYPE,
            isDefault = true,
            applicationIdSuffix = ".demo",
            bundleIdSuffix = ".demo",
        ),

        /**
         * Production flavor - uses real backend.
         *
         * Use this flavor for production builds that connect to
         * the real backend servers.
         */
        prod(
            dimension = Dimension.CONTENT_TYPE,
            isDefault = false,
        ),
    }

    /** Default dimensions to use across the project */
    val defaultDimensions: Set<Dimension> = setOf(Dimension.CONTENT_TYPE)

    /** Default flavors (filtered by default dimensions) */
    val defaultFlavors: Set<Flavor> = Flavor.values()
        .filter { it.dimension in defaultDimensions }
        .toSet()
}

/**
 * Configure KMP Flavors with project-wide settings.
 *
 * This function is called by [KMPFlavorsConventionPlugin] to configure
 * the kmp-product-flavors plugin with the centralized flavor definitions.
 *
 * @param extension The KMP Flavor extension to configure
 * @param dimensions The dimensions to register
 * @param flavors The flavors to register
 * @param generateBuildConfig Whether to generate BuildConfig object
 * @param buildConfigPackage Package for the generated BuildConfig
 */
fun configureKmpFlavors(
    extension: KmpFlavorExtension,
    dimensions: Set<KmpFlavors.Dimension> = KmpFlavors.defaultDimensions,
    flavors: Set<KmpFlavors.Flavor> = KmpFlavors.defaultFlavors,
    generateBuildConfig: Boolean = true,
    buildConfigPackage: String? = null,
) {
    extension.apply {
        this.generateBuildConfig.set(generateBuildConfig)
        buildConfigPackage?.let { this.buildConfigPackage.set(it) }

        // Register dimensions
        flavorDimensions {
            dimensions.forEach { dim ->
                register(dim.name.lowercase()) {
                    priority.set(dim.priority)
                }
            }
        }

        // Register flavors
        this.flavors {
            flavors
                .filter { it.dimension in dimensions }
                .forEach { flavor ->
                    register(flavor.name.lowercase()) {
                        dimension.set(flavor.dimension.name.lowercase())
                        isDefault.set(flavor.isDefault)
                        flavor.applicationIdSuffix?.let { applicationIdSuffix.set(it) }
                        flavor.bundleIdSuffix?.let { bundleIdSuffix.set(it) }
                        flavor.versionNameSuffix?.let { versionNameSuffix.set(it) }
                    }
                }
        }
    }
}
