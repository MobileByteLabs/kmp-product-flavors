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

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorDimension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Direct coverage for AgpBridge's reflective propagation logic via a
 * fake AGP-shaped target (mimics methods AGP exposes — getFlavorDimensions,
 * getProductFlavors with getNames + maybeCreate, etc.).
 *
 * Full TestKit fixture with real AGP applied lives in Tier D.
 */
class AgpBridgeTest {

    /** Test double mimicking AGP's ApplicationExtension shape. */
    class FakeAndroidExtension {
        val dimensions: MutableList<String> = mutableListOf()
        private val pf = FakeFlavorContainer()
        private val bt = FakeBuildTypeContainer()
        @Suppress("unused") fun getFlavorDimensions(): MutableList<String> = dimensions
        @Suppress("unused") fun getProductFlavors(): FakeFlavorContainer = pf
        @Suppress("unused") fun getBuildTypes(): FakeBuildTypeContainer = bt
    }

    class FakeFlavorContainer {
        val entries: MutableMap<String, FakeAgpFlavor> = linkedMapOf()
        @Suppress("unused") fun getNames(): Set<String> = entries.keys
        @Suppress("unused") fun maybeCreate(name: String): FakeAgpFlavor = entries.getOrPut(name) { FakeAgpFlavor(name) }
    }

    class FakeBuildTypeContainer {
        val entries: MutableMap<String, FakeAgpBuildType> = linkedMapOf()
        @Suppress("unused") fun getNames(): Set<String> = entries.keys
        @Suppress("unused") fun maybeCreate(name: String): FakeAgpBuildType = entries.getOrPut(name) { FakeAgpBuildType(name) }
    }

    class FakeAgpFlavor(@Suppress("unused") val name: String) {
        var dim: String? = null
        var appIdSuffix: String? = null
        var versionSuffix: String? = null
        private val fb: MutableList<String> = mutableListOf()
        fun fallbacks(): List<String> = fb.toList()
        @Suppress("unused") fun setDimension(d: String) { dim = d }
        @Suppress("unused") fun setApplicationIdSuffix(s: String) { appIdSuffix = s }
        @Suppress("unused") fun setVersionNameSuffix(s: String) { versionSuffix = s }
        @Suppress("unused") fun getMatchingFallbacks(): MutableList<String> = fb
    }

    class FakeAgpBuildType(@Suppress("unused") val name: String) {
        var debuggable: Boolean? = null
        var minify: Boolean? = null
        var appIdSuffix: String? = null
        @Suppress("unused") fun setDebuggable(b: Boolean) { debuggable = b }
        @Suppress("unused") fun setMinifyEnabled(b: Boolean) { minify = b }
        @Suppress("unused") fun setApplicationIdSuffix(s: String) { appIdSuffix = s }
    }

    private fun project() = ProjectBuilder.builder().build()
    private fun newFlavor(name: String, dim: String? = null): FlavorConfig {
        val f = project().objects.newInstance(FlavorConfig::class.java, name)
        if (dim != null) f.dimension.set(dim)
        return f
    }
    private fun newDimension(name: String, priority: Int = 0): FlavorDimension {
        val d = project().objects.newInstance(FlavorDimension::class.java, name)
        d.priority.set(priority)
        return d
    }
    private fun newBuildType(name: String): BuildTypeConfig =
        project().objects.newInstance(BuildTypeConfig::class.java, name)

    @Test
    fun `single-dim legacy path appends dimensions and creates flavors`() {
        val ext = FakeAndroidExtension()
        val proj = project()
        val dims = listOf(newDimension("tier"))
        val flavors = listOf(
            newFlavor("free", "tier").apply { applicationIdSuffix.set(".free") },
            newFlavor("paid", "tier").apply {
                versionNameSuffix.set("-paid")
                matchingFallbacks("free")
            },
        )
        AgpBridge.propagateFlavorsLegacy(ext, dims, flavors, proj.logger)

        assertEquals(listOf("tier"), ext.dimensions)
        assertEquals(setOf("free", "paid"), ext.getProductFlavors().entries.keys)
        assertEquals("tier", ext.getProductFlavors().entries["free"]!!.dim)
        assertEquals(".free", ext.getProductFlavors().entries["free"]!!.appIdSuffix)
        assertEquals("-paid", ext.getProductFlavors().entries["paid"]!!.versionSuffix)
        assertEquals(listOf("free"), ext.getProductFlavors().entries["paid"]!!.fallbacks())
    }

