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

package org.convention

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension

/**
 * Centralized KMP Flavor configuration for the entire project.
 *
 * This object defines the product flavors and dimensions available across
 * all modules in the project. It replaces the Android-only AppFlavor.kt
 * approach and provides true cross-platform flavor support.
 *
 * ## Configuration Structure
 *
 * Flavors are organized into dimensions. Each dimension represents an
 * independent axis of variation:
 *
 * ```
 * Dimensions: CONTENT_TYPE × ENVIRONMENT
 *
 * Resulting Variants:
 * - demoDebug, demoProd (if using CONTENT_TYPE + BUILD_TYPE)
 * - demoDevDebug, demoDevRelease, etc. (if using multiple dimensions)
 * ```
 *
 * ## Customization
 *
 * To customize the flavors for your project, modify the enums in this file:
 *
 * ```kotlin
 * enum class Flavor(...) {
 *     // Add your custom flavors here
 *     FREE(Dimension.TIER, isDefault = true, applicationIdSuffix = ".free"),
 *     PAID(Dimension.TIER, applicationIdSuffix = ".paid"),
 * }
 * ```
 *
 * @see configureKmpFlavors
 */
object KmpFlavors {

    /**
     * Flavor dimensions used across the project.
     *
     * Dimensions define independent axes of variation. Lower priority numbers
     * are applied first in the variant name (e.g., priority 0 + priority 1 = "freeDev").
     */
    enum class Dimension(val priority: Int) {
        /**
         * Content type dimension - differentiates between demo/mock data and production data.
         */
        CONTENT_TYPE(0),

        /**
         * Environment dimension - differentiates between development, staging, and production.
         * Optional: Only use if your project needs environment-specific configuration.
         */
        ENVIRONMENT(1),

        /**
         * Tier dimension - differentiates between free and paid versions.
         * Optional: Only use if your project has monetization tiers.
         */
        TIER(2),
    }

    /**
     * Product flavors available in the project.
     *
     * Each flavor belongs to exactly one dimension and can define:
     * - Application ID suffix (for Android)
     * - Bundle ID suffix (for iOS)
     * - Whether it's the default for its dimension
     */
    enum class Flavor(
        val dimension: Dimension,
        val isDefault: Boolean = false,
        val applicationIdSuffix: String? = null,
        val bundleIdSuffix: String? = null,
        val versionNameSuffix: String? = null,
    ) {
        // ==========================================
        // CONTENT_TYPE Dimension
        // ==========================================

        /**
         * Demo flavor - uses mock/local data for demonstration purposes.
         */
        DEMO(
            dimension = Dimension.CONTENT_TYPE,
            isDefault = true,
            applicationIdSuffix = ".demo",
            bundleIdSuffix = ".demo",
        ),

        /**
         * Production flavor - uses real backend data.
         */
        PROD(
            dimension = Dimension.CONTENT_TYPE,
            isDefault = false,
            applicationIdSuffix = null,
            bundleIdSuffix = null,
        ),

        // ==========================================
        // ENVIRONMENT Dimension (Optional)
        // ==========================================

        /**
         * Development environment - connects to dev servers.
         */
        DEV(
            dimension = Dimension.ENVIRONMENT,
            isDefault = true,
            versionNameSuffix = "-dev",
        ),

        /**
         * Staging environment - connects to staging servers.
         */
        STAGING(
            dimension = Dimension.ENVIRONMENT,
            isDefault = false,
            versionNameSuffix = "-staging",
        ),

        /**
         * Production environment - connects to production servers.
         */
        PRODUCTION(
            dimension = Dimension.ENVIRONMENT,
            isDefault = false,
            versionNameSuffix = null,
        ),

        // ==========================================
        // TIER Dimension (Optional)
        // ==========================================

        /**
         * Free tier - limited features, may include ads.
         */
        FREE(
            dimension = Dimension.TIER,
            isDefault = true,
            applicationIdSuffix = ".free",
            bundleIdSuffix = ".free",
        ),

        /**
         * Paid/Premium tier - full features, no ads.
         */
        PAID(
            dimension = Dimension.TIER,
            isDefault = false,
            applicationIdSuffix = null,
            bundleIdSuffix = null,
        ),
    }

    /**
     * Default dimensions to use when none are specified.
     * Projects can override this by calling [configureKmpFlavors] with custom dimensions.
     */
    val defaultDimensions: Set<Dimension> = setOf(Dimension.CONTENT_TYPE)

    /**
     * Default flavors to use when none are specified.
     * Only includes flavors from the default dimensions.
     */
    val defaultFlavors: Set<Flavor> = Flavor.entries
        .filter { it.dimension in defaultDimensions }
        .toSet()

    /**
     * Get all flavors for a specific dimension.
     */
    fun flavorsForDimension(dimension: Dimension): List<Flavor> = Flavor.entries.filter { it.dimension == dimension }

    /**
     * Get the default flavor for a dimension.
     */
    fun defaultFlavorForDimension(dimension: Dimension): Flavor? = Flavor.entries.find { it.dimension == dimension && it.isDefault }
}

/**
 * Configure KMP Flavors with project-wide settings.
 *
 * This function applies the centralized flavor configuration to the
 * KmpFlavorExtension. It can be called directly or through the
 * [KMPFlavorsConventionPlugin].
 *
 * ## Example
 *
 * ```kotlin
 * // Using defaults (CONTENT_TYPE dimension with DEMO/PROD flavors)
 * configureKmpFlavors(extension)
 *
 * // Using custom dimensions
 * configureKmpFlavors(
 *     extension = extension,
 *     dimensions = setOf(KmpFlavors.Dimension.CONTENT_TYPE, KmpFlavors.Dimension.ENVIRONMENT),
 *     flavors = KmpFlavors.Flavor.entries.filter {
 *         it.dimension in setOf(KmpFlavors.Dimension.CONTENT_TYPE, KmpFlavors.Dimension.ENVIRONMENT)
 *     }.toSet()
 * )
 * ```
 *
 * @param extension The KmpFlavorExtension to configure
 * @param dimensions The dimensions to register
 * @param flavors The flavors to register (must belong to the specified dimensions)
 * @param generateBuildConfig Whether to generate a BuildConfig object
 * @param buildConfigPackage The package name for the generated BuildConfig
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

/**
 * Extension function to configure KMP Flavors with a simple DSL.
 *
 * ```kotlin
 * extensions.configure<KmpFlavorExtension> {
 *     useProjectFlavors(
 *         dimensions = setOf(Dimension.CONTENT_TYPE, Dimension.TIER)
 *     )
 * }
 * ```
 */
fun KmpFlavorExtension.useProjectFlavors(dimensions: Set<KmpFlavors.Dimension> = KmpFlavors.defaultDimensions, buildConfigPackage: String? = null) {
    configureKmpFlavors(
        extension = this,
        dimensions = dimensions,
        flavors = KmpFlavors.Flavor.entries.filter { it.dimension in dimensions }.toSet(),
        generateBuildConfig = true,
        buildConfigPackage = buildConfigPackage,
    )
}
