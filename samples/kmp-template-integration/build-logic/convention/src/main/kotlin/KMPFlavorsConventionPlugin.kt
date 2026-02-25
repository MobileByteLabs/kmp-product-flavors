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

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import com.mobilebytelabs.kmpflavors.KmpFlavorPlugin
import org.convention.KmpFlavors
import org.convention.configureKmpFlavors
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin that applies and configures the KMP Product Flavors plugin.
 *
 * This plugin provides a centralized, project-wide configuration for product flavors
 * in Kotlin Multiplatform projects, extending flavor support beyond just Android.
 *
 * ## Usage
 *
 * ```kotlin
 * plugins {
 *     id("org.convention.kmp.flavors")
 * }
 * ```
 *
 * ## Features
 *
 * - Automatic flavor source sets (commonDemo, commonProd, iosProd, etc.)
 * - BuildConfig generation for all platforms
 * - Platform-specific suffix properties (applicationIdSuffix, bundleIdSuffix)
 * - Centralized flavor configuration across all modules
 *
 * @see KmpFlavors for centralized flavor definitions
 */
class KMPFlavorsConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            // Apply the KMP Product Flavors plugin class directly
            // (plugin ID resolution not available when using jar file dependency)
            pluginManager.apply(KmpFlavorPlugin::class.java)

            // Configure with project-wide defaults
            extensions.configure<KmpFlavorExtension> {
                configureKmpFlavors(
                    extension = this,
                    dimensions = KmpFlavors.defaultDimensions,
                    flavors = KmpFlavors.defaultFlavors,
                    generateBuildConfig = true,
                    buildConfigPackage = inferBuildConfigPackage(target),
                )
            }
        }
    }

    /**
     * Infers the build config package from project group and name.
     *
     * Falls back to "org.mifos.{project-name}" if group is not set.
     */
    private fun inferBuildConfigPackage(project: Project): String {
        val group = project.group.toString()
        val name = project.name
            .replace("-", ".")
            .replace("_", ".")
            .lowercase()

        return if (group.isNotEmpty() && group != project.name) {
            "$group.$name"
        } else {
            "org.mifos.$name"
        }
    }
}
