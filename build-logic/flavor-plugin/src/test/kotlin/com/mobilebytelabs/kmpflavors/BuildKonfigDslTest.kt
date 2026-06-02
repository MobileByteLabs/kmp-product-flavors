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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildKonfigDslTest {

    private fun newDsl(): BuildKonfigDsl =
        BuildKonfigDsl::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

    @Test
    fun `secret registration trims and stores ids`() {
        val dsl = newDsl()
        dsl.secret("api-key")
        dsl.secret("auth.token")
        assertEquals(listOf("api-key", "auth.token"), dsl.secrets.toList())
    }

    @Test
    fun `secret rejects blank id`() {
        val dsl = newDsl()
        assertThrows(IllegalArgumentException::class.java) { dsl.secret("") }
        assertThrows(IllegalArgumentException::class.java) { dsl.secret("   ") }
    }

    @Test
    fun `enum registers dimension by name`() {
        val dsl = newDsl()
        dsl.enum("tier")
        dsl.enum("env")
        assertEquals(listOf("tier", "env"), dsl.enums.toList())
    }

    @Test
    fun `customField registers declaration with explicit type descriptor`() {
        val dsl = newDsl()
        dsl.customField("debugConfig", "com.example.MyConfig", "com.example.MyConfig.Default")
        val emitted = dsl.customFields.single()
        assertEquals("debugConfig", emitted.name)
        assertEquals("com.example.MyConfig", emitted.typeDescriptor)
        assertEquals("com.example.MyConfig.Default", emitted.value)
    }

    @Test
    fun `customField rejects blank name`() {
        val dsl = newDsl()
        assertThrows(IllegalArgumentException::class.java) {
            dsl.customField("", "String", "\"x\"")
        }
    }

    @Test
    fun `perTarget groups fields by target name`() {
        val dsl = newDsl()
        dsl.perTarget("iosMain", Action<PerTargetScope> {
field("RUNTIME_FLAG", "Boolean", "true")
            field("API_TIER", "String", "\"premium\"")})
        dsl.perTarget("desktopMain", Action<PerTargetScope> {
field("ENABLE_DEBUG", "Boolean", "true")})

        val iosFields = dsl.perTargetBlocks["iosMain"].orEmpty()
        val desktopFields = dsl.perTargetBlocks["desktopMain"].orEmpty()
        assertEquals(2, iosFields.size)
        assertEquals(1, desktopFields.size)
        assertTrue(iosFields.any { it.name == "RUNTIME_FLAG" && it.targetName == "iosMain" })
        assertTrue(desktopFields.single().name == "ENABLE_DEBUG")
    }

    @Test
    fun `perTarget rejects blank target name`() {
        val dsl = newDsl()
        assertThrows(IllegalArgumentException::class.java) {
            dsl.perTarget("", Action<PerTargetScope> { /* no-op */ })
        }
    }

    @Test
    fun `network baseUrl rejects empty pairs vararg`() {
        val dsl = newDsl()
        assertThrows(IllegalArgumentException::class.java) {
            dsl.network.baseUrl()
        }
    }

    @Test
    fun `network baseUrl rejects blank flavor key`() {
        val dsl = newDsl()
        assertThrows(IllegalArgumentException::class.java) {
            dsl.network.baseUrl("" to "https://x")
        }
    }

    @Test
    fun `network baseUrl rejects blank URL value`() {
        val dsl = newDsl()
        assertThrows(IllegalArgumentException::class.java) {
            dsl.network.baseUrl("free" to "")
        }
    }

    @Test
    fun `network timeout rejects non-positive seconds`() {
        val dsl = newDsl()
        assertThrows(IllegalArgumentException::class.java) { dsl.network.timeout(0) }
        assertThrows(IllegalArgumentException::class.java) { dsl.network.timeout(-5) }
    }

    @Test
    fun `network DSL stores baseUrl map preserving insertion order`() {
        val dsl = newDsl()
        dsl.network(
            Action<NetworkDsl> {
baseUrl("free" to "https://a", "paid" to "https://b")
                timeout(45)},
        )
        assertEquals(linkedMapOf("free" to "https://a", "paid" to "https://b"), dsl.network.baseUrls)
        assertEquals(45, dsl.network.timeoutSeconds)
    }

    @Test
    fun `network DSL allows incremental baseUrl additions`() {
        val dsl = newDsl()
        dsl.network.baseUrl("free" to "https://a")
        dsl.network.baseUrl("paid" to "https://b")
        assertEquals(setOf("free", "paid"), dsl.network.baseUrls.keys)
    }
}
