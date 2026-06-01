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
import com.mobilebytelabs.kmpflavors.internal.MockAndroidExtension
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.logging.Logger
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * v2.6 Tier C — `AgpBridge` cross-product routing tests, re-enabled.
 *
 * History: previously @Disabled because [AgpBridge.apply] short-circuits at the
 * AGP-extension resolution gate when no AGP classpath is present. v2.6 Tier C
 * (this commit) promotes [AgpBridge.propagateFlavorsLegacy] and
 * [AgpBridge.propagateFlavorsCrossProduct] from `private` to `internal` so
 * these tests can invoke them directly with a reflection-shaped
 * [MockAndroidExtension] — completely sidestepping the AGP-class-loading gate.
 *
 * The dispatcher [AgpBridge.apply] stays integration-tested via the
 * `samples/multi-dim-3d` TestKit build (it needs a real AGP classpath to
 * exercise `finalizeDsl`). Unit-test scope here is the per-branch propagator
 * behavior: which dimensions/flavors are added to the mock extension and
 * which telemetry lines the logger sees.
 *
 * See `plan-layer/.../v26-stability-parity-beyond-platform/01-coverage-gate.md`
 * Tier C (T6 + T7 + T8) for the contract.
 */
class AgpBridgeMultiDimTest {

    private val realProject = ProjectBuilder.builder().build()
    private val logger = mockk<Logger>(relaxed = true)

    private fun flavor(name: String, dimension: String): FlavorConfig =
        realProject.objects.newInstance(FlavorConfig::class.java, name).apply {
            this.dimension.set(dimension)
        }

    private fun dim(name: String, priority: Int = 0): FlavorDimension =
        realProject.objects.newInstance(FlavorDimension::class.java, name).apply {
            this.priority.set(priority)
        }

