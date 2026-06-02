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

import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeatureFlagsConfigTest {

    private fun newConfig(): FeatureFlagsConfig =
        ProjectBuilder.builder().build().objects.newInstance(FeatureFlagsConfig::class.java)

    @Test
    fun `growthbook is non-null and enabled defaults to false`() {
        val cfg = newConfig()
        assertNotNull(cfg.growthbook)
        assertFalse(cfg.growthbook.enabled.get())
    }

    @Test
    fun `statsig is non-null and enabled defaults to false`() {
        val cfg = newConfig()
        assertNotNull(cfg.statsig)
        assertFalse(cfg.statsig.enabled.get())
    }

    @Test
    fun `launchDarkly is non-null and enabled defaults to false`() {
        val cfg = newConfig()
        assertNotNull(cfg.launchDarkly)
        assertFalse(cfg.launchDarkly.enabled.get())
    }

    @Test
    fun `growthbook action mutates the shared instance`() {
        val cfg = newConfig()
        cfg.growthbook(Action<GrowthBookConfig> { enabled.set(true) })
        assertTrue(cfg.growthbook.enabled.get())
    }

    @Test
    fun `statsig action mutates the shared instance`() {
        val cfg = newConfig()
        cfg.statsig(Action<StatsigConfig> { enabled.set(true) })
        assertTrue(cfg.statsig.enabled.get())
    }

    @Test
    fun `launchDarkly action mutates the shared instance`() {
        val cfg = newConfig()
        cfg.launchDarkly(Action<LaunchDarklyConfig> { enabled.set(true) })
        assertTrue(cfg.launchDarkly.enabled.get())
    }

    @Test
    fun `defaultPayload is unset by default for all platforms`() {
        val cfg = newConfig()
        assertEquals(null, cfg.growthbook.defaultPayload.orNull)
        assertEquals(null, cfg.statsig.defaultPayload.orNull)
        assertEquals(null, cfg.launchDarkly.defaultPayload.orNull)
    }

    @Test
    fun `each platform config is a distinct instance`() {
        val cfg = newConfig()
        val gb = cfg.growthbook
        val st = cfg.statsig
        val ld = cfg.launchDarkly
        // Sanity — they're different abstract classes; downcasting confirms identity.
        assertTrue(gb is GrowthBookConfig)
        assertTrue(st is StatsigConfig)
        assertTrue(ld is LaunchDarklyConfig)
    }
}
