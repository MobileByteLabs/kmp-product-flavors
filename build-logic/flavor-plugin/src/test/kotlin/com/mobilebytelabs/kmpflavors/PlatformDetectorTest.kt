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

import com.mobilebytelabs.kmpflavors.internal.PlatformDetector
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformDetectorTest {

    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun `detect finds Android target`() {
        val kotlin = createMockKotlin("android")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val android = platforms.find { it.prefix == "android" }
        assertNotNull(android)
        assertEquals("androidMain", android?.mainSourceSet)
        assertFalse(android?.isIntermediate ?: true)
    }

    @Test
    fun `detect finds iOS target and adds nativeMain intermediate`() {
        val kotlin = createMockKotlin("iosArm64", "iosSimulatorArm64")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val ios = platforms.find { it.prefix == "ios" }
        val native = platforms.find { it.prefix == "native" }

        assertNotNull(ios)
        assertEquals("iosMain", ios?.mainSourceSet)
        assertEquals("native", ios?.parent)

        assertNotNull(native)
        assertTrue(native?.isIntermediate ?: false)
    }

    @Test
    fun `detect finds Desktop JVM target`() {
        val kotlin = createMockKotlin("desktop")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val desktop = platforms.find { it.prefix == "desktop" }
        assertNotNull(desktop)
        assertEquals("desktopMain", desktop?.mainSourceSet)
    }

    @Test
    fun `detect finds JVM target without desktop name`() {
        val kotlin = createMockKotlin("jvm")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val desktop = platforms.find { it.prefix == "desktop" }
        assertNotNull(desktop)
        assertEquals("jvmMain", desktop?.mainSourceSet)
    }

    @Test
    fun `detect finds JS and WasmJS targets with webMain intermediate`() {
        val kotlin = createMockKotlin("js", "wasmJs")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val js = platforms.find { it.prefix == "js" }
        val wasmJs = platforms.find { it.prefix == "wasmJs" }
        val web = platforms.find { it.prefix == "web" }

        assertNotNull(js)
        assertEquals("jsMain", js?.mainSourceSet)
        assertEquals("web", js?.parent)

        assertNotNull(wasmJs)
        assertEquals("wasmJsMain", wasmJs?.mainSourceSet)
        assertEquals("web", wasmJs?.parent)

        assertNotNull(web)
        assertTrue(web?.isIntermediate ?: false)
    }

    @Test
    fun `detect finds macOS targets`() {
        val kotlin = createMockKotlin("macosArm64", "macosX64")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val macos = platforms.find { it.prefix == "macos" }
        assertNotNull(macos)
        assertEquals("macosMain", macos?.mainSourceSet)
        assertEquals("native", macos?.parent)
    }

    @Test
    fun `detect finds Linux targets`() {
        val kotlin = createMockKotlin("linuxX64")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val linux = platforms.find { it.prefix == "linux" }
        assertNotNull(linux)
        assertEquals("linuxMain", linux?.mainSourceSet)
        assertEquals("native", linux?.parent)
    }

    @Test
    fun `detect finds Windows (MinGW) targets`() {
        val kotlin = createMockKotlin("mingwX64")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val mingw = platforms.find { it.prefix == "mingw" }
        assertNotNull(mingw)
        assertEquals("mingwMain", mingw?.mainSourceSet)
        assertEquals("native", mingw?.parent)
    }

    @Test
    fun `detect handles full KMP project with all targets`() {
        val kotlin = createMockKotlin(
            "android",
            "iosArm64",
            "iosSimulatorArm64",
            "desktop",
            "js",
            "wasmJs",
        )

        val platforms = PlatformDetector.detect(kotlin, logger)

        // Leaf platforms
        assertNotNull(platforms.find { it.prefix == "android" })
        assertNotNull(platforms.find { it.prefix == "ios" })
        assertNotNull(platforms.find { it.prefix == "desktop" })
        assertNotNull(platforms.find { it.prefix == "js" })
        assertNotNull(platforms.find { it.prefix == "wasmJs" })

        // Intermediate platforms
        assertNotNull(platforms.find { it.prefix == "native" && it.isIntermediate })
        assertNotNull(platforms.find { it.prefix == "web" && it.isIntermediate })

        // Count: 5 leaf + 2 intermediate = 7
        assertEquals(7, platforms.size)
    }

    @Test
    fun `detect returns empty list for no targets`() {
        val kotlin = createMockKotlin()

        val platforms = PlatformDetector.detect(kotlin, logger)

        assertTrue(platforms.isEmpty())
    }

    // Helper to create mock KotlinMultiplatformExtension

    private fun createMockKotlin(vararg targetNames: String): KotlinMultiplatformExtension {
        val targets = targetNames.map { name ->
            mockk<KotlinTarget>(relaxed = true) {
                every { this@mockk.name } returns name
            }
        }

        // Use a real ArrayList wrapped by the mock to make standard Kotlin extension functions work
        val targetsList = ArrayList(targets)

        // Create a mock collection that delegates iteration to the real list
        val mockTargetContainer = mockk<NamedDomainObjectCollection<KotlinTarget>>()

        // Mock iterator() which is used by Kotlin's map extension function
        every { mockTargetContainer.iterator() } answers { targetsList.iterator() }

        // Mock size and isEmpty for completeness
        every { mockTargetContainer.size } returns targetsList.size
        every { mockTargetContainer.isEmpty() } returns targetsList.isEmpty()

        return mockk(relaxed = true) {
            every { this@mockk.targets } returns mockTargetContainer
        }
    }
}