    // ─────────────────────────────────────────────────────────────────────
    // AC 3 — 1-dimension legacy path: byte-identical to v2.4.3 behaviour.
    // Direct call to propagateFlavorsLegacy verifies (a) flavor names landed
    // in AGP container, (b) dimension landed in flavorDimensions, (c) the
    // cross-product telemetry log is ABSENT (legacy path's signature).
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `legacy 1-dim path registers flavors and dimension on AGP extension`() {
        val ext = MockAndroidExtension()
        val dimensions = listOf(dim("tier"))
        val flavors = listOf(flavor("free", "tier"), flavor("paid", "tier"))

        AgpBridge.propagateFlavorsLegacy(ext, dimensions, flavors, logger)

        assertEquals(listOf("tier"), ext.getFlavorDimensions())
        assertEquals(setOf("free", "paid"), ext.getProductFlavors().getNames())
        assertEquals("tier", ext.getProductFlavors().get("free")?.dimension)
        assertEquals("tier", ext.getProductFlavors().get("paid")?.dimension)
        verify(exactly = 0) {
            logger.lifecycle(match<String> { it.contains("cross-product = ") })
        }
        verify(atLeast = 1) {
            logger.lifecycle(match<String> { it.contains("Bridged 2 flavor(s) across 1 dimension(s)") })
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 4 + AC 5 — ≥2-dim cross-product path: variant count math + dimension
    // ordering + per-flavor dimension assignment.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `cross-product 2D (2 tier x 2 env) emits 4-variant telemetry`() {
        val ext = MockAndroidExtension()
        val dimensions = listOf(dim("tier"), dim("env"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("prod", "env"),
        )

        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)

        assertEquals(listOf("tier", "env"), ext.getFlavorDimensions())
        assertEquals(setOf("free", "paid", "dev", "prod"), ext.getProductFlavors().getNames())
        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 4 variants") && it.contains("2 × 2")
                },
            )
        }
    }

    @Test
    fun `cross-product 3D (2 x 2 x 2) emits 8-variant telemetry`() {
        val ext = MockAndroidExtension()
        val dimensions = listOf(dim("tier"), dim("env"), dim("form"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("prod", "env"),
            flavor("phone", "form"),
            flavor("tablet", "form"),
        )

        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)

        assertEquals(listOf("tier", "env", "form"), ext.getFlavorDimensions())
        assertEquals(6, ext.getProductFlavors().getNames().size)
        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 8 variants") && it.contains("2 × 2 × 2")
                },
            )
        }
    }

    @Test
    fun `cross-product 4D (2 x 2 x 2 x 2) emits 16-variant telemetry`() {
        val ext = MockAndroidExtension()
        val dimensions = listOf(dim("tier"), dim("env"), dim("form"), dim("locale"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("prod", "env"),
            flavor("phone", "form"),
            flavor("tablet", "form"),
            flavor("en", "locale"),
            flavor("ja", "locale"),
        )

        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)

        assertEquals(8, ext.getProductFlavors().getNames().size)
        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 16 variants") && it.contains("2 × 2 × 2 × 2")
                },
            )
        }
    }

    @Test
    fun `cross-product handles uneven per-dimension counts (2 x 3 x 2 = 12)`() {
        val ext = MockAndroidExtension()
        val dimensions = listOf(dim("tier"), dim("env"), dim("form"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("staging", "env"),
            flavor("prod", "env"),
            flavor("phone", "form"),
            flavor("tablet", "form"),
        )

        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)

        assertEquals(7, ext.getProductFlavors().getNames().size)
        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 12 variants") && it.contains("2 × 3 × 2")
                },
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 6 — bridge propagates ALL dimension members; variantFilter pruning
    // happens at FlavorVariantResolver layer, not at the bridge.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `bridge propagates ALL dimension members - filter happens elsewhere`() {
        val ext = MockAndroidExtension()
        val dimensions = listOf(dim("tier"), dim("env"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("trial", "tier"),
            flavor("dev", "env"),
            flavor("staging", "env"),
            flavor("prod", "env"),
        )

        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)

        // 3 tier + 3 env = 6 flavors land on AGP; cross-product math says 9 variants.
        assertEquals(6, ext.getProductFlavors().getNames().size)
        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 9 variants") && it.contains("3 × 3")
                },
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 7 — re-apply idempotency + KMPF-V25 conflict detection.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `re-apply with same flavor set is idempotent - no duplicate registration`() {
        val ext = MockAndroidExtension()
        val dimensions = listOf(dim("tier"), dim("env"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("prod", "env"),
        )

        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)
        // Second call should detect existing flavor coverage and short-circuit.
        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)

        assertEquals(4, ext.getProductFlavors().getNames().size)
        verify(atLeast = 1) {
            logger.info(
                match<String> {
                    it.contains("already registered") && it.contains("idempotent, ≥2-dim re-apply")
                },
            )
        }
    }

    @Test
    fun `KMPF-V25 conflict warn fires when AGP flavors differ from KMP set`() {
        val ext = MockAndroidExtension()
        // Consumer hand-wrote android { productFlavors { create("legacyAlpha") } }
        // before kmpFlavors {} got a chance to bridge.
        ext.getProductFlavors().maybeCreate("legacyAlpha")

        val dimensions = listOf(dim("tier"), dim("env"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("prod", "env"),
        )

        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)

        // Bridge skipped — no KMP flavors added.
        assertEquals(setOf("legacyAlpha"), ext.getProductFlavors().getNames())
        verify(atLeast = 1) {
            logger.warn(match<String> { it.contains("KMPF-V25") })
        }
    }

    @Test
    fun `empty inputs to either propagator register nothing on AGP extension`() {
        // The "No KMP dimensions/flavors to propagate" short-circuit lives in the
        // private propagateFlavors dispatcher, exercised end-to-end via the
        // samples/multi-dim-3d TestKit build. At the per-branch level we only
        // assert: empty inputs produce no flavor registrations and no dimensions.
        val ext = MockAndroidExtension()

        AgpBridge.propagateFlavorsCrossProduct(ext, emptyList(), emptyList(), logger)
        AgpBridge.propagateFlavorsLegacy(ext, emptyList(), emptyList(), logger)

        assertTrue(ext.getProductFlavors().getNames().isEmpty())
        assertTrue(ext.getFlavorDimensions().isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 8 — dimension priority ordering: higher-priority dimensions land
    // first in flavorDimensions list, matching FlavorVariantResolver order.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `dimension priority controls flavorDimensions ordering`() {
        val ext = MockAndroidExtension()
        // env priority 10, tier priority 5 → env should appear before tier.
        val dimensions = listOf(dim("tier", priority = 5), dim("env", priority = 10))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("prod", "env"),
        )

        AgpBridge.propagateFlavorsCrossProduct(ext, dimensions, flavors, logger)

        assertEquals(listOf("env", "tier"), ext.getFlavorDimensions())
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 9 — legacy path's per-flavor dimension assignment + matchingFallbacks.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `legacy path assigns dimension on each created flavor`() {
        val ext = MockAndroidExtension()
        val dimensions = listOf(dim("tier"))
        val flavors = listOf(flavor("free", "tier"), flavor("paid", "tier"))

        AgpBridge.propagateFlavorsLegacy(ext, dimensions, flavors, logger)

        assertEquals("tier", ext.getProductFlavors().get("free")?.dimension)
        assertEquals("tier", ext.getProductFlavors().get("paid")?.dimension)
        // Suffixes not set on these flavors → mock fields stay null.
        assertNull(ext.getProductFlavors().get("free")?.applicationIdSuffix)
        assertNull(ext.getProductFlavors().get("free")?.versionNameSuffix)
    }
}
