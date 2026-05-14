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

import com.mobilebytelabs.kmpflavors.internal.FlavorVariantResolver
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlavorVariantResolverTest {

    @Test
    fun `resolveAllVariants returns empty list when no flavors`() {
        val result = FlavorVariantResolver.resolveAllVariants(emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolveAllVariants with no dimensions creates single-flavor variants`() {
        val flavors = listOf(
            createMockFlavorConfig("free", null),
            createMockFlavorConfig("paid", null),
        )

        val result = FlavorVariantResolver.resolveAllVariants(emptyList(), flavors)

        assertEquals(2, result.size)
        assertEquals("free", result[0].name)
        assertEquals("paid", result[1].name)
    }

    @Test
    fun `resolveAllVariants computes cartesian product for two dimensions`() {
        val dimensions = listOf(
            createMockDimension("tier", 0),
            createMockDimension("environment", 1),
        )
        val flavors = listOf(
            createMockFlavorConfig("free", "tier"),
            createMockFlavorConfig("paid", "tier"),
            createMockFlavorConfig("dev", "environment"),
            createMockFlavorConfig("prod", "environment"),
        )

        val result = FlavorVariantResolver.resolveAllVariants(dimensions, flavors)

        assertEquals(4, result.size)
        val variantNames = result.map { it.name }.toSet()
        assertTrue("freeDev" in variantNames)
        assertTrue("freeProd" in variantNames)
        assertTrue("paidDev" in variantNames)
        assertTrue("paidProd" in variantNames)
    }

    @Test
    fun `resolveAllVariants respects dimension priority for naming`() {
        val dimensions = listOf(
            createMockDimension("environment", 1),
            createMockDimension("tier", 0),
        )
        val flavors = listOf(
            createMockFlavorConfig("free", "tier"),
            createMockFlavorConfig("dev", "environment"),
        )

        val result = FlavorVariantResolver.resolveAllVariants(dimensions, flavors)

        assertEquals(1, result.size)
        // tier (priority 0) comes first, so "free" + "Dev" = "freeDev"
        assertEquals("freeDev", result[0].name)
    }

    @Test
    fun `resolveAllVariants returns empty list when a dimension has no flavors (V03 routed to validator)`() {
        // v2.1: the resolver no longer throws for "dimension has no flavors" — that condition is
        // surfaced as a structured KMPF-V03 finding by KmpFlavorPluginValidator. The resolver
        // returns an empty matrix so the validator can see the state and report it.
        val dimensions = listOf(
            createMockDimension("tier", 0),
            createMockDimension("environment", 1),
        )
        val flavors = listOf(
            createMockFlavorConfig("free", "tier"),
            // Missing "environment" flavors
        )

        val result = FlavorVariantResolver.resolveAllVariants(dimensions, flavors)
        assertTrue(result.isEmpty(), "Expected an empty matrix when a dimension has no flavors; got: $result")
    }

    @Test
    fun `resolveDefaultVariant returns first flavor when no defaults set`() {
        val flavors = listOf(
            createMockFlavorConfig("free", null, isDefault = false),
            createMockFlavorConfig("paid", null, isDefault = false),
        )

        val result = FlavorVariantResolver.resolveDefaultVariant(emptyList(), flavors)

        assertNotNull(result)
        assertEquals("free", result?.name)
    }

    @Test
    fun `resolveDefaultVariant returns default flavor when set`() {
        val flavors = listOf(
            createMockFlavorConfig("free", null, isDefault = false),
            createMockFlavorConfig("paid", null, isDefault = true),
        )

        val result = FlavorVariantResolver.resolveDefaultVariant(emptyList(), flavors)

        assertNotNull(result)
        assertEquals("paid", result?.name)
    }

    @Test
    fun `resolveDefaultVariant uses defaults from each dimension`() {
        val dimensions = listOf(
            createMockDimension("tier", 0),
            createMockDimension("environment", 1),
        )
        val flavors = listOf(
            createMockFlavorConfig("free", "tier", isDefault = true),
            createMockFlavorConfig("paid", "tier", isDefault = false),
            createMockFlavorConfig("dev", "environment", isDefault = false),
            createMockFlavorConfig("prod", "environment", isDefault = true),
        )

        val result = FlavorVariantResolver.resolveDefaultVariant(dimensions, flavors)

        assertNotNull(result)
        assertEquals("freeProd", result?.name)
    }

    @Test
    fun `resolveVariantByName finds variant case-insensitively`() {
        val variants = listOf(
            FlavorVariant("freeDev", emptyList()),
            FlavorVariant("paidProd", emptyList()),
        )

        val result1 = FlavorVariantResolver.resolveVariantByName("freeDev", variants)
        val result2 = FlavorVariantResolver.resolveVariantByName("FREEDEV", variants)
        val result3 = FlavorVariantResolver.resolveVariantByName("FreeDev", variants)

        assertNotNull(result1)
        assertNotNull(result2)
        assertNotNull(result3)
        assertEquals("freeDev", result1?.name)
    }

    @Test
    fun `resolveVariantByName returns null for unknown variant`() {
        val variants = listOf(
            FlavorVariant("freeDev", emptyList()),
        )

        val result = FlavorVariantResolver.resolveVariantByName("unknown", variants)

        assertNull(result)
    }

    // ----- v1.1.1 — buildType axis tests ---------------------------------------------

    @Test
    fun `resolveAllVariants without enableBuildTypes returns flavor-only matrix`() {
        val dimensions = listOf(createMockDimension("tier", 0))
        val flavors = listOf(
            createMockFlavorConfig("free", "tier", isDefault = true),
            createMockFlavorConfig("paid", "tier"),
        )
        val buildTypes = listOf(
            createMockBuildType("debug", isDefault = true),
            createMockBuildType("release"),
        )

        // enableBuildTypes=false (default) — buildTypes ignored
        val result = FlavorVariantResolver.resolveAllVariants(dimensions, flavors, buildTypes = buildTypes, enableBuildTypes = false)

        assertEquals(2, result.size)
        assertEquals(setOf("free", "paid"), result.map { it.name }.toSet())
    }

    @Test
    fun `resolveAllVariants with enableBuildTypes expands by buildType axis`() {
        val dimensions = listOf(createMockDimension("tier", 0))
        val flavors = listOf(
            createMockFlavorConfig("free", "tier", isDefault = true),
            createMockFlavorConfig("paid", "tier"),
        )
        val buildTypes = listOf(
            createMockBuildType("debug", isDefault = true),
            createMockBuildType("release"),
        )

        val result = FlavorVariantResolver.resolveAllVariants(
            dimensions = dimensions,
            flavors = flavors,
            buildTypes = buildTypes,
            enableBuildTypes = true,
        )

        assertEquals(4, result.size)
        assertEquals(
            setOf("freeDebug", "freeRelease", "paidDebug", "paidRelease"),
            result.map { it.name }.toSet(),
        )
        // Each variant carries its buildType
        assertEquals("debug", result.first { it.name == "freeDebug" }.buildType?.name)
        assertEquals("release", result.first { it.name == "paidRelease" }.buildType?.name)
    }

    @Test
    fun `resolveDefaultVariant returns flavor-only when enableBuildTypes is false`() {
        val dimensions = listOf(createMockDimension("tier", 0))
        val flavors = listOf(
            createMockFlavorConfig("free", "tier", isDefault = true),
            createMockFlavorConfig("paid", "tier"),
        )
        val buildTypes = listOf(createMockBuildType("debug", isDefault = true))

        val result = FlavorVariantResolver.resolveDefaultVariant(dimensions, flavors, buildTypes, enableBuildTypes = false)

        assertEquals("free", result?.name)
        assertNull(result?.buildType)
    }

    @Test
    fun `resolveDefaultVariant picks default buildType when enableBuildTypes is true`() {
        val dimensions = listOf(createMockDimension("tier", 0))
        val flavors = listOf(
            createMockFlavorConfig("free", "tier", isDefault = true),
            createMockFlavorConfig("paid", "tier"),
        )
        val buildTypes = listOf(
            createMockBuildType("debug", isDefault = true),
            createMockBuildType("release"),
        )

        val result = FlavorVariantResolver.resolveDefaultVariant(dimensions, flavors, buildTypes, enableBuildTypes = true)

        assertEquals("freeDebug", result?.name)
        assertEquals("debug", result?.buildType?.name)
    }

    @Test
    fun `enableBuildTypes with no buildTypes declared falls back to flavor-only`() {
        val dimensions = listOf(createMockDimension("tier", 0))
        val flavors = listOf(createMockFlavorConfig("free", "tier", isDefault = true))

        val result = FlavorVariantResolver.resolveAllVariants(
            dimensions = dimensions,
            flavors = flavors,
            buildTypes = emptyList(),
            enableBuildTypes = true,
        )

        assertEquals(1, result.size)
        assertEquals("free", result.first().name)
        assertNull(result.first().buildType)
    }

    // Helper functions to create mock objects

    private fun createMockFlavorConfig(name: String, dimension: String?, isDefault: Boolean = false): FlavorConfig {
        val mock = mockk<FlavorConfig>()
        every { mock.name } returns name
        every { mock.dimension } returns mockProperty(dimension)
        every { mock.isDefault } returns mockProperty(isDefault)
        every { mock.buildConfigFields } returns mockk {
            every { get() } returns emptyMap()
        }
        every { mock.applicationIdSuffix } returns mockProperty(null)
        every { mock.bundleIdSuffix } returns mockProperty(null)
        every { mock.desktopWindowTitleSuffix } returns mockProperty(null)
        every { mock.webTitleSuffix } returns mockProperty(null)
        every { mock.versionNameSuffix } returns mockProperty(null)
        every { mock.extras } returns mockk {
            every { get() } returns emptyMap()
        }
        every { mock.flavorDependencies } returns mockk {
            every { get() } returns emptyList()
        }
        every { mock.matchingFallbacks } returns mockListProperty(emptyList())
        return mock
    }

    private inline fun <reified T : Any> mockListProperty(values: List<T>): ListProperty<T> {
        val mock = mockk<ListProperty<T>>()
        every { mock.get() } returns values
        every { mock.getOrElse(any()) } returns values
        return mock
    }

    private fun createMockDimension(name: String, priority: Int): FlavorDimension {
        val mock = mockk<FlavorDimension>()
        every { mock.name } returns name
        every { mock.priority } returns mockProperty(priority)
        return mock
    }

    private fun createMockBuildType(name: String, isDefault: Boolean = false): BuildTypeConfig {
        val mock = mockk<BuildTypeConfig>()
        every { mock.name } returns name
        every { mock.isDefault } returns mockProperty(isDefault)
        every { mock.isDebuggable } returns mockProperty(false)
        every { mock.isMinifyEnabled } returns mockProperty(false)
        every { mock.applicationIdSuffix } returns mockProperty(null)
        every { mock.matchingFallbacks } returns mockListProperty(emptyList())
        every { mock.buildConfigFields } returns mockk {
            every { get() } returns emptyMap()
        }
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
}
