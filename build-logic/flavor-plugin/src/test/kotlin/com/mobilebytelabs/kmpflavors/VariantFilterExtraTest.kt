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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the v2.0+ branches not exercised by the existing
 * resolver-driven VariantFilterTest: exclude / setIgnore / hasBuildType
 * (with non-null buildType) / excludeTargets / availableTargets.
 */
class VariantFilterExtraTest {

    @Test
    fun `exclude flips isExcluded to true`() {
        val vf = VariantFilter("freeDev", listOf("free", "dev"), emptyList())
        assertFalse(vf.isExcluded())
        vf.exclude()
        assertTrue(vf.isExcluded())
    }

    @Test
    fun `setIgnore toggles exclusion both directions`() {
        val vf = VariantFilter("freeDev", listOf("free"), emptyList())
        vf.setIgnore(true)
        assertTrue(vf.isExcluded())
        vf.setIgnore(false)
        assertFalse(vf.isExcluded())
    }

    @Test
    fun `hasBuildType matches when name equals`() {
        val vf = VariantFilter("freeDevDebug", listOf("free", "dev"), emptyList(), buildType = "debug")
        assertTrue(vf.hasBuildType("debug"))
        assertFalse(vf.hasBuildType("release"))
    }

    @Test
    fun `hasBuildType returns false when buildType is null`() {
        val vf = VariantFilter("freeDev", listOf("free", "dev"), emptyList())
        assertFalse(vf.hasBuildType("debug"))
    }

    @Test
    fun `excludeTargets rejects empty vararg`() {
        val vf = VariantFilter("freeDev", listOf("free"), emptyList())
        assertThrows(IllegalArgumentException::class.java) { vf.excludeTargets() }
    }

    @Test
    fun `excludeTargets stores names and isTargetExcluded reflects them`() {
        val vf = VariantFilter("freeDev", listOf("free"), emptyList(), availableTargets = setOf("desktop", "iosArm64"))
        vf.excludeTargets("desktop")
        assertTrue(vf.isTargetExcluded("desktop"))
        assertFalse(vf.isTargetExcluded("iosArm64"))
    }

    @Test
    fun `excludedTargetsSnapshot returns immutable copy`() {
        val vf = VariantFilter("freeDev", listOf("free"), emptyList())
        vf.excludeTargets("a", "b")
        val snap1 = vf.excludedTargetsSnapshot()
        vf.excludeTargets("c")
        val snap2 = vf.excludedTargetsSnapshot()
        assertEquals(setOf("a", "b"), snap1)
        assertEquals(setOf("a", "b", "c"), snap2)
    }

    @Test
    fun `availableTargets is exposed as the constructor arg`() {
        val vf = VariantFilter("v", emptyList(), emptyList(), availableTargets = setOf("desktop", "jvm"))
        assertEquals(setOf("desktop", "jvm"), vf.availableTargets)
    }

    @Test
    fun `availableTargets defaults to empty set`() {
        val vf = VariantFilter("v", emptyList(), emptyList())
        assertEquals(emptySet<String>(), vf.availableTargets)
    }
}
