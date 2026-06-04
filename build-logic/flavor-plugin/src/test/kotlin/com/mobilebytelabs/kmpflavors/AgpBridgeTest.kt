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

import com.mobilebytelabs.kmpflavors.internal.AgpBridge
import com.mobilebytelabs.kmpflavors.internal.FlavorVariantResolver
import com.mobilebytelabs.kmpflavors.internal.MockAndroidComponentsExtension
import com.mobilebytelabs.kmpflavors.internal.MockVariantBuilder
import com.mobilebytelabs.kmpflavors.internal.MockVariantBuilderMissingSetEnabled
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.PluginContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Smoke tests for the AGP bridge.
 *
 * Full integration tests that exercise actual AGP propagation live in
 * `KmpFlavorPluginIntegrationTest` (TestKit-based). These tests verify the
 * defensive behaviours that don't require AGP on the classpath.
 */
class AgpBridgeTest {

    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun `apply is a no-op when both flags are disabled`() {
        val project = mockk<Project>(relaxed = true)

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = false,
            bridgeBuildTypes = false,
            kmpDimensions = emptyList(),
            kmpFlavors = emptyList(),
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        // No project lookup should have happened — early return.
        verify(exactly = 0) { project.plugins }
    }

    @Test
    fun `apply skips silently when com_android_application is not applied`() {
        val plugins = mockk<PluginContainer>(relaxed = true) {
            every { hasPlugin("com.android.application") } returns false
        }
        val project = mockk<Project>(relaxed = true) {
            every { this@mockk.plugins } returns plugins
        }

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = true,
            kmpDimensions = emptyList(),
            kmpFlavors = emptyList(),
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        // Should log info (skip) and never touch project.extensions.
        verify(atLeast = 1) { logger.info(match<String> { it.contains("skipping AGP bridge") }) }
        verify(exactly = 0) { project.extensions }
    }

    // ─────────────────────────────────────────────────────────────────────
    // v2.6 Phase 2 — propagateVariantFilterToAgp tests
    //
    // Direct calls to the new internal helper with MockAndroidComponentsExtension
    // (same pattern as Phase 1's MockAndroidExtension). Tests cover the three
    // documented branches: success (variant disabled), graceful fallback (setter
    // missing), and the variant-name parity contract that the AGP-side disable
    // mechanism depends on.
    // ─────────────────────────────────────────────────────────────────────

    private val realProject = ProjectBuilder.builder().build()

    private fun flavor(name: String, dimension: String): FlavorConfig = realProject.objects.newInstance(FlavorConfig::class.java, name).apply {
        this.dimension.set(dimension)
    }

    private fun dim(name: String, priority: Int = 0): FlavorDimension = realProject.objects.newInstance(FlavorDimension::class.java, name).apply {
        this.priority.set(priority)
    }

    @Test
    fun `variantFilter forwards to AGP for single-dim config`() {
        val mockComponents = MockAndroidComponentsExtension()
        // KMP filtered: only freeDebug allowed.
        AgpBridge.propagateVariantFilterToAgp(mockComponents, setOf("freeDebug"), logger)

        // Simulate AGP firing beforeVariants for all 4 cross-product variants.
        val agpVariants = listOf(
            MockVariantBuilder("freeDebug"),
            MockVariantBuilder("freeRelease"),
            MockVariantBuilder("paidDebug"),
            MockVariantBuilder("paidRelease"),
        )
        agpVariants.forEach { mockComponents.fireRegisteredAction(it) }

        assertEquals(
            setOf("freeDebug"),
            agpVariants.filter { it.enabled }.map { it.name }.toSet(),
        )
    }

    @Test
    fun `variantFilter forwards to AGP for 2D cross-product config`() {
        val mockComponents = MockAndroidComponentsExtension()
        // KMP filtered out freeProd + paidDev.
        AgpBridge.propagateVariantFilterToAgp(mockComponents, setOf("freeDev", "paidProd"), logger)

        val agpVariants = listOf("freeDev", "freeProd", "paidDev", "paidProd").map { MockVariantBuilder(it) }
        agpVariants.forEach { mockComponents.fireRegisteredAction(it) }

        assertEquals(
            setOf("freeDev", "paidProd"),
            agpVariants.filter { it.enabled }.map { it.name }.toSet(),
        )
    }

    @Test
    fun `propagateVariantFilterToAgp logs WARN when setEnabled missing (AGP fallback)`() {
        val mockComponents = MockAndroidComponentsExtension()
        AgpBridge.propagateVariantFilterToAgp(mockComponents, setOf("freeDebug"), logger)

        // Variant builder is shaped without setEnabled() — bridge can't disable; logs WARN.
        val variant = MockVariantBuilderMissingSetEnabled("paidDebug")
        mockComponents.fireRegisteredAction(variant)

        // Still enabled — bridge had no way to flip it.
        assertTrue(variant.enabled)
        verify {
            logger.warn(
                match<String> {
                    it.contains("KMP↔AGP parity") && it.contains("neither setEnabled() nor setEnable() resolved")
                },
            )
        }
    }

    @Test
    fun `KMP buildVariantName matches AGP variant-name for 2D config`() {
        val kmpVariants = FlavorVariantResolver.resolveAllVariants(
            dimensions = listOf(dim("tier"), dim("env")),
            flavors = listOf(
                flavor("free", "tier"),
                flavor("paid", "tier"),
                flavor("dev", "env"),
                flavor("prod", "env"),
            ),
        )
        // AGP's variant-name convention: <flavor1><Flavor2> (lowercase-first +
        // capitalize subsequent) — same as KMP's buildVariantName.
        assertEquals(
            setOf("freeDev", "freeProd", "paidDev", "paidProd"),
            kmpVariants.map { it.name }.toSet(),
        )
    }

    @Test
    fun `KMP buildVariantName matches AGP variant-name for 3D config`() {
        val kmpVariants = FlavorVariantResolver.resolveAllVariants(
            dimensions = listOf(dim("tier"), dim("env"), dim("form")),
            flavors = listOf(
                flavor("free", "tier"),
                flavor("paid", "tier"),
                flavor("dev", "env"),
                flavor("prod", "env"),
                flavor("phone", "form"),
                flavor("tablet", "form"),
            ),
        )
        assertEquals(
            setOf(
                "freeDevPhone",
                "freeDevTablet",
                "freeProdPhone",
                "freeProdTablet",
                "paidDevPhone",
                "paidDevTablet",
                "paidProdPhone",
                "paidProdTablet",
            ),
            kmpVariants.map { it.name }.toSet(),
        )
    }
}
