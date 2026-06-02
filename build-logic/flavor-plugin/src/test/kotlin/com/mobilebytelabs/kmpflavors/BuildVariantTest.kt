/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildVariantTest {

    private fun project() = ProjectBuilder.builder().build()

    private fun newFlavor(name: String): FlavorConfig =
        project().objects.newInstance(FlavorConfig::class.java, name)

    private fun newBuildType(name: String): BuildTypeConfig =
        project().objects.newInstance(BuildTypeConfig::class.java, name)

    @Test
    fun `fromFlavorVariant creates BuildVariant without buildType`() {
        val fv = FlavorVariant("free", listOf(newFlavor("free")))
        val bv = BuildVariant.fromFlavorVariant(fv)
        assertEquals("free", bv.name)
        assertEquals(fv, bv.flavorVariant)
        assertNull(bv.buildType)
        assertFalse(bv.isDebuggable)
        assertFalse(bv.isMinifyEnabled)
    }

    @Test
    fun `fromFlavorVariantAndBuildType joins name with capitalized buildType`() {
        val fv = FlavorVariant("free", listOf(newFlavor("free")))
        val bt = newBuildType("debug")
        val bv = BuildVariant.fromFlavorVariantAndBuildType(fv, bt)
        assertEquals("freeDebug", bv.name)
        assertEquals(bt, bv.buildType)
    }

    @Test
    fun `empty flavor variant name yields just the buildType`() {
        val fv = FlavorVariant("", emptyList())
        val bt = newBuildType("release")
        val bv = BuildVariant.fromFlavorVariantAndBuildType(fv, bt)
        assertEquals("release", bv.name)
    }

    @Test
    fun `isDebuggable mirrors buildType debug default`() {
        val fv = FlavorVariant("free", listOf(newFlavor("free")))
        val debugBt = newBuildType("debug")
        val releaseBt = newBuildType("release")
        assertTrue(BuildVariant.fromFlavorVariantAndBuildType(fv, debugBt).isDebuggable)
        assertFalse(BuildVariant.fromFlavorVariantAndBuildType(fv, releaseBt).isDebuggable)
    }

    @Test
    fun `isMinifyEnabled mirrors buildType release default`() {
        val fv = FlavorVariant("free", listOf(newFlavor("free")))
        val releaseBt = newBuildType("release")
        val debugBt = newBuildType("debug")
        assertTrue(BuildVariant.fromFlavorVariantAndBuildType(fv, releaseBt).isMinifyEnabled)
        assertFalse(BuildVariant.fromFlavorVariantAndBuildType(fv, debugBt).isMinifyEnabled)
    }

    @Test
    fun `flavors and flavorNames forward to flavorVariant`() {
        val f1 = newFlavor("free")
        val f2 = newFlavor("dev")
        val fv = FlavorVariant("freeDev", listOf(f1, f2))
        val bv = BuildVariant.fromFlavorVariant(fv)
        assertEquals(listOf(f1, f2), bv.flavors)
        assertEquals(listOf("free", "dev"), bv.flavorNames)
    }

    @Test
    fun `mergedBuildConfigFields buildType overrides flavor`() {
        val free = newFlavor("free").apply { buildConfigField("Boolean", "FLAG", "true") }
        val debug = newBuildType("debug").apply { buildConfigField("Boolean", "FLAG", "false") }
        val fv = FlavorVariant("free", listOf(free))
        val bv = BuildVariant.fromFlavorVariantAndBuildType(fv, debug)
        assertEquals("false", bv.mergedBuildConfigFields["FLAG"]!!.value)
    }

    @Test
    fun `combinedApplicationIdSuffix joins flavor and buildType`() {
        val free = newFlavor("free").apply { applicationIdSuffix.set(".free") }
        val debug = newBuildType("debug").apply { applicationIdSuffix.set(".debug") }
        val fv = FlavorVariant("free", listOf(free))
        val bv = BuildVariant.fromFlavorVariantAndBuildType(fv, debug)
        assertEquals(".free.debug", bv.combinedApplicationIdSuffix)
    }

    @Test
    fun `combinedBundleIdSuffix joins flavor and buildType`() {
        val free = newFlavor("free").apply { bundleIdSuffix.set(".free") }
        val debug = newBuildType("debug").apply { bundleIdSuffix.set(".debug") }
        val fv = FlavorVariant("free", listOf(free))
        val bv = BuildVariant.fromFlavorVariantAndBuildType(fv, debug)
        assertEquals(".free.debug", bv.combinedBundleIdSuffix)
    }

    @Test
    fun `combinedVersionNameSuffix joins flavor and buildType`() {
        val free = newFlavor("free").apply { versionNameSuffix.set("-free") }
        val debug = newBuildType("debug").apply { versionNameSuffix.set("-debug") }
        val fv = FlavorVariant("free", listOf(free))
        val bv = BuildVariant.fromFlavorVariantAndBuildType(fv, debug)
        assertEquals("-free-debug", bv.combinedVersionNameSuffix)
    }

    @Test
    fun `allDependencies merges flavor and buildType`() {
        val free = newFlavor("free").apply { dependency("implementation", "a:1") }
        val debug = newBuildType("debug").apply { dependency("implementation", "b:1") }
        val fv = FlavorVariant("free", listOf(free))
        val bv = BuildVariant.fromFlavorVariantAndBuildType(fv, debug)
        assertEquals(2, bv.allDependencies.size)
        assertTrue(bv.allDependencies.any { it.notation == "a:1" })
        assertTrue(bv.allDependencies.any { it.notation == "b:1" })
    }

    @Test
    fun `mergedMatchingFallbacks merges and dedupes flavor and buildType`() {
        val free = newFlavor("free").apply { matchingFallbacks("a", "b") }
        val debug = newBuildType("debug").apply { matchingFallbacks("b", "c") }
        val fv = FlavorVariant("free", listOf(free))
        val bv = BuildVariant.fromFlavorVariantAndBuildType(fv, debug)
        assertEquals(listOf("a", "b", "c"), bv.mergedMatchingFallbacks)
    }

    @Test
    fun `BuildVariant equality contract via data class`() {
        val free = newFlavor("free")
        val debug = newBuildType("debug")
        val fv = FlavorVariant("free", listOf(free))
        val a = BuildVariant.fromFlavorVariantAndBuildType(fv, debug)
        val b = BuildVariant.fromFlavorVariantAndBuildType(fv, debug)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
