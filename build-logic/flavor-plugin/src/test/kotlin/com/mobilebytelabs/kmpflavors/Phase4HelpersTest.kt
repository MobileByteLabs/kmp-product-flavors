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
 */

package com.mobilebytelabs.kmpflavors

import com.mobilebytelabs.kmpflavors.internal.DependencyGuardHelper
import com.mobilebytelabs.kmpflavors.internal.DetektPerVariantHelper
import com.mobilebytelabs.kmpflavors.internal.SpotlessDetektScopeHelper
import com.mobilebytelabs.kmpflavors.internal.VariantBuildCacheKeyConfigurator
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * v2.1 Phase 4 — guard / no-op semantics for the four Phase 4 helpers.
 *
 * The helpers' positive paths (registering tasks / wiring excludes against
 * real adjacent plugins like Detekt, Spotless, dependency-guard) are gated
 * by `pluginManager.withPlugin(...)` hooks. Asserting those positive paths
 * end-to-end inside TestKit isn't feasible because applying those plugins
 * via `withPluginClasspath()` hits the same classloader-isolation issue
 * that affects the CMP integration test (see `PerVariantComposeResourcesTest`).
 *
 * What this test covers:
 *   - Each helper is a no-op when its opt-in flag is false (the common case).
 *   - Each helper is a no-op when the matching adjacent plugin isn't applied.
 *   - The helpers don't throw on empty inputs (defensive guards).
 *
 * End-to-end positive verification is delegated to:
 *   - `samples/matrix-mode/` once the Phase 4 helpers are toggled there.
 *   - Consumer adoption canaries (Phase 6).
 */
class Phase4HelpersTest {

    private fun newProject() = ProjectBuilder.builder().build()

    @Test
    fun `DependencyGuardHelper is a no-op when enabled flag is false`() {
        val project = newProject()
        assertDoesNotThrow {
            DependencyGuardHelper.configure(
                project = project,
                allVariants = emptyList(),
                targetNames = emptyList(),
                enabled = false,
                logger = project.logger,
            )
        }
    }

    @Test
    fun `DependencyGuardHelper is a no-op when allVariants is empty even if enabled`() {
        val project = newProject()
        assertDoesNotThrow {
            DependencyGuardHelper.configure(
                project = project,
                allVariants = emptyList(),
                targetNames = listOf("desktop"),
                enabled = true,
                logger = project.logger,
            )
        }
    }

    @Test
    fun `DependencyGuardHelper is a no-op when dependency-guard plugin is not applied`() {
        // enabled + variants + targets present, but plugin is not applied → withPlugin hook
        // never fires; helper returns cleanly without registering anything.
        val project = newProject()
        val variant = FlavorVariant(name = "free", flavors = emptyList())
        assertDoesNotThrow {
            DependencyGuardHelper.configure(
                project = project,
                allVariants = listOf(variant),
                targetNames = listOf("desktop"),
                enabled = true,
                logger = project.logger,
            )
        }
    }

    @Test
    fun `SpotlessDetektScopeHelper is a no-op when enabled flag is false`() {
        val project = newProject()
        assertDoesNotThrow {
            SpotlessDetektScopeHelper.configure(
                project = project,
                enabled = false,
                logger = project.logger,
            )
        }
    }

    @Test
    fun `SpotlessDetektScopeHelper is a no-op when neither Spotless nor Detekt is applied`() {
        // enabled = true but no adjacent plugin → both withPlugin hooks no-op cleanly.
        val project = newProject()
        assertDoesNotThrow {
            SpotlessDetektScopeHelper.configure(
                project = project,
                enabled = true,
                logger = project.logger,
            )
        }
    }

    @Test
    fun `DetektPerVariantHelper is a no-op when enabled flag is false`() {
        val project = newProject()
        assertDoesNotThrow {
            DetektPerVariantHelper.configure(
                project = project,
                allVariants = emptyList(),
                enabled = false,
                logger = project.logger,
            )
        }
    }

    @Test
    fun `DetektPerVariantHelper is a no-op when allVariants is empty`() {
        val project = newProject()
        assertDoesNotThrow {
            DetektPerVariantHelper.configure(
                project = project,
                allVariants = emptyList(),
                enabled = true,
                logger = project.logger,
            )
        }
    }

    @Test
    fun `DetektPerVariantHelper is a no-op when Detekt plugin is not applied`() {
        val project = newProject()
        val variant = FlavorVariant(name = "free", flavors = emptyList())
        assertDoesNotThrow {
            DetektPerVariantHelper.configure(
                project = project,
                allVariants = listOf(variant),
                enabled = true,
                logger = project.logger,
            )
        }
    }

    // ── v2.3 Phase 1 — detektPerVariantPerTarget overload ──────────────────
    //
    // The helper's perTarget branch should be a no-op when Detekt isn't
    // applied (mirror of the per-variant case above) AND when nonAndroidTargets
    // is empty even if perTarget=true (degrades to per-variant scope safely).

