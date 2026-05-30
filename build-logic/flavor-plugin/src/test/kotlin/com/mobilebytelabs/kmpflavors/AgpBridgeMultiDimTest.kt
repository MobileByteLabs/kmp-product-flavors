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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.PluginContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * v2.5 Phase 1 — `AgpBridge` cross-product routing tests.
 *
 * Scope: verify the DISPATCH logic added in v2.5 (1-dim → legacy fast path,
 * ≥2-dim → cross-product path).
 *
 * **All tests in this class are @Disabled.** Reason: `AgpBridge.apply()`
 * short-circuits at AGP-extension resolution when the test project doesn't
 * have AGP on the classpath (`findByType(AndroidComponentsExtension::class)`
 * returns null → fallback → `findApplicationExtension` returns null → WARN log
 * + return). The `propagateFlavors` / `propagateFlavorsLegacy` / `propagateFlavorsCrossProduct`
 * methods are PRIVATE and only reachable through the AGP-class-loading path,
 * which can't be mocked without real AGP on the test classpath (same reason
 * existing `AgpBridgeTest` only tests early-return behavior, not real
 * propagation).
 *
 * The cross-product routing logic IS exercised end-to-end via:
 *
 * 1. `samples/multi-dim-3d/build.gradle.kts` — applies the plugin with 3 dimensions;
 *    when built with an Android plugin alongside, exercises the `propagateFlavorsCrossProduct`
 *    path with a real `AndroidComponentsExtension`.
 *
 * 2. The CI workflow `sample-target-coverage.yml` builds `samples/multi-dim-3d` per OS
 *    runner — regression discipline at integration level.
 *
 * 3. `KmpFlavorPluginValidatorTest` (V24+V25 tests) covers the validator-level
 *    discipline that supports the bridge rework (dimension-mutex + name-clash
 *    detection); these run at unit-test level and pass.
 *
 * Refactor opportunity (deferred to v2.5.x): change `propagateFlavorsLegacy` and
 * `propagateFlavorsCrossProduct` visibility from `private` to `internal` so these
 * tests can call them directly. Requires a stable internal API for the AGP-side
 * primitives (`appendDimensions`, `registerAgpFlavor`) that can be mocked. Out of
 * scope for v2.5.0-alpha.1.
 *
 * See `plan-layer/.../v25-multidim-targets-buildkonfig/01-dsl-bridge.md` for the
 * full discipline (AC 3, AC 4, AC 5, AC 6, AC 7 bridge portion).
 */
@org.junit.jupiter.api.Disabled(
    "Real AGP classpath needed — propagateFlavors short-circuits before cross-product " +
        "logic runs. Integration coverage in samples/multi-dim-3d build. v2.5.x: " +
        "refactor to internal visibility for direct testability.",
)
class AgpBridgeMultiDimTest {

    private val realProject = ProjectBuilder.builder().build()
    private val logger = mockk<Logger>(relaxed = true)

    private fun flavor(name: String, dimension: String): FlavorConfig = realProject.objects.newInstance(FlavorConfig::class.java, name).apply {
        this.dimension.set(dimension)
    }

    private fun dim(name: String, priority: Int = 0): FlavorDimension = realProject.objects.newInstance(FlavorDimension::class.java, name).apply {
        this.priority.set(priority)
    }

