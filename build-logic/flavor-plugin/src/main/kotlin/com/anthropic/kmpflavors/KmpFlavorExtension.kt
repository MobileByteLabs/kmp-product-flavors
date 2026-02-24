/*
 * Copyright 2026 Anthropic, Inc.
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

package com.anthropic.kmpflavors

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Main extension for configuring KMP Product Flavors.
 *
 * This extension is registered as `kmpFlavors` in the build script and provides
 * the DSL for configuring product flavors in a Kotlin Multiplatform project.
 *
 * Example usage:
 * ```kotlin
 * kmpFlavors {
 *     // Generate BuildConfig object
 *     generateBuildConfig.set(true)
 *     buildConfigPackage.set("com.example.app")
 *     buildConfigClassName.set("AppConfig")
 *
 *     // Active flavor (can be overridden via -PkmpFlavor=xxx)
 *     activeFlavor.set("freeDev")
 *
 *     // Create intermediate source sets (webMain, nativeMain)
 *     createIntermediateSourceSets.set(true)
 *
 *     // Define dimensions
 *     flavorDimensions {
 *         register("tier") { priority.set(0) }
 *         register("environment") { priority.set(1) }
 *     }
 *
 *     // Define flavors
 *     flavors {
 *         register("free") {
 *             dimension.set("tier")
 *             isDefault.set(true)
 *             buildConfigField("Boolean", "IS_PREMIUM", "false")
 *         }
 *         register("paid") {
 *             dimension.set("tier")
 *             buildConfigField("Boolean", "IS_PREMIUM", "true")
 *         }
 *         register("dev") {
 *             dimension.set("environment")
 *             isDefault.set(true)
 *             buildConfigField("String", "BASE_URL", "\"https://dev-api.example.com\"")
 *         }
 *         register("prod") {
 *             dimension.set("environment")
 *             buildConfigField("String", "BASE_URL", "\"https://api.example.com\"")
 *         }
 *     }
 * }
 * ```
 */
abstract class KmpFlavorExtension @Inject constructor(
    objects: ObjectFactory,
) {
    /**
     * Whether to generate a BuildConfig Kotlin object.
     * When true, a Kotlin object will be generated in commonMain with
     * variant information and custom build config fields.
     *
     * Convention: true
     */
    abstract val generateBuildConfig: Property<Boolean>

    /**
     * The package name for the generated BuildConfig object.
     * Required when [generateBuildConfig] is true.
     *
     * Example: "com.example.app"
     */
    abstract val buildConfigPackage: Property<String>

    /**
     * The class name for the generated BuildConfig object.
     *
     * Convention: "FlavorConfig"
     */
    abstract val buildConfigClassName: Property<String>

    /**
     * The active flavor/variant name.
     * Can be overridden via the Gradle property `-PkmpFlavor=xxx`.
     *
     * If not set, defaults to the variant formed by default flavors
     * from each dimension, or the first flavor if no defaults are set.
     */
    abstract val activeFlavor: Property<String>

    /**
     * Whether to create intermediate source sets like webMain and nativeMain.
     * These provide shared code between related platforms:
     * - webMain: shared between js and wasmJs
     * - nativeMain: shared between iOS, macOS, Linux, Windows
     *
     * Convention: true
     */
    abstract val createIntermediateSourceSets: Property<Boolean>

    /**
     * Container for flavor dimensions.
     * Dimensions define independent axes of variation.
     */
    val flavorDimensions: NamedDomainObjectContainer<FlavorDimension> =
        objects.domainObjectContainer(FlavorDimension::class.java)

    /**
     * Container for flavor configurations.
     * Each flavor belongs to a dimension.
     */
    val flavors: NamedDomainObjectContainer<FlavorConfig> =
        objects.domainObjectContainer(FlavorConfig::class.java)

    /**
     * Configure flavor dimensions using a DSL block.
     */
    fun flavorDimensions(action: Action<NamedDomainObjectContainer<FlavorDimension>>) {
        action.execute(flavorDimensions)
    }

    /**
     * Configure flavors using a DSL block.
     */
    fun flavors(action: Action<NamedDomainObjectContainer<FlavorConfig>>) {
        action.execute(flavors)
    }

    init {
        // Set conventions
        generateBuildConfig.convention(true)
        buildConfigClassName.convention("FlavorConfig")
        createIntermediateSourceSets.convention(true)
    }
}
