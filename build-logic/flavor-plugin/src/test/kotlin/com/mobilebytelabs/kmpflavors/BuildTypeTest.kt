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

import io.mockk.every
import io.mockk.mockk
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildTypeTest {

    @Test
    fun `BuildVariant fromFlavorVariant creates variant without build type`() {
        val flavor = createMockFlavor("free", null)
        val flavorVariant = FlavorVariant("free", listOf(flavor))

        val buildVariant = BuildVariant.fromFlavorVariant(flavorVariant)

        assertEquals("free", buildVariant.name)
        assertEquals(flavorVariant, buildVariant.flavorVariant)
        assertEquals(null, buildVariant.buildType)
    }

    @Test
    fun `BuildVariant fromFlavorVariantAndBuildType creates combined variant`() {
        val flavor = createMockFlavor("free", null)
        val flavorVariant = FlavorVariant("free", listOf(flavor))
        val buildType = createMockBuildType("debug")

        val buildVariant = BuildVariant.fromFlavorVariantAndBuildType(flavorVariant, buildType)

        assertEquals("freeDebug", buildVariant.name)
        assertEquals(flavorVariant, buildVariant.flavorVariant)
        assertEquals(buildType, buildVariant.buildType)
    }

    @Test
    fun `BuildVariant merges build config fields with build type taking precedence`() {
        val flavor = createMockFlavor(
            "free",
            null,
            buildConfigFields = mapOf(
                "DEBUG" to BuildConfigField("Boolean", "DEBUG", "true"),
                "FLAVOR" to BuildConfigField("String", "FLAVOR", "\"free\""),
            ),
        )
        val flavorVariant = FlavorVariant("free", listOf(flavor))
        val buildType = createMockBuildType(
            "release",
            buildConfigFields = mapOf(
                "DEBUG" to BuildConfigField("Boolean", "DEBUG", "false"),
            ),
        )

        val buildVariant = BuildVariant.fromFlavorVariantAndBuildType(flavorVariant, buildType)

        val fields = buildVariant.mergedBuildConfigFields
        assertEquals(2, fields.size)
        assertEquals("false", fields["DEBUG"]?.value) // Build type overrides
        assertEquals("\"free\"", fields["FLAVOR"]?.value) // Flavor retained
    }

    @Test
    fun `BuildVariant combines suffixes from flavor and build type`() {
        val flavor = createMockFlavor(
            "free",
            null,
            applicationIdSuffix = ".free",
            versionNameSuffix = "-free",
        )
        val flavorVariant = FlavorVariant("free", listOf(flavor))
        val buildType = createMockBuildType(
            "debug",
            applicationIdSuffix = ".debug",
            versionNameSuffix = "-debug",
        )

        val buildVariant = BuildVariant.fromFlavorVariantAndBuildType(flavorVariant, buildType)

        assertEquals(".free.debug", buildVariant.combinedApplicationIdSuffix)
        assertEquals("-free-debug", buildVariant.combinedVersionNameSuffix)
    }

    @Test
    fun `BuildVariant isDebuggable reflects build type`() {
        val flavor = createMockFlavor("free", null)
        val flavorVariant = FlavorVariant("free", listOf(flavor))
        val debugBuildType = createMockBuildType("debug", isDebuggable = true)
        val releaseBuildType = createMockBuildType("release", isDebuggable = false)

        val debugVariant = BuildVariant.fromFlavorVariantAndBuildType(flavorVariant, debugBuildType)
        val releaseVariant = BuildVariant.fromFlavorVariantAndBuildType(flavorVariant, releaseBuildType)

        assertTrue(debugVariant.isDebuggable)
        assertFalse(releaseVariant.isDebuggable)
    }

    @Test
    fun `BuildVariant isMinifyEnabled reflects build type`() {
        val flavor = createMockFlavor("free", null)
        val flavorVariant = FlavorVariant("free", listOf(flavor))
        val debugBuildType = createMockBuildType("debug", isMinifyEnabled = false)
        val releaseBuildType = createMockBuildType("release", isMinifyEnabled = true)

        val debugVariant = BuildVariant.fromFlavorVariantAndBuildType(flavorVariant, debugBuildType)
        val releaseVariant = BuildVariant.fromFlavorVariantAndBuildType(flavorVariant, releaseBuildType)

        assertFalse(debugVariant.isMinifyEnabled)
        assertTrue(releaseVariant.isMinifyEnabled)
    }

    @Test
    fun `BuildVariant combines dependencies from flavor and build type`() {
        val flavor = createMockFlavor(
            "free",
            null,
            dependencies = listOf(FlavorDependency("implementation", "com.example:ads:1.0")),
        )
        val flavorVariant = FlavorVariant("free", listOf(flavor))
        val buildType = createMockBuildType(
            "debug",
            dependencies = listOf(FlavorDependency("implementation", "com.example:debug-tools:1.0")),
        )

        val buildVariant = BuildVariant.fromFlavorVariantAndBuildType(flavorVariant, buildType)

        val deps = buildVariant.allDependencies
        assertEquals(2, deps.size)
        assertTrue(deps.any { it.notation == "com.example:ads:1.0" })
        assertTrue(deps.any { it.notation == "com.example:debug-tools:1.0" })
    }

    // Helper functions

    private fun createMockFlavor(
        name: String,
        dimension: String?,
        buildConfigFields: Map<String, BuildConfigField> = emptyMap(),
        applicationIdSuffix: String? = null,
        versionNameSuffix: String? = null,
        dependencies: List<FlavorDependency> = emptyList(),
    ): FlavorConfig {
        val mock = mockk<FlavorConfig>()
        every { mock.name } returns name
        every { mock.dimension } returns mockProperty(dimension)
        every { mock.isDefault } returns mockProperty(false)
        every { mock.buildConfigFields } returns mockMapProperty(buildConfigFields)
        every { mock.applicationIdSuffix } returns mockProperty(applicationIdSuffix)
        every { mock.bundleIdSuffix } returns mockProperty(null)
        every { mock.desktopWindowTitleSuffix } returns mockProperty(null)
        every { mock.webTitleSuffix } returns mockProperty(null)
        every { mock.versionNameSuffix } returns mockProperty(versionNameSuffix)
        every { mock.extras } returns mockMapProperty(emptyMap())
        every { mock.flavorDependencies } returns mockListProperty(dependencies)
        every { mock.matchingFallbacks } returns mockListProperty(emptyList())
        return mock
    }

    private fun createMockBuildType(
        name: String,
        isDebuggable: Boolean = name == "debug",
        isMinifyEnabled: Boolean = name == "release",
        buildConfigFields: Map<String, BuildConfigField> = emptyMap(),
        applicationIdSuffix: String? = null,
        versionNameSuffix: String? = null,
        dependencies: List<FlavorDependency> = emptyList(),
    ): BuildTypeConfig {
        val mock = mockk<BuildTypeConfig>()
        every { mock.name } returns name
        every { mock.isDefault } returns mockProperty(false)
        every { mock.isDebuggable } returns mockProperty(isDebuggable)
        every { mock.isMinifyEnabled } returns mockProperty(isMinifyEnabled)
        every { mock.buildConfigFields } returns mockMapProperty(buildConfigFields)
        every { mock.applicationIdSuffix } returns mockProperty(applicationIdSuffix)
        every { mock.bundleIdSuffix } returns mockProperty(null)
        every { mock.versionNameSuffix } returns mockProperty(versionNameSuffix)
        every { mock.buildTypeDependencies } returns mockListProperty(dependencies)
        every { mock.matchingFallbacks } returns mockListProperty(emptyList())
        every { mock.signingConfig } returns mockProperty(null)
        return mock
    }

    private inline fun <reified T : Any> mockProperty(value: T?): Property<T> {
        val mock = mockk<Property<T>>()
        every { mock.orNull } returns value
        every { mock.getOrElse(any()) } answers {
            value ?: firstArg()
        }
        if (value != null) {
            every { mock.get() } returns value
        }
        return mock
    }

    private inline fun <reified K : Any, reified V : Any> mockMapProperty(value: Map<K, V>): MapProperty<K, V> {
        val mock = mockk<MapProperty<K, V>>()
        every { mock.get() } returns value
        every { mock.orNull } returns value
        every { mock.getOrElse(any()) } returns value
        return mock
    }

    private inline fun <reified T : Any> mockListProperty(values: List<T>): ListProperty<T> {
        val mock = mockk<ListProperty<T>>()
        every { mock.get() } returns values
        every { mock.getOrElse(any()) } returns values
        return mock
    }
}
