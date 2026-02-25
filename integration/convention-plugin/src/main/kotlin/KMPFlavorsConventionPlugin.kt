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

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.convention.KmpFlavors
import org.convention.configureKmpFlavors
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin that applies and configures the KMP Product Flavors plugin.
 *
 * This plugin provides a centralized, project-wide configuration for product flavors
 * in Kotlin Multiplatform projects. It wraps the kmp-product-flavors library and
 * applies default settings from [KmpFlavors].
 *
 * ## Usage
 *
 * Apply this plugin in your module's build.gradle.kts:
 * ```kotlin
 * plugins {
 *     id("org.convention.kmp.flavors")
 * }
 * ```
 *
 * Or apply it through [KMPLibraryConventionPlugin] which includes it automatically.
 *
 * ## Configuration
 *
 * Override defaults in your module:
 * ```kotlin
 * kmpFlavors {
 *     flavors {
 *         named("demo") {
 *             buildConfigField("String", "API_URL", "\"https://demo.example.com\"")
 *         }
 *     }
 * }
 * ```
 *
 * @see KmpFlavors
 * @see configureKmpFlavors
 */
class KMPFlavorsConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            // Apply the KMP Product Flavors plugin
            pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")

            // Configure with project-wide defaults
            extensions.configure<KmpFlavorExtension> {
                // Apply centralized configuration
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
     * Infers the build config package name from the project.
     *
     * Uses the project group and name to generate a sensible package name.
     * Falls back to a default if group is not set.
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
            "com.example.$name"
        }
    }
}