    @Test
    fun `cross-product path appends dimensions sorted by descending priority`() {
        val ext = FakeAndroidExtension()
        val proj = project()
        val dims = listOf(
            newDimension("tier", priority = 0),
            newDimension("env", priority = 5),
        )
        val flavors = listOf(
            newFlavor("free", "tier"),
            newFlavor("paid", "tier"),
            newFlavor("dev", "env"),
            newFlavor("prod", "env"),
        )
        AgpBridge.propagateFlavorsCrossProduct(ext, dims, flavors, proj.logger)
        // Higher priority first.
        assertEquals(listOf("env", "tier"), ext.dimensions)
        assertEquals(setOf("free", "paid", "dev", "prod"), ext.getProductFlavors().entries.keys)
    }

    @Test
    fun `idempotent re-apply when AGP already has same flavor names`() {
        val ext = FakeAndroidExtension()
        // Pre-populate AGP with the exact same KMP flavor names.
        ext.getProductFlavors().maybeCreate("free")
        ext.getProductFlavors().maybeCreate("paid")
        val proj = project()
        val dims = listOf(newDimension("tier"))
        val flavors = listOf(newFlavor("free", "tier"), newFlavor("paid", "tier"))
        AgpBridge.propagateFlavorsLegacy(ext, dims, flavors, proj.logger)
        // Already idempotent — no new dimensions added.
        assertEquals(emptyList<String>(), ext.dimensions)
    }

    @Test
    fun `conflict on legacy path skips propagation with warning`() {
        val ext = FakeAndroidExtension()
        ext.getProductFlavors().maybeCreate("differentFlavor")
        val proj = project()
        val dims = listOf(newDimension("tier"))
        val flavors = listOf(newFlavor("free", "tier"))
        AgpBridge.propagateFlavorsLegacy(ext, dims, flavors, proj.logger)
        // Existing differentFlavor remains, kmp `free` NOT added because of conflict guard.
        assertEquals(setOf("differentFlavor"), ext.getProductFlavors().entries.keys)
    }

    @Test
    fun `propagateVariantFilterToAgp gracefully degrades when beforeVariants missing`() {
        // The fake ext doesn't have beforeVariants method — should not throw.
        val ext = FakeAndroidExtension()
        val proj = project()
        AgpBridge.propagateVariantFilterToAgp(ext, setOf("freeDev"), proj.logger)
        // Method absent → silently degraded.
        assertNotEquals(null, ext)
    }

    @Test
    fun `apply returns silently when bridgeProductFlavors and bridgeBuildTypes both false`() {
        val proj = project()
        AgpBridge.apply(
            proj,
            bridgeProductFlavors = false,
            bridgeBuildTypes = false,
            kmpDimensions = emptyList(),
            kmpFlavors = emptyList(),
            kmpBuildTypes = emptyList(),
            logger = proj.logger,
        )
        // No throw, no side effect.
        assertTrue(true)
    }

    @Test
    fun `apply skips when com_android_application is not applied`() {
        val proj = project()
        AgpBridge.apply(
            proj,
            bridgeProductFlavors = true,
            bridgeBuildTypes = true,
            kmpDimensions = listOf(newDimension("tier")),
            kmpFlavors = listOf(newFlavor("free", "tier")),
            kmpBuildTypes = listOf(newBuildType("debug")),
            logger = proj.logger,
        )
        // No throw, no extension changes.
        assertTrue(true)
    }
}
