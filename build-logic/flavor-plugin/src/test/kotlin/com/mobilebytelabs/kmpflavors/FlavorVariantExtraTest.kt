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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for FlavorVariant lazy properties NOT exercised by the existing
 * FlavorVariantTest (which covers mergedBuildConfigFields, mergedExtras,
 * excludedTargets, and the BuildConfigField shape).
 */
class FlavorVariantExtraTest {

    private fun newFlavor(name: String, dim: String? = null): FlavorConfig {
        val f = ProjectBuilder.builder().build().objects.newInstance(FlavorConfig::class.java, name)
        if (dim != null) f.dimension.set(dim)
        return f
    }

    @Test
    fun `combinedDesktopTitleSuffix joins with spaces`() {
        val a = newFlavor("free").apply { desktopWindowTitleSuffix.set("(Free)") }
        val b = newFlavor("dev").apply { desktopWindowTitleSuffix.set("(Dev)") }
        val fv = FlavorVariant("freeDev", listOf(a, b))
        assertEquals("(Free) (Dev)", fv.combinedDesktopTitleSuffix)
    }

    @Test
    fun `combinedWebTitleSuffix joins with spaces`() {
        val a = newFlavor("free").apply { webTitleSuffix.set("[Free]") }
        val b = newFlavor("dev").apply { webTitleSuffix.set("[Dev]") }
        val fv = FlavorVariant("freeDev", listOf(a, b))
        assertEquals("[Free] [Dev]", fv.combinedWebTitleSuffix)
    }

    @Test
    fun `combined suffixes are empty strings when no flavor has them set`() {
        val a = newFlavor("free")
        val fv = FlavorVariant("free", listOf(a))
        assertEquals("", fv.combinedApplicationIdSuffix)
        assertEquals("", fv.combinedBundleIdSuffix)
        assertEquals("", fv.combinedDesktopTitleSuffix)
        assertEquals("", fv.combinedWebTitleSuffix)
        assertEquals("", fv.combinedVersionNameSuffix)
    }

    @Test
    fun `flavorNames is derived from flavors list`() {
        val a = newFlavor("free")
        val b = newFlavor("dev")
        val c = newFlavor("phone")
        val fv = FlavorVariant("freeDevPhone", listOf(a, b, c))
        assertEquals(listOf("free", "dev", "phone"), fv.flavorNames)
    }

    @Test
    fun `allDependencies flattens flavor deps`() {
        val a = newFlavor("free").apply { dependency("implementation", "a:1") }
        val b = newFlavor("dev").apply {
            dependency("implementation", "b:1")
            dependency("api", "c:1")
        }
        val fv = FlavorVariant("freeDev", listOf(a, b))
        assertEquals(3, fv.allDependencies.size)
        assertTrue(fv.allDependencies.any { it.notation == "a:1" })
        assertTrue(fv.allDependencies.any { it.notation == "b:1" })
        assertTrue(fv.allDependencies.any { it.notation == "c:1" })
    }

    @Test
    fun `mergedMatchingFallbacks dedupes across flavors`() {
        val a = newFlavor("free").apply { matchingFallbacks("a", "b") }
        val b = newFlavor("dev").apply { matchingFallbacks("b", "c") }
        val fv = FlavorVariant("freeDev", listOf(a, b))
        assertEquals(listOf("a", "b", "c"), fv.mergedMatchingFallbacks)
    }

    @Test
    fun `getMatchingFallbacksForDimension returns for matching dimension`() {
        val a = newFlavor("free", "tier").apply { matchingFallbacks("internal") }
        val b = newFlavor("dev", "env").apply { matchingFallbacks("staging") }
        val fv = FlavorVariant("freeDev", listOf(a, b))
        assertEquals(listOf("internal"), fv.getMatchingFallbacksForDimension("tier"))
        assertEquals(listOf("staging"), fv.getMatchingFallbacksForDimension("env"))
        assertEquals(emptyList<String>(), fv.getMatchingFallbacksForDimension("unknown"))
    }

    @Test
    fun `buildType layer overrides flavor mergedBuildConfigFields`() {
        val proj = ProjectBuilder.builder().build()
        val flavor = newFlavor("free").apply { buildConfigField("Boolean", "FLAG", "true") }
        val bt = proj.objects.newInstance(BuildTypeConfig::class.java, "release").apply { buildConfigField("Boolean", "FLAG", "false") }
        val fv = FlavorVariant("freeRelease", listOf(flavor), buildType = bt)
        assertEquals("false", fv.mergedBuildConfigFields["FLAG"]!!.value)
    }

    @Test
    fun `combinedApplicationIdSuffix appends buildType suffix`() {
        val proj = ProjectBuilder.builder().build()
        val flavor = newFlavor("free").apply { applicationIdSuffix.set(".free") }
        val bt = proj.objects.newInstance(BuildTypeConfig::class.java, "debug").apply { applicationIdSuffix.set(".debug") }
        val fv = FlavorVariant("freeDebug", listOf(flavor), buildType = bt)
        assertEquals(".free.debug", fv.combinedApplicationIdSuffix)
    }
}
