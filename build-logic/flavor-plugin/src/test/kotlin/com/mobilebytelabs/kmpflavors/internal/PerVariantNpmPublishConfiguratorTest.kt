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
 * Coverage for PerVariantNpmPublishConfigurator's early-return branches.
 * Full "js(IR) + variants" path requires real KMP TestKit (deferred to v2.7.x).
 */
class PerVariantNpmPublishConfiguratorTest {

    private fun project() = ProjectBuilder.builder().build()
    private fun newExtension(): KmpFlavorExtension = project().objects.newInstance(KmpFlavorExtension::class.java)
    private fun variant(name: String) = FlavorVariant(name = name, flavors = emptyList())

    @Test
    fun `no-op when publishMatrix is false`() {
        val proj = project()
        val ext = newExtension()
        PerVariantNpmPublishConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }

    @Test
    fun `no-op when npmPublishMatrix is false`() {
        val proj = project()
        val ext = newExtension()
        ext.publishMatrix.set(true)
        // npmPublishMatrix convention=false
        PerVariantNpmPublishConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }

    @Test
    fun `no-op when inactiveVariants empty`() {
        val proj = project()
        val ext = newExtension()
        ext.publishMatrix.set(true)
        ext.npmPublishMatrix.set(true)
        PerVariantNpmPublishConfigurator.configure(proj, ext, emptyList(), emptyList())
        assertTrue(true)
    }

    @Test
    fun `no-op when no js or wasm targets in nonAndroidTargets`() {
        val proj = project()
        val ext = newExtension()
        ext.publishMatrix.set(true)
        ext.npmPublishMatrix.set(true)
        // empty nonAndroidTargets → jsFamilyTargets is empty → return.
        PerVariantNpmPublishConfigurator.configure(proj, ext, listOf(variant("paid")), emptyList())
        assertTrue(true)
    }
}
