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

import com.mobilebytelabs.kmpflavors.internal.DependencyConfigurator
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MatchingFallbacksTest {

    private lateinit var logger: Logger
    private lateinit var configurator: DependencyConfigurator

    @BeforeEach
    fun setup() {
        logger = mockk(relaxed = true)
        configurator = DependencyConfigurator(logger)
    }

    @Test
    fun `resolveWithFallbacks returns exact match when available`() {
        val result = configurator.resolveWithFallbacks(
            requestedVariant = "paid",
            availableVariants = listOf("free", "paid"),
            fallbacks = listOf("free"),
        )

        assertEquals("paid", result)
    }

    @Test
    fun `resolveWithFallbacks uses first fallback when exact match not found`() {
        val result = configurator.resolveWithFallbacks(
            requestedVariant = "premium",
            availableVariants = listOf("free", "paid"),
            fallbacks = listOf("paid", "free"),
        )

        assertEquals("paid", result)
    }

    @Test
    fun `resolveWithFallbacks tries fallbacks in order`() {
        val result = configurator.resolveWithFallbacks(
            requestedVariant = "premium",
            availableVariants = listOf("free"),
            fallbacks = listOf("paid", "free"),
        )

        assertEquals("free", result)
    }

    @Test
    fun `resolveWithFallbacks returns null when no match found`() {
        val result = configurator.resolveWithFallbacks(
            requestedVariant = "premium",
            availableVariants = listOf("basic"),
            fallbacks = listOf("paid", "free"),
        )

        assertNull(result)
    }

    @Test
    fun `resolveWithFallbacks returns null for empty available variants`() {
        val result = configurator.resolveWithFallbacks(
            requestedVariant = "paid",
            availableVariants = emptyList(),
            fallbacks = listOf("free"),
        )

        assertNull(result)
    }

    @Test
    fun `getFallbackChain includes variant name and fallbacks`() {
        val flavor = createMockFlavorConfig("paid", null, listOf("free"))

        val variant = FlavorVariant(
            name = "paid",
            flavors = listOf(flavor),
        )

        val chain = configurator.getFallbackChain(variant)

        assertEquals(listOf("paid", "free"), chain)
    }

    @Test
    fun `getFallbackChain removes duplicates`() {
        val flavor1 = createMockFlavorConfig("paid", "tier", listOf("free"))
        val flavor2 = createMockFlavorConfig("dev", "environment", listOf("free")) // Same fallback

        val variant = FlavorVariant(
            name = "paidDev",
            flavors = listOf(flavor1, flavor2),
        )

        val chain = configurator.getFallbackChain(variant)

        assertEquals(listOf("paidDev", "free"), chain)
    }

    @Test
    fun `FlavorVariant mergedMatchingFallbacks combines from all flavors`() {
        val flavor1 = createMockFlavorConfig("paid", "tier", listOf("free"))
        val flavor2 = createMockFlavorConfig("prod", "environment", listOf("dev", "staging"))

        val variant = FlavorVariant(
            name = "paidProd",
            flavors = listOf(flavor1, flavor2),
        )

        assertEquals(listOf("free", "dev", "staging"), variant.mergedMatchingFallbacks)
    }

    @Test
    fun `FlavorVariant getMatchingFallbacksForDimension returns dimension-specific fallbacks`() {
        val flavor1 = createMockFlavorConfig("paid", "tier", listOf("free"))
        val flavor2 = createMockFlavorConfig("prod", "environment", listOf("dev"))

        val variant = FlavorVariant(
            name = "paidProd",
            flavors = listOf(flavor1, flavor2),
        )

        assertEquals(listOf("free"), variant.getMatchingFallbacksForDimension("tier"))
        assertEquals(listOf("dev"), variant.getMatchingFallbacksForDimension("environment"))
        assertEquals(emptyList<String>(), variant.getMatchingFallbacksForDimension("unknown"))
    }

    // Helper functions to create mock objects

    private fun createMockFlavorConfig(name: String, dimension: String?, fallbacks: List<String> = emptyList()): FlavorConfig {
        val mock = mockk<FlavorConfig>()
        every { mock.name } returns name
        every { mock.dimension } returns mockProperty(dimension)
        every { mock.isDefault } returns mockProperty(false)
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
        every { mock.matchingFallbacks } returns mockListProperty(fallbacks)
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

    private inline fun <reified T : Any> mockListProperty(values: List<T>): ListProperty<T> {
        val mock = mockk<ListProperty<T>>()
        every { mock.get() } returns values
        every { mock.getOrElse(any()) } returns values
        return mock
    }
}