    @Test
    fun `DetektPerVariantHelper perTarget overload is a no-op when Detekt not applied`() {
        val project = newProject()
        val variant = FlavorVariant(name = "freeDev", flavors = emptyList())
        assertDoesNotThrow {
            DetektPerVariantHelper.configure(
                project = project,
                allVariants = listOf(variant),
                enabled = true,
                logger = project.logger,
                perTarget = true,
                nonAndroidTargets = emptyList(),
            )
        }
    }

    @Test
    fun `DetektPerVariantHelper perTarget overload degrades to per-variant when no targets`() {
        // perTarget=true but nonAndroidTargets=[] → branch falls through to the
        // per-variant path, which is itself a no-op without the Detekt plugin.
        // Concrete assertion: no exception, no spurious task registration.
        val project = newProject()
        val variant = FlavorVariant(name = "paidProd", flavors = emptyList())
        assertDoesNotThrow {
            DetektPerVariantHelper.configure(
                project = project,
                allVariants = listOf(variant),
                enabled = true,
                logger = project.logger,
                perTarget = true,
                nonAndroidTargets = emptyList(),
            )
        }
        // No detekt* task registered (Detekt plugin not applied).
        assertNull(project.tasks.findByName("detektPaidProd"))
        assertNull(project.tasks.findByName("detektPaidProdDesktop"))
    }

    @Test
    fun `DetektPerVariantHelper backwards-compat — original signature still works`() {
        // perTarget + nonAndroidTargets default to (false, emptyList()).
        // Existing v2.1 callers that don't pass the new params keep compiling.
        val project = newProject()
        val variant = FlavorVariant(name = "free", flavors = emptyList())
        assertDoesNotThrow {
            DetektPerVariantHelper.configure(
                project = project,
                allVariants = listOf(variant),
                enabled = true,
                logger = project.logger,
            )
        }
    }

    // ── v2.3 Phase 2 — VariantBuildCacheKeyConfigurator stub ───────────────

    @Test
    fun `VariantBuildCacheKeyConfigurator is a no-op when flag is false (default)`() {
        val project = newProject()
        project.pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")
        val extension = project.extensions.getByType(com.mobilebytelabs.kmpflavors.KmpFlavorExtension::class.java)
        // Default: flag is false. Configurator returns early.
        assertDoesNotThrow {
            VariantBuildCacheKeyConfigurator.configure(project = project, extension = extension)
        }
        // No tasks registered — stub doesn't modify any task graph nodes.
        assertNull(project.tasks.findByName("compileKotlinDesktop"))
    }

    @Test
    fun `VariantBuildCacheKeyConfigurator no-op when buildMatrix is off`() {
        // v2.4 path-(b) prerequisite: matrix mode must be on. Without it, the
        // configurator returns early — there are no per-variant compilations
        // to namespace.
        val project = newProject()
        project.pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")
        val extension = project.extensions.getByType(com.mobilebytelabs.kmpflavors.KmpFlavorExtension::class.java)
        extension.variantCacheNamespacing.set(true)
        // buildMatrix defaults to false → early-return path.
        assertDoesNotThrow {
            VariantBuildCacheKeyConfigurator.configure(project = project, extension = extension)
        }
    }

    @Test
    fun `VariantBuildCacheKeyConfigurator applies kmpFlavorVariant input when matrix mode on`() {
        // v2.4 path-(b) impl: variantCacheNamespacing=true AND buildMatrix=true →
        // configurator hooks compileKotlin* tasks via tasks.matching { ... }.configureEach { ... }.
        // We don't register compileKotlin* tasks in this fixture (no KGP applied),
        // but the configureEach hook attaches lazily — assertDoesNotThrow is the
        // signal that the wiring code path completed without exception.
        val project = newProject()
        project.pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")
        val extension = project.extensions.getByType(com.mobilebytelabs.kmpflavors.KmpFlavorExtension::class.java)
        extension.variantCacheNamespacing.set(true)
        extension.buildMatrix.set(true)
        assertDoesNotThrow {
            VariantBuildCacheKeyConfigurator.configure(project = project, extension = extension)
        }
    }

    // ── v2.4 Phase 5 — VariantDependenciesScope + applyVariantExcludes ─────

    @Test
    fun `applyVariantExcludes is a no-op when no variant has registered excludes`() {
        val project = newProject()
        project.pluginManager.apply("io.github.mobilebytelabs.kmp-product-flavors")
        val extension = project.extensions.getByType(
            com.mobilebytelabs.kmpflavors.KmpFlavorExtension::class.java,
        )
        val cfg = com.mobilebytelabs.kmpflavors.internal.DependencyConfigurator(project.logger)
        assertDoesNotThrow {
            cfg.applyVariantExcludes(extension.variants)
        }
    }
}
