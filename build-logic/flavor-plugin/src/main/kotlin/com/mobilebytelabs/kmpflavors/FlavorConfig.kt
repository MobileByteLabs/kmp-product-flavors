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
import java.io.Serializable
import javax.inject.Inject

/**
 * Represents a custom build config field that will be generated in the BuildConfig object.
 *
 * @property type The Kotlin type of the field (e.g., "String", "Boolean", "Int")
 * @property name The name of the constant
 * @property value The value as a string (will be formatted based on type)
 */
data class BuildConfigField(val type: String, val name: String, val value: String) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Represents a dependency that should only be included for a specific flavor.
 *
 * @property configuration The Gradle configuration (e.g., "implementation", "api")
 * @property notation The dependency notation (e.g., "com.example:library:1.0.0")
 */
data class FlavorDependency(val configuration: String, val notation: String) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Configuration for a single product flavor.
 *
 * This is a Gradle managed abstract class that allows users to configure individual
 * flavors in the build script. Each flavor belongs to a dimension and can define
 * custom build config fields, platform-specific suffixes, and per-flavor dependencies.
 *
 * Example usage:
 * ```kotlin
 * kmpFlavors {
 *     flavors {
 *         register("free") {
 *             dimension.set("tier")
 *             isDefault.set(true)
 *             buildConfigField("Boolean", "IS_PREMIUM", "false")
 *             buildConfigField("Int", "MAX_ITEMS", "10")
 *             applicationIdSuffix.set(".free")
 *             dependency("implementation", "com.google.ads:ads-sdk:1.0.0")
 *         }
 *     }
 * }
 * ```
 */
open class FlavorConfig @Inject constructor(private val flavorName: String, objects: ObjectFactory) : Named {
    /**
     * Returns the name of this flavor.
     */
    override fun getName(): String = flavorName

    /**
     * The dimension this flavor belongs to.
     * Must reference a dimension declared in [KmpFlavorExtension.flavorDimensions].
     */
    val dimension: Property<String> = objects.property(String::class.java)

    /**
     * Whether this flavor is the default for its dimension.
     * Each dimension must have exactly one default flavor.
     * Convention: false
     */
    val isDefault: Property<Boolean> = objects.property(Boolean::class.javaObjectType).convention(false)

    /**
     * Custom build config fields to include in the generated BuildConfig object.
     * These will be available as `const val` properties in the generated Kotlin object.
     */
    val buildConfigFields: MapProperty<String, BuildConfigField> =
        objects.mapProperty(String::class.java, BuildConfigField::class.java)

    /**
     * Suffix to append to the Android application ID.
     * Example: ".free" results in "com.example.app.free"
     */
    val applicationIdSuffix: Property<String> = objects.property(String::class.java)

    /**
     * Suffix to append to the iOS bundle ID.
     * Example: ".free" results in "com.example.app.free"
     */
    val bundleIdSuffix: Property<String> = objects.property(String::class.java)

    /**
     * Suffix to append to the desktop window title.
     */
    val desktopWindowTitleSuffix: Property<String> = objects.property(String::class.java)

    /**
     * Suffix to append to the web page title.
     */
    val webTitleSuffix: Property<String> = objects.property(String::class.java)

    /**
     * Suffix to append to the version name.
     * Example: "-free" results in "1.0.0-free"
     */
    val versionNameSuffix: Property<String> = objects.property(String::class.java)

    /**
     * Arbitrary key-value pairs for custom configuration.
     * These are available at configuration time but not in generated code.
     */
    val extras: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java)

    /**
     * Dependencies that should only be included when this flavor is active.
     */
    val flavorDependencies: ListProperty<FlavorDependency> =
        objects.listProperty(FlavorDependency::class.java)

    /**
     * Fallback flavors for dependency resolution when a dependency doesn't
     * have a variant for this flavor.
     */
    val matchingFallbacks: ListProperty<String> =
        objects.listProperty(String::class.java)

    /**
     * The signing config name to use for Android builds with this flavor.
     */
    val signingConfig: Property<String> = objects.property(String::class.java)

    /**
     * Adds a custom build config field to this flavor.
     *
     * @param type The Kotlin type (e.g., "String", "Boolean", "Int", "Long")
     * @param name The constant name (will be uppercase)
     * @param value The value as a string
     */
    fun buildConfigField(type: String, name: String, value: String) {
        buildConfigFields.put(name, BuildConfigField(type, name, value))
    }

    /**
     * Adds a per-flavor dependency.
     *
     * @param configuration The Gradle configuration (e.g., "implementation")
     * @param notation The dependency notation
     */
    fun dependency(configuration: String, notation: String) {
        flavorDependencies.add(FlavorDependency(configuration, notation))
    }
}
