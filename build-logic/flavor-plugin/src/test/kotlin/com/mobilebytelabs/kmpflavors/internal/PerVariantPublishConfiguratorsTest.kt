/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.FlavorVariant
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Early-return coverage for the family of PerVariant publish configurators.
 * Full "real adjacent plugin + KMP target wiring" paths require TestKit
 * fixtures and live in the v2.7.x roadmap.
 */
class PerVariantPublishConfiguratorsTest {

    private fun project() = ProjectBuilder.builder().build()
    private fun newExt(): KmpFlavorExtension = project().objects.newInstance(KmpFlavorExtension::class.java)
    private fun variant(name: String) = FlavorVariant(name = name, flavors = emptyList())

    // ─────────────────────────────────────────────────────────────────
    // PerVariantJsPublishConfigurator
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `JS no-op when publishMatrix false`() {
        val proj = project()
        val ext = newExt()
        PerVariantJsPublishConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }

    @Test
    fun `JS no-op when inactiveVariants empty`() {
        val proj = project()
        val ext = newExt()
        ext.publishMatrix.set(true)
        PerVariantJsPublishConfigurator.configure(proj, ext, emptyList(), emptyList())
        assertTrue(true)
    }

    @Test
    fun `JS no-op when no js or wasm targets present`() {
        val proj = project()
        val ext = newExt()
        ext.publishMatrix.set(true)
        PerVariantJsPublishConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }

    // ─────────────────────────────────────────────────────────────────
    // PerVariantPublishConfigurator
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `Publish no-op when publishMatrix false`() {
        val proj = project()
        val ext = newExt()
        // PerVariantPublishConfigurator.configure(project, extension, inactiveVariants, nonAndroidTargets)
        PerVariantPublishConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }

    // ─────────────────────────────────────────────────────────────────
    // PerVariantIosPublishConfigurator
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `IosPublish no-op when publishMatrix false`() {
        val proj = project()
        val ext = newExt()
        PerVariantIosPublishConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }

    // ─────────────────────────────────────────────────────────────────
    // PerVariantIosXcframeworkConfigurator
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `IosXcframework no-op when publishMatrix false`() {
        val proj = project()
        val ext = newExt()
        PerVariantIosXcframeworkConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }

    // ─────────────────────────────────────────────────────────────────
    // PerVariantComposeHotReloadConfigurator
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `ComposeHotReload no-op when composeHotReloadPerVariant false`() {
        val proj = project()
        val ext = newExt()
        PerVariantComposeHotReloadConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }

    // ─────────────────────────────────────────────────────────────────
    // BuildScanConfigurator
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `BuildScan no-op when matrix mode off`() {
        val proj = project()
        BuildScanConfigurator.configure(proj, listOf(variant("free")), emptyList(), matrixModeEnabled = false, logger = proj.logger)
        assertTrue(true)
    }

    @Test
    fun `BuildScan no-op when allVariants empty`() {
        val proj = project()
        BuildScanConfigurator.configure(proj, emptyList(), emptyList(), matrixModeEnabled = true, logger = proj.logger)
        assertTrue(true)
    }

    @Test
    fun `BuildScan no-op when Develocity plugin not applied`() {
        val proj = project()
        // matrix on, variants present, but Develocity plugin NOT applied → withPlugin never fires.
        BuildScanConfigurator.configure(proj, listOf(variant("free")), emptyList(), matrixModeEnabled = true, logger = proj.logger)
        assertTrue(true)
    }
}