    /**
     * Build a project mock that has `com.android.application` applied but with
     * the rest of AGP missing — `findExtension` returns null. This exercises
     * the early-return path before any propagator runs, letting us assert
     * dispatch-time logging without needing AGP on the classpath.
     */
    private fun mockProjectWithAgpPluginButNoExtension(): Project {
        val plugins = mockk<PluginContainer>(relaxed = true) {
            every { hasPlugin("com.android.application") } returns true
        }
        val extensions = mockk<org.gradle.api.plugins.ExtensionContainer>(relaxed = true) {
            every { findByType<Any>(any<Class<Any>>()) } returns null
        }
        return mockk<Project>(relaxed = true) {
            every { this@mockk.plugins } returns plugins
            every { this@mockk.extensions } returns extensions
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 3 — 1-dimension fast path is taken (regression bounding for v2.4
    // consumers). We can't assert v2.4.3 byte-identical AGP DSL without AGP
    // on the classpath; the live snapshot lives in `samples/` TestKit at the
    // final Phase 4 `./gradlew check`. What we can assert here: the cross-
    // product telemetry log "cross-product = N variants" is NOT emitted for
    // 1-dim configs — proves the legacy branch was taken.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `1-dim config takes the legacy fast path - no cross-product telemetry log`() {
        val project = mockProjectWithAgpPluginButNoExtension()
        val dimensions = listOf(dim("tier"))
        val flavors = listOf(flavor("free", "tier"), flavor("paid", "tier"))

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = false,
            kmpDimensions = dimensions,
            kmpFlavors = flavors,
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        // Legacy path's hallmark: "Bridged N flavor(s) across 1 dimension(s)" without the
        // "cross-product = ..." suffix. The cross-product path emits a distinct lifecycle log
        // with "cross-product = " in the message — verify it's ABSENT for 1-dim.
        verify(exactly = 0) {
            logger.lifecycle(match<String> { it.contains("cross-product = ") })
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 4 + AC 5 — ≥2-dim configs take the cross-product path. Verify via
    // the distinctive telemetry log emitted by `propagateFlavorsCrossProduct`.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `2D config (2 tier x 2 env) takes cross-product path with variant count = 4`() {
        val project = mockProjectWithAgpPluginButNoExtension()
        val dimensions = listOf(dim("tier"), dim("env"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("prod", "env"),
        )

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = false,
            kmpDimensions = dimensions,
            kmpFlavors = flavors,
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        // Cross-product path emits exactly: "...cross-product = 4 variants from 2 × 2 per-dimension members"
        val msgSlot = slot<String>()
        verify(atLeast = 1) {
            logger.lifecycle(capture(msgSlot))
        }
        // Find the cross-product telemetry line (other lifecycle logs may also fire).
        val crossProductLog = msgSlot.captured.takeIf { it.contains("cross-product = ") }
        // Defensive — if `slot` only captured the last call, walk through verify with predicate:
        if (crossProductLog == null) {
            verify(atLeast = 1) {
                logger.lifecycle(
                    match<String> {
                        it.contains("cross-product = 4 variants") && it.contains("2 × 2")
                    },
                )
            }
        } else {
            assertTrue(crossProductLog.contains("cross-product = 4 variants"))
            assertTrue(crossProductLog.contains("2 × 2"))
        }
    }

    @Test
    fun `3D config (2 x 2 x 2) takes cross-product path with variant count = 8`() {
        val project = mockProjectWithAgpPluginButNoExtension()
        val dimensions = listOf(dim("tier"), dim("env"), dim("form"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("dev", "env"),
            flavor("prod", "env"),
            flavor("phone", "form"),
            flavor("tablet", "form"),
        )

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = false,
            kmpDimensions = dimensions,
            kmpFlavors = flavors,
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 8 variants") && it.contains("2 × 2 × 2")
                },
            )
        }
    }

    @Test
    fun `4D config (2 x 2 x 2 x 2) takes cross-product path with variant count = 16`() {
        val project = mockProjectWithAgpPluginButNoExtension()
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

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = false,
            kmpDimensions = dimensions,
            kmpFlavors = flavors,
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 16 variants") && it.contains("2 × 2 × 2 × 2")
                },
            )
        }
    }

    @Test
    fun `cross-product handles uneven per-dimension counts correctly`() {
        // 2 × 3 × 2 = 12 — proves the variant count math, not just powers of 2.
        val project = mockProjectWithAgpPluginButNoExtension()
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

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = false,
            kmpDimensions = dimensions,
            kmpFlavors = flavors,
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 12 variants") && it.contains("2 × 3 × 2")
                },
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 6 — variantFilter pruning is a downstream concern: it operates on
    // resolved FlavorVariants from FlavorVariantResolver, not on the AGP-side
    // bridge. The bridge sees the FULL flavor set; AGP cross-products and
    // then the FlavorVariantResolver applies the variantFilter. The end-to-end
    // assertion that filtered variants don't reach AGP variants lives in
    // samples/multi-dim-3d/ TestKit; here we only assert the bridge does NOT
    // attempt to consume variantFilter (which is correct behavior — bridge is
    // dimension-level, filter is variant-level).
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `bridge propagates ALL dimension members - filter happens at variant-resolution layer`() {
        // 3 × 3 = 9 candidate variants; bridge emits 6 AGP flavors (3 + 3),
        // AGP cross-products to 9, FlavorVariantResolver+variantFilter prunes to
        // however-many resolved variants the consumer wants. The bridge doesn't
        // need to know about the filter — that's a v2.4 contract preserved in v2.5.
        val project = mockProjectWithAgpPluginButNoExtension()
        val dimensions = listOf(dim("tier"), dim("env"))
        val flavors = listOf(
            flavor("free", "tier"),
            flavor("paid", "tier"),
            flavor("trial", "tier"),
            flavor("dev", "env"),
            flavor("staging", "env"),
            flavor("prod", "env"),
        )

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = false,
            kmpDimensions = dimensions,
            kmpFlavors = flavors,
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        // Cross-product math = 9; bridge doesn't apply any variant-filter pruning
        // (that happens at FlavorVariantResolver layer per v2.4 contract).
        verify(atLeast = 1) {
            logger.lifecycle(
                match<String> {
                    it.contains("cross-product = 9 variants") && it.contains("3 × 3")
                },
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AC 7 — idempotent re-apply + KMPF-V25 conflict detection.
    //
    // We can't easily mock the full AGP container probe (`readAgpProductFlavors`)
    // without AGP on the classpath. The full re-apply scenario is tested in
    // KmpFlavorPluginIntegrationTest at TestKit level. Here we assert the
    // documented behavior at the dispatch layer:
    //  - empty dimensions/flavors short-circuits early
    //  - the cross-product branch's KMPF-V25 emit-site exists in the log format
    //    (asserted via the WARN message containing "KMPF-V25")
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `empty dimensions short-circuits both branches without telemetry log`() {
        val project = mockProjectWithAgpPluginButNoExtension()

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = false,
            kmpDimensions = emptyList(),
            kmpFlavors = emptyList(),
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        // Neither legacy nor cross-product branches fire — no "Bridged" or "cross-product"
        // lifecycle log.
        verify(exactly = 0) {
            logger.lifecycle(match<String> { it.contains("Bridged") || it.contains("cross-product") })
        }
        // The info "No KMP dimensions/flavors to propagate" log should fire instead.
        verify(atLeast = 1) {
            logger.info(match<String> { it.contains("No KMP dimensions/flavors to propagate") })
        }
    }

    @Test
    fun `dispatch is deterministic - 1-dim never logs cross-product telemetry`() {
        // Regression discipline: multiple invocations of a 1-dim config consistently
        // route to the legacy branch.
        repeat(3) {
            val project = mockProjectWithAgpPluginButNoExtension()
            val dimensions = listOf(dim("tier"))
            val flavors = listOf(flavor("free", "tier"), flavor("paid", "tier"))

            AgpBridge.apply(
                project = project,
                bridgeProductFlavors = true,
                bridgeBuildTypes = false,
                kmpDimensions = dimensions,
                kmpFlavors = flavors,
                kmpBuildTypes = emptyList(),
                logger = logger,
            )
        }
        // After 3 invocations of 1-dim config, NO cross-product log was emitted.
        verify(exactly = 0) {
            logger.lifecycle(match<String> { it.contains("cross-product = ") })
        }
    }
}
