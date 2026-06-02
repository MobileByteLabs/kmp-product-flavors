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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class NetworkConfigSpecTest {

    @Test
    fun `default timeout is 30 seconds`() {
        val spec = NetworkConfigSpec(baseUrls = mapOf("free" to "https://x"))
        assertEquals(30, spec.timeoutSeconds)
    }

    @Test
    fun `data-class equality holds for identical content`() {
        val a = NetworkConfigSpec(mapOf("free" to "https://x", "paid" to "https://y"), 60)
        val b = NetworkConfigSpec(mapOf("free" to "https://x", "paid" to "https://y"), 60)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `data-class equality breaks on timeout drift`() {
        val a = NetworkConfigSpec(mapOf("free" to "https://x"), 30)
        val b = NetworkConfigSpec(mapOf("free" to "https://x"), 60)
        assertNotEquals(a, b)
    }

    @Test
    fun `data-class equality breaks on baseUrl drift`() {
        val a = NetworkConfigSpec(mapOf("free" to "https://x"), 30)
        val b = NetworkConfigSpec(mapOf("free" to "https://y"), 30)
        assertNotEquals(a, b)
    }

    @Test
    fun `serializable round-trip preserves state (config-cache contract)`() {
        val original = NetworkConfigSpec(
            baseUrls = linkedMapOf("free" to "https://api.free.example.com", "paid" to "https://api.paid.example.com"),
            timeoutSeconds = 45,
        )
        val bytes = ByteArrayOutputStream().also { os ->
            ObjectOutputStream(os).use { it.writeObject(original) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as NetworkConfigSpec }
        assertEquals(original, restored)
        assertEquals(original.baseUrls, restored.baseUrls)
        assertEquals(45, restored.timeoutSeconds)
    }

    @Test
    fun `empty baseUrls map is permitted (codegen-side checks govern emission)`() {
        val spec = NetworkConfigSpec(baseUrls = emptyMap())
        assertTrue(spec.baseUrls.isEmpty())
    }
}
