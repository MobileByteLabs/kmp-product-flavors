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

import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration for a build type (e.g., debug, release).
 *
 * Build types work similarly to Android's build types and can be combined with
 * product flavors to create build variants. For example, with flavors [free, paid]
 * and build types [debug, release], you get variants like "freeDebug", "freeRelease",
 * "paidDebug", "paidRelease".
 *
 * Example usage:
 * ```kotlin
 * kmpFlavors {
 *     buildTypes {
 *         register("debug") {
 *             isDefault.set(true)
 *             isDebuggable.set(true)
 *             buildConfigField("Boolean", "DEBUG", "true")
 *             applicationIdSuffix.set(".debug")
 *         }
 *         register("release") {
 *             isDebuggable.set(false)
 *             isMinifyEnabled.set(true)
 *             buildConfigField("Boolean", "DEBUG", "false")
 *         }
 *     }
 * }
 * ```
 */
open class BuildTypeConfig @Inject constructor(private val buildTypeName: String, objects: ObjectFactory) : Named {

    /**
     * Returns the name of this build type.
     */
    override fun getName(): String = buildTypeName

    /**
     * Whether this build type is the default one.
     * Default: false
     */
    val isDefault: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(false)

    /**
     * Whether this build type is debuggable.
     * This affects whether debuggers can attach to the application.
     * Default: true for "debug", false for others
     */
    val isDebuggable: Property<Boolean> = objects.property(Boolean::class.javaObjectType)
        .convention(buildTypeName.equals("debug", ignoreCase = true))

    /**
     * Whether minification is enabled for this build type.
     * Default: false for "debug", true for "release"
     */
    val isMinifyEnabled: Property<Boolean> = objects.property(Boolean::class.javaObjectType)
        .convention(buildTypeName.equals("release", ignoreCase = true))

    /**
     * Custom build config fields for this build type.
     */
    val buildConfigFields: MapProperty<String, BuildConfigField> =
        objects.mapProperty(String::class.java, BuildConfigField::class.java)

    /**
     * Suffix to append to the application ID for this build type.
     */
    val applicationIdSuffix: Property<String> = objects.property(String::class.java)

    /**
     * Suffix to append to the bundle ID for this build type.
     */
    val bundleIdSuffix: Property<String> = objects.property(String::class.java)

    /**
     * Suffix to append to the version name for this build type.
     */
    val versionNameSuffix: Property<String> = objects.property(String::class.java)

    /**
     * Dependencies specific to this build type.
     */
    val buildTypeDependencies: ListProperty<FlavorDependency> =
        objects.listProperty(FlavorDependency::class.java)

    /**
     * Fallback build types for dependency resolution.
     */
    val matchingFallbacks: ListProperty<String> =
        objects.listProperty(String::class.java)

    /**
     * The signing config name to use for this build type.
     */
    val signingConfig: Property<String> = objects.property(String::class.java)

    /**
     * Adds a custom build config field to this build type.
     *
     * @param type The Kotlin type (e.g., "String", "Boolean", "Int")
     * @param name The constant name
     * @param value The value as a string
     */
    fun buildConfigField(type: String, name: String, value: String) {
        buildConfigFields.put(name, BuildConfigField(type, name, value))
    }

    /**
     * Adds a build type-specific dependency.
     *
     * @param configuration The Gradle configuration (e.g., "implementation")
     * @param notation The dependency notation
     */
    fun dependency(configuration: String, notation: String) {
        buildTypeDependencies.add(FlavorDependency(configuration, notation))
    }

    /**
     * Sets matching fallbacks for dependency resolution.
     *
     * @param fallbacks The fallback build type names
     */
    fun matchingFallbacks(vararg fallbacks: String) {
        matchingFallbacks.addAll(fallbacks.toList())
    }
}
