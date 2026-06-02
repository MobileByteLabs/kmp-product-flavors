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
import org.junit.jupiter.api.Test

class SpmConfigTest {

    private fun newConfig(): SpmConfig =
        ProjectBuilder.builder().build().objects.newInstance(SpmConfig::class.java)

    @Test
    fun `generateManifest convention is false`() {
        assertFalse(newConfig().generateManifest.get())
    }

    @Test
    fun `xcframeworkName convention is Shared`() {
        assertEquals("Shared", newConfig().xcframeworkName.get())
    }

    @Test
    fun `distribution convention is LOCAL`() {
        assertEquals(SpmDistribution.LOCAL, newConfig().distribution.get())
    }

    @Test
    fun `checksumStrategy convention is AUTO`() {
        assertEquals(SpmChecksumStrategy.AUTO, newConfig().checksumStrategy.get())
    }

    @Test
    fun `binaryUrlTemplate is unset by default`() {
        assertEquals(null, newConfig().binaryUrlTemplate.orNull)
    }

    @Test
    fun `xcframeworkPath is unset by default`() {
        assertEquals(null, newConfig().xcframeworkPath.orNull)
    }

    @Test
    fun `properties can be assigned`() {
        val cfg = newConfig()
        cfg.generateManifest.set(true)
        cfg.xcframeworkName.set("MyFW")
        cfg.distribution.set(SpmDistribution.REMOTE)
        cfg.binaryUrlTemplate.set("https://cdn.example.com/{flavor}/{version}/MyFW.xcframework.zip")
        cfg.xcframeworkPath.set("XCFrameworks/free/MyFW.xcframework")
        cfg.checksumStrategy.set(SpmChecksumStrategy.REQUIRE_FILE)

        assertEquals(true, cfg.generateManifest.get())
        assertEquals("MyFW", cfg.xcframeworkName.get())
        assertEquals(SpmDistribution.REMOTE, cfg.distribution.get())
        assertEquals("https://cdn.example.com/{flavor}/{version}/MyFW.xcframework.zip", cfg.binaryUrlTemplate.get())
        assertEquals("XCFrameworks/free/MyFW.xcframework", cfg.xcframeworkPath.get())
        assertEquals(SpmChecksumStrategy.REQUIRE_FILE, cfg.checksumStrategy.get())
    }

    @Test
    fun `SpmDistribution enum has expected values`() {
        assertEquals(2, SpmDistribution.values().size)
        assertEquals(SpmDistribution.LOCAL, SpmDistribution.valueOf("LOCAL"))
        assertEquals(SpmDistribution.REMOTE, SpmDistribution.valueOf("REMOTE"))
    }

    @Test
    fun `SpmChecksumStrategy enum has expected values`() {
        assertEquals(3, SpmChecksumStrategy.values().size)
        assertEquals(SpmChecksumStrategy.AUTO, SpmChecksumStrategy.valueOf("AUTO"))
        assertEquals(SpmChecksumStrategy.REQUIRE_FILE, SpmChecksumStrategy.valueOf("REQUIRE_FILE"))
        assertEquals(SpmChecksumStrategy.SKIP, SpmChecksumStrategy.valueOf("SKIP"))
    }
}
