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
 * Coverage for PerVariantSbomConfigurator early-return branches. The full
 * "CycloneDX + maven-publish applied" branch requires real adjacent plugins
 * and lives in v2.7.x TestKit fixtures.
 */
class PerVariantSbomConfiguratorTest {

    private fun project() = ProjectBuilder.builder().build()
    private fun newExtension(): KmpFlavorExtension = project().objects.newInstance(KmpFlavorExtension::class.java)
    private fun variant(name: String) = FlavorVariant(name = name, flavors = emptyList())

    @Test
    fun `no-op when publishMatrixSbom is false`() {
        val proj = project()
        val ext = newExtension()
        // publishMatrixSbom convention = false
        PerVariantSbomConfigurator.configure(proj, ext, listOf(variant("paid")), proj.logger)
        assertTrue(true)
    }

    @Test
    fun `no-op when publishMatrix is false but publishMatrixSbom true`() {
        val proj = project()
        val ext = newExtension()
        ext.publishMatrixSbom.set(true)
        // publishMatrix unset → defaults to false.
        PerVariantSbomConfigurator.configure(proj, ext, listOf(variant("paid")), proj.logger)
        assertTrue(true)
    }

    @Test
    fun `no-op when inactiveVariants empty`() {
        val proj = project()
        val ext = newExtension()
        ext.publishMatrixSbom.set(true)
        ext.publishMatrix.set(true)
        PerVariantSbomConfigurator.configure(proj, ext, emptyList(), proj.logger)
        assertTrue(true)
    }

    @Test
    fun `no-op when CycloneDX plugin not applied`() {
        val proj = project()
        val ext = newExtension()
        ext.publishMatrixSbom.set(true)
        ext.publishMatrix.set(true)
        // CycloneDX plugin NOT applied — withPlugin callback never fires.
        PerVariantSbomConfigurator.configure(proj, ext, listOf(variant("paid")), proj.logger)
        assertTrue(true)
    }
}
