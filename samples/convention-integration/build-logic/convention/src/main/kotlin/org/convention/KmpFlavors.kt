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
 * all modules in the project. Customize the enums to match your project's needs.
 */
object KmpFlavors {

    /**
     * Flavor dimensions used across the project.
     */
    enum class Dimension(val priority: Int) {
        /** Content type dimension - demo vs production data */
        CONTENT_TYPE(0),
    }

    /**
     * Product flavors available in the project.
     */
    enum class Flavor(
        val dimension: Dimension,
        val isDefault: Boolean = false,
        val applicationIdSuffix: String? = null,
        val bundleIdSuffix: String? = null,
        val versionNameSuffix: String? = null,
    ) {
        /** Demo flavor - uses mock/local data */
        DEMO(
            dimension = Dimension.CONTENT_TYPE,
            isDefault = true,
            applicationIdSuffix = ".demo",
            bundleIdSuffix = ".demo",
        ),

        /** Production flavor - uses real backend */
        PROD(
            dimension = Dimension.CONTENT_TYPE,
            isDefault = false,
        ),
    }

    /** Default dimensions to use */
    val defaultDimensions: Set<Dimension> = setOf(Dimension.CONTENT_TYPE)

    /** Default flavors (from default dimensions) */
    val defaultFlavors: Set<Flavor> = Flavor.values()
        .filter { it.dimension in defaultDimensions }
        .toSet()
}

/**
 * Configure KMP Flavors with project-wide settings.
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
