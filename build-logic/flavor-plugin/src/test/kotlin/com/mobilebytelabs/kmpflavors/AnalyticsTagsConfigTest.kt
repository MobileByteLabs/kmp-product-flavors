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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalyticsTagsConfigTest {

    private fun newConfig(): AnalyticsTagsConfig =
        ProjectBuilder.builder().build().objects.newInstance(AnalyticsTagsConfig::class.java)

    @Test
    fun `enabled convention is false`() {
        assertFalse(newConfig().enabled.get())
    }

    @Test
    fun `platforms convention is single-element common`() {
        assertEquals(listOf("common"), newConfig().platforms.get())
    }

    @Test
    fun `customTag registers resolver under given key`() {
        val cfg = newConfig()
        cfg.customTag("environment") { variant -> variant.flavors.firstOrNull()?.name ?: "default" }
        assertTrue(cfg.customTags.containsKey("environment"))
    }

    @Test
    fun `customTag rejects blank name`() {
        val cfg = newConfig()
        assertThrows(IllegalArgumentException::class.java) {
            cfg.customTag("") { _ -> "x" }
        }
        assertThrows(IllegalArgumentException::class.java) {
            cfg.customTag("   ") { _ -> "x" }
        }
    }

    @Test
    fun `customTag preserves insertion order`() {
        val cfg = newConfig()
        cfg.customTag("environment") { _ -> "dev" }
        cfg.customTag("tier") { _ -> "free" }
        cfg.customTag("form") { _ -> "phone" }
        assertEquals(listOf("environment", "tier", "form"), cfg.customTags.keys.toList())
    }

    @Test
    fun `customTag overwrites existing key when re-registered`() {
        val cfg = newConfig()
        cfg.customTag("tier") { _ -> "free" }
        cfg.customTag("tier") { _ -> "paid" }
        val variant = FlavorVariant(name = "freeDev", flavors = emptyList())
        assertEquals("paid", cfg.customTags.getValue("tier").invoke(variant))
    }

    @Test
    fun `resolver receives the FlavorVariant unchanged`() {
        val cfg = newConfig()
        cfg.customTag("variantName") { variant -> variant.name }
        val v = FlavorVariant(name = "freeDevPhone", flavors = emptyList())
        assertEquals("freeDevPhone", cfg.customTags.getValue("variantName").invoke(v))
    }
}
