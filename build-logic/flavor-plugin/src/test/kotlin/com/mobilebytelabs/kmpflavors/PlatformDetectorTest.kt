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
    fun `detect finds tvOS targets`() {
        val kotlin = createMockKotlin("tvosArm64", "tvosSimulatorArm64")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val tvos = platforms.find { it.prefix == "tvos" }
        assertNotNull(tvos)
        assertEquals("tvosMain", tvos?.mainSourceSet)
        assertEquals("native", tvos?.parent)
        assertNotNull(platforms.find { it.prefix == "native" && it.isIntermediate })
    }

    @Test
    fun `detect finds watchOS targets including watchosDeviceArm64`() {
        val kotlin = createMockKotlin("watchosArm64", "watchosSimulatorArm64", "watchosDeviceArm64")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val watchos = platforms.find { it.prefix == "watchos" }
        assertNotNull(watchos)
        assertEquals("watchosMain", watchos?.mainSourceSet)
        assertEquals("native", watchos?.parent)
    }

    @Test
    fun `detect finds wasmWasi target alongside js and wasmJs`() {
        val kotlin = createMockKotlin("js", "wasmJs", "wasmWasi")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val wasmWasi = platforms.find { it.prefix == "wasmWasi" }
        assertNotNull(wasmWasi)
        assertEquals("wasmWasiMain", wasmWasi?.mainSourceSet)
        assertEquals("web", wasmWasi?.parent)

        // All three web targets share a single webMain intermediate
        val web = platforms.find { it.prefix == "web" && it.isIntermediate }
        assertNotNull(web)
    }

    @Test
    fun `detect finds Android Native targets`() {
        val kotlin = createMockKotlin("androidNativeArm64", "androidNativeX64")

        val platforms = PlatformDetector.detect(kotlin, logger)

        val androidNative = platforms.find { it.prefix == "androidNative" }
        assertNotNull(androidNative)
        assertEquals("androidNativeMain", androidNative?.mainSourceSet)
        assertEquals("native", androidNative?.parent)
    }

    @Test
    fun `detect does not confuse Android Native with Android target`() {
        val kotlin = createMockKotlin("androidNativeArm64")

        val platforms = PlatformDetector.detect(kotlin, logger)

        // androidTarget() not declared — only the native variant
        assertTrue(platforms.none { it.prefix == "android" })
        assertNotNull(platforms.find { it.prefix == "androidNative" })
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

    // ─────────────────────────────────────────────────────────────────────
    // v2.5 — per-arch detection discipline (AC 8 of v25-multidim-targets-buildkonfig)
    //
    // The 9 KMP targets the v2.5 epic promises to "fully cover" in samples must
    // EACH have an individual smoke-detection test, not just family-grouped
    // assertions. This locks the contract that target naming changes don't
    // silently drop a target from the matrix.
    //
    // PlatformDetector already supports all 9 (registered in v1.1.0 phases G1-G4) —
    // this section is regression discipline only.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-5 AC 8 - wasmJs detection (web parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("wasmJs"), logger)
        val wasmJs = platforms.find { it.prefix == "wasmJs" }
        assertNotNull(wasmJs, "wasmJs MUST be detected")
        assertEquals("wasmJsMain", wasmJs?.mainSourceSet)
        assertEquals("web", wasmJs?.parent)
        assertNotNull(
            platforms.find { it.prefix == "web" && it.isIntermediate },
            "wasmJs MUST trigger webMain intermediate",
        )
    }

    @Test
    fun `v2-5 AC 8 - watchosX64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("watchosX64"), logger)
        val watchos = platforms.find { it.prefix == "watchos" }
        assertNotNull(watchos, "watchosX64 MUST be detected via watchos family")
        assertEquals("watchosMain", watchos?.mainSourceSet)
        assertEquals("native", watchos?.parent)
        assertNotNull(
            platforms.find { it.prefix == "native" && it.isIntermediate },
            "watchosX64 MUST trigger nativeMain intermediate",
        )
    }

    @Test
    fun `v2-5 AC 8 - watchosArm64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("watchosArm64"), logger)
        val watchos = platforms.find { it.prefix == "watchos" }
        assertNotNull(watchos, "watchosArm64 MUST be detected via watchos family")
        assertEquals("native", watchos?.parent)
    }

    @Test
    fun `v2-5 AC 8 - watchosSimulatorArm64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("watchosSimulatorArm64"), logger)
        val watchos = platforms.find { it.prefix == "watchos" }
        assertNotNull(watchos, "watchosSimulatorArm64 MUST be detected via watchos family")
        assertEquals("native", watchos?.parent)
    }

    @Test
    fun `v2-5 AC 8 - watchosDeviceArm64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("watchosDeviceArm64"), logger)
        val watchos = platforms.find { it.prefix == "watchos" }
        assertNotNull(watchos, "watchosDeviceArm64 MUST be detected via watchos family")
        assertEquals("native", watchos?.parent)
    }

    @Test
    fun `v2-5 AC 8 - tvosX64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("tvosX64"), logger)
        val tvos = platforms.find { it.prefix == "tvos" }
        assertNotNull(tvos, "tvosX64 MUST be detected via tvos family")
        assertEquals("tvosMain", tvos?.mainSourceSet)
        assertEquals("native", tvos?.parent)
        assertNotNull(
            platforms.find { it.prefix == "native" && it.isIntermediate },
            "tvosX64 MUST trigger nativeMain intermediate",
        )
    }

    @Test
    fun `v2-5 AC 8 - tvosArm64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("tvosArm64"), logger)
        val tvos = platforms.find { it.prefix == "tvos" }
        assertNotNull(tvos, "tvosArm64 MUST be detected via tvos family")
        assertEquals("native", tvos?.parent)
    }

    @Test
    fun `v2-5 AC 8 - tvosSimulatorArm64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("tvosSimulatorArm64"), logger)
        val tvos = platforms.find { it.prefix == "tvos" }
        assertNotNull(tvos, "tvosSimulatorArm64 MUST be detected via tvos family")
        assertEquals("native", tvos?.parent)
    }

    @Test
    fun `v2-5 AC 8 - linuxX64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("linuxX64"), logger)
        val linux = platforms.find { it.prefix == "linux" }
        assertNotNull(linux, "linuxX64 MUST be detected via linux family")
        assertEquals("linuxMain", linux?.mainSourceSet)
        assertEquals("native", linux?.parent)
        assertNotNull(
            platforms.find { it.prefix == "native" && it.isIntermediate },
            "linuxX64 MUST trigger nativeMain intermediate",
        )
    }

    @Test
    fun `v2-5 AC 8 - mingwX64 detection (native parent)`() {
        val platforms = PlatformDetector.detect(createMockKotlin("mingwX64"), logger)
        val mingw = platforms.find { it.prefix == "mingw" }
        assertNotNull(mingw, "mingwX64 MUST be detected via mingw family")
        assertEquals("mingwMain", mingw?.mainSourceSet)
        assertEquals("native", mingw?.parent)
        assertNotNull(
            platforms.find { it.prefix == "native" && it.isIntermediate },
            "mingwX64 MUST trigger nativeMain intermediate",
        )
    }

    @Test
    fun `v2-5 AC 8 - all 9 new targets in single project produce expected family count`() {
        // End-to-end smoke: declare all 9 v2.5 new targets at once and assert the
        // family-grouping math: 1 wasmJs + 1 watchos + 1 tvos + 1 linux + 1 mingw
        // = 5 leaf platforms + 2 intermediates (native + web) = 7 platforms total.
        val platforms = PlatformDetector.detect(
            createMockKotlin(
                "wasmJs",
                "watchosX64", "watchosArm64", "watchosSimulatorArm64", "watchosDeviceArm64",
                "tvosX64", "tvosArm64", "tvosSimulatorArm64",
                "linuxX64", "mingwX64",
            ),
            logger,
        )
        // 5 leaf families: wasmJs, watchos, tvos, linux, mingw
        assertEquals(5, platforms.count { !it.isIntermediate })
        // 2 intermediates: native (4 native families) + web (1 web family)
        assertEquals(2, platforms.count { it.isIntermediate })
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
