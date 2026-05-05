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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for KMP library modules.
 *
 * This plugin applies:
 * - Kotlin Multiplatform plugin
 * - KMP Product Flavors (via convention plugin)
 *
 * ## Usage
 *
 * ```kotlin
 * plugins {
 *     id("org.convention.kmp.library")
 * }
 * ```
 *
 * Modules using this plugin automatically get flavor support with the
 * project-wide flavor configuration.
 */
class KMPLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                // Apply Kotlin Multiplatform
                apply("org.jetbrains.kotlin.multiplatform")

                // Apply KMP Flavors convention plugin
                apply("org.convention.kmp.flavors")
            }

            // Configure Kotlin Multiplatform targets
            extensions.configure<KotlinMultiplatformExtension> {
                jvm("desktop")

                // Compiler options
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }

            // Module-specific flavor configuration (optional)
            extensions.configure<KmpFlavorExtension> {
                // Override build config package for this module
                buildConfigPackage.set("${target.group}.${target.name.replace("-", ".")}")
            }
        }
    }
}
