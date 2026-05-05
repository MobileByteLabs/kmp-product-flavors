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
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariantFilterTest {

    @Test
    fun `variant filter can exclude specific combinations`() {
        val dimensions = listOf(
            createMockDimension("tier", 0),
            createMockDimension("environment", 1),
        )

        val flavors = listOf(
            createMockFlavor("free", "tier"),
            createMockFlavor("paid", "tier"),
            createMockFlavor("dev", "environment"),
            createMockFlavor("prod", "environment"),
        )

        // Filter to exclude freeProd
        val filters = listOf<Action<VariantFilter>>(
            Action {
                if (hasAllFlavors("free", "prod")) {
                    exclude()
                }
            },
        )

        val variants = FlavorVariantResolver.resolveAllVariants(dimensions, flavors, filters)

        // Should have 3 variants instead of 4
        assertEquals(3, variants.size)
        assertTrue(variants.any { it.name == "freeDev" })
        assertTrue(variants.any { it.name == "paidDev" })
        assertTrue(variants.any { it.name == "paidProd" })
        assertFalse(variants.any { it.name == "freeProd" })
    }

    @Test
    fun `variant filter can exclude by variant name`() {
        val dimensions = listOf(
            createMockDimension("tier", 0),
        )

        val flavors = listOf(
            createMockFlavor("free", "tier"),
            createMockFlavor("paid", "tier"),
            createMockFlavor("enterprise", "tier"),
        )

        val filters = listOf<Action<VariantFilter>>(
            Action {
                if (variantName == "enterprise") {
                    exclude()
                }
            },
        )

        val variants = FlavorVariantResolver.resolveAllVariants(dimensions, flavors, filters)

        assertEquals(2, variants.size)
        assertFalse(variants.any { it.name == "enterprise" })
    }

    @Test
    fun `variant filter with multiple filters applies all`() {
        val flavors = listOf(
            createMockFlavor("a", null),
            createMockFlavor("b", null),
            createMockFlavor("c", null),
            createMockFlavor("d", null),
        )

        val filters = listOf<Action<VariantFilter>>(
            Action {
                if (variantName == "a") exclude()
            },
            Action {
                if (variantName == "c") exclude()
            },
        )

        val variants = FlavorVariantResolver.resolveAllVariants(emptyList(), flavors, filters)

        assertEquals(2, variants.size)
        assertTrue(variants.any { it.name == "b" })
        assertTrue(variants.any { it.name == "d" })
    }

    @Test
    fun `variant filter helper methods work correctly`() {
        val flavors = listOf(
            createMockFlavor("free", "tier"),
            createMockFlavor("dev", "environment"),
        )

        val filter = VariantFilter(
            variantName = "freeDev",
            flavorNames = listOf("free", "dev"),
            flavors = flavors,
        )

        assertTrue(filter.hasFlavor("free"))
        assertTrue(filter.hasFlavor("dev"))
        assertFalse(filter.hasFlavor("paid"))

        assertTrue(filter.hasAllFlavors("free", "dev"))
        assertFalse(filter.hasAllFlavors("free", "prod"))

        assertTrue(filter.hasAnyFlavor("free", "prod"))
        assertFalse(filter.hasAnyFlavor("paid", "prod"))

        assertEquals("free", filter.getFlavorFromDimension("tier"))
        assertEquals("dev", filter.getFlavorFromDimension("environment"))
        assertEquals(null, filter.getFlavorFromDimension("region"))
    }

    @Test
    fun `empty filters returns all variants`() {
        val flavors = listOf(
            createMockFlavor("a", null),
            createMockFlavor("b", null),
        )

        val variants = FlavorVariantResolver.resolveAllVariants(emptyList(), flavors, emptyList())

        assertEquals(2, variants.size)
    }

    // Helper functions

    private fun createMockDimension(name: String, priority: Int): FlavorDimension {
        val mock = mockk<FlavorDimension>()
        every { mock.name } returns name
        every { mock.priority } returns mockProperty(priority)
        return mock
    }

    private fun createMockFlavor(name: String, dimension: String?): FlavorConfig {
        val mock = mockk<FlavorConfig>()
        every { mock.name } returns name
        every { mock.dimension } returns mockProperty(dimension)
        every { mock.isDefault } returns mockProperty(false)
        every { mock.buildConfigFields } returns mockMapProperty(emptyMap())
        every { mock.applicationIdSuffix } returns mockProperty(null)
        every { mock.bundleIdSuffix } returns mockProperty(null)
        every { mock.desktopWindowTitleSuffix } returns mockProperty(null)
        every { mock.webTitleSuffix } returns mockProperty(null)
        every { mock.versionNameSuffix } returns mockProperty(null)
        every { mock.extras } returns mockMapProperty(emptyMap())
        every { mock.flavorDependencies } returns mockListProperty(emptyList())
        every { mock.matchingFallbacks } returns mockListProperty(emptyList())
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

    private inline fun <reified T : Any> mockListProperty(value: List<T>): ListProperty<T> {
        val mock = mockk<ListProperty<T>>()
        every { mock.get() } returns value
        every { mock.orNull } returns value
        every { mock.getOrElse(any()) } returns value
        return mock
    }
}
