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

import com.mobilebytelabs.kmpflavors.internal.PlatformGroup
import com.mobilebytelabs.kmpflavors.internal.SourceSetConfigurator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SourceSetConfiguratorTest {

    private lateinit var logger: Logger
    private lateinit var configurator: SourceSetConfigurator
    private lateinit var kotlin: KotlinMultiplatformExtension
    private lateinit var sourceSets: NamedDomainObjectContainer<KotlinSourceSet>
    private lateinit var commonMain: KotlinSourceSet

    @BeforeEach
    fun setup() {
        logger = mockk(relaxed = true)
        configurator = SourceSetConfigurator(logger)
        kotlin = mockk()
        sourceSets = mockk()
        commonMain = createMockSourceSet("commonMain")

        every { kotlin.sourceSets } returns sourceSets
        every { sourceSets.getByName("commonMain") } returns commonMain
    }

    @Test
    fun `configure creates commonFlavor source set for each flavor`() {
        val freeFlavor = createMockFlavorConfig("free", "tier")
        val paidFlavor = createMockFlavorConfig("paid", "tier")
        val activeVariant = FlavorVariant("free", listOf(freeFlavor))

        val commonFree = createMockSourceSet("commonFree")
        val commonPaid = createMockSourceSet("commonPaid")

        every { sourceSets.maybeCreate("commonFree") } returns commonFree
        every { sourceSets.maybeCreate("commonPaid") } returns commonPaid
        every { sourceSets.findByName(any()) } returns null

        configurator.configure(
            kotlin = kotlin,
            activeVariant = activeVariant,
            allFlavors = listOf(freeFlavor, paidFlavor),
            platforms = emptyList(),
            platformSourceSets = emptyMap(),
            createIntermediates = false,
        )

        verify { sourceSets.maybeCreate("commonFree") }
        verify { sourceSets.maybeCreate("commonPaid") }
    }

    @Test
    fun `configure wires active flavor to commonMain`() {
        val freeFlavor = createMockFlavorConfig("free", "tier")
        val activeVariant = FlavorVariant("free", listOf(freeFlavor))

        val commonFree = createMockSourceSet("commonFree")
        every { sourceSets.maybeCreate("commonFree") } returns commonFree
        every { sourceSets.findByName(any()) } returns null

        configurator.configure(
            kotlin = kotlin,
            activeVariant = activeVariant,
            allFlavors = listOf(freeFlavor),
            platforms = emptyList(),
            platformSourceSets = emptyMap(),
            createIntermediates = false,
        )

        verify { commonFree.dependsOn(commonMain) }
    }

    @Test
    fun `configure does not wire inactive flavor to commonMain`() {
        val freeFlavor = createMockFlavorConfig("free", "tier")
        val paidFlavor = createMockFlavorConfig("paid", "tier")
        val activeVariant = FlavorVariant("free", listOf(freeFlavor))

        val commonFree = createMockSourceSet("commonFree")
        val commonPaid = createMockSourceSet("commonPaid")

        every { sourceSets.maybeCreate("commonFree") } returns commonFree
        every { sourceSets.maybeCreate("commonPaid") } returns commonPaid
        every { sourceSets.findByName(any()) } returns null

        configurator.configure(
            kotlin = kotlin,
            activeVariant = activeVariant,
            allFlavors = listOf(freeFlavor, paidFlavor),
            platforms = emptyList(),
            platformSourceSets = emptyMap(),
            createIntermediates = false,
        )

        verify { commonFree.dependsOn(commonMain) }
        verify(exactly = 0) { commonPaid.dependsOn(commonMain) }
    }

    @Test
    fun `configure creates platform-specific flavor source sets`() {
        val freeFlavor = createMockFlavorConfig("free", "tier")
        val activeVariant = FlavorVariant("free", listOf(freeFlavor))

        val androidPlatform = PlatformGroup("android", "androidMain")
        val androidMain = createMockSourceSet("androidMain")

        val commonFree = createMockSourceSet("commonFree")
        val androidFree = createMockSourceSet("androidFree")

        every { sourceSets.maybeCreate("commonFree") } returns commonFree
        every { sourceSets.maybeCreate("androidFree") } returns androidFree
        every { sourceSets.findByName("androidMain") } returns androidMain
        every { sourceSets.findByName(any()) } returns null

        configurator.configure(
            kotlin = kotlin,
            activeVariant = activeVariant,
            allFlavors = listOf(freeFlavor),
            platforms = listOf(androidPlatform),
            platformSourceSets = mapOf(androidPlatform to androidMain),
            createIntermediates = false,
        )

        verify { sourceSets.maybeCreate("androidFree") }
        verify { androidFree.dependsOn(androidMain) }
        verify { androidFree.dependsOn(commonFree) }
    }

    @Test
    fun `configure creates intermediate flavor source sets when enabled`() {
        val freeFlavor = createMockFlavorConfig("free", "tier")
        val activeVariant = FlavorVariant("free", listOf(freeFlavor))

        val iosPlatform = PlatformGroup("ios", "iosMain", parent = "native")
        val nativeIntermediate = PlatformGroup("native", "nativeMain", isIntermediate = true)

        val iosMain = createMockSourceSet("iosMain")
        val nativeMain = createMockSourceSet("nativeMain")
        val commonFree = createMockSourceSet("commonFree")
        val nativeFree = createMockSourceSet("nativeFree")
        val iosFree = createMockSourceSet("iosFree")

        every { sourceSets.maybeCreate("commonFree") } returns commonFree
        every { sourceSets.maybeCreate("nativeFree") } returns nativeFree
        every { sourceSets.maybeCreate("iosFree") } returns iosFree
        every { sourceSets.findByName("iosMain") } returns iosMain
        every { sourceSets.findByName("nativeMain") } returns nativeMain
        every { sourceSets.findByName("nativeFree") } returns nativeFree
        every { sourceSets.findByName(any()) } returns null

        configurator.configure(
            kotlin = kotlin,
            activeVariant = activeVariant,
            allFlavors = listOf(freeFlavor),
            platforms = listOf(iosPlatform, nativeIntermediate),
            platformSourceSets = mapOf(iosPlatform to iosMain),
            createIntermediates = true,
        )

        verify { sourceSets.maybeCreate("nativeFree") }
        verify { nativeFree.dependsOn(commonFree) }
        verify { nativeFree.dependsOn(nativeMain) }
        verify { iosFree.dependsOn(iosMain) }
        verify { iosFree.dependsOn(nativeFree) }
    }

    // Helpers

    private fun createMockSourceSet(name: String): KotlinSourceSet {
        val mock = mockk<KotlinSourceSet>(relaxed = true)
        every { mock.name } returns name
        every { mock.kotlin } returns mockk<SourceDirectorySet>(relaxed = true)
        every { mock.resources } returns mockk<SourceDirectorySet>(relaxed = true)
        return mock
    }

    private fun createMockFlavorConfig(name: String, dimension: String?): FlavorConfig {
        val mock = mockk<FlavorConfig>()
        every { mock.name } returns name
        every { mock.dimension } returns mockProperty(dimension)
        every { mock.isDefault } returns mockProperty(false)
        every { mock.buildConfigFields } returns mockk {
            every { get() } returns emptyMap()
        }
        every { mock.applicationIdSuffix } returns mockProperty(null)
        every { mock.versionNameSuffix } returns mockProperty(null)
        every { mock.extras } returns mockk {
            every { get() } returns emptyMap()
        }
        every { mock.flavorDependencies } returns mockk {
            every { get() } returns emptyList()
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
