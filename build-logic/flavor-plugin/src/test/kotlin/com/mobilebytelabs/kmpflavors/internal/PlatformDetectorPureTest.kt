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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-data coverage for the PlatformGroup data class. The PlatformDetector.detect
 * + wireIntermediateSourceSets methods require a real KotlinMultiplatformExtension
 * — full coverage lives in Tier D's TestKit fixture.
 */
class PlatformDetectorPureTest {

    @Test
    fun `PlatformGroup data class equality and copy`() {
        val a = PlatformGroup("ios", "iosMain", parent = "native")
        val b = PlatformGroup("ios", "iosMain", parent = "native")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        val c = a.copy(isIntermediate = true)
        assertTrue(c.isIntermediate)
    }

    @Test
    fun `PlatformGroup defaults`() {
        val pg = PlatformGroup("desktop", "desktopMain")
        assertEquals("desktop", pg.prefix)
        assertEquals("desktopMain", pg.mainSourceSet)
        assertEquals(null, pg.parent)
        assertFalse(pg.isIntermediate)
    }
}
