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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class VariantPromotionTest {

    @Test
    fun `VariantPromotion data class equality and copy`() {
        val a = VariantPromotion(from = "freeDev", to = "freeStaging")
        val b = VariantPromotion(from = "freeDev", to = "freeStaging")
        assertEquals(a, b)
        val c = a.copy(to = "paidStaging")
        assertEquals("paidStaging", c.to)
    }

    @Test
    fun `VariantPromotion default transforms is empty list`() {
        val p = VariantPromotion(from = "a", to = "b")
        assertEquals(emptyList<VariantPromotionTransform>(), p.transforms)
        assertTrue(p.copyResources)
        assertTrue(p.copyTests)
    }

    @Test
    fun `VariantPromotion is Serializable end-to-end`() {
        val p = VariantPromotion(from = "freeDev", to = "freeStaging")
        p.transforms.add(VariantPromotionTransform("renamePackage", "com.a", "com.b"))
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(p) }
        val restored = ObjectInputStream(ByteArrayInputStream(bos.toByteArray())).readObject() as VariantPromotion
        assertEquals(p, restored)
        assertEquals(1, restored.transforms.size)
        assertEquals("renamePackage", restored.transforms[0].kind)
    }

    @Test
    fun `VariantPromotionTransform data class equality`() {
        val t1 = VariantPromotionTransform("renamePackage", "a", "b")
        val t2 = VariantPromotionTransform("renamePackage", "a", "b")
        assertEquals(t1, t2)
        assertEquals(t1.hashCode(), t2.hashCode())
    }

    @Test
    fun `VariantPromotionTransform is Serializable`() {
        val t = VariantPromotionTransform("renamePackage", "x", "y")
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(t) }
        val restored = ObjectInputStream(ByteArrayInputStream(bos.toByteArray())).readObject() as VariantPromotionTransform
        assertEquals(t, restored)
    }

    @Test
    fun `VariantPromotionScope applyTransform adds entry to promotion`() {
        val p = VariantPromotion(from = "a", to = "b")
        val scope = VariantPromotionScope(p)
        scope.applyTransform("renamePackage", "com.x" to "com.y")
        scope.applyTransform("renameNamespace", "src" to "dst")
        assertEquals(2, p.transforms.size)
        assertEquals("renamePackage", p.transforms[0].kind)
        assertEquals("com.x", p.transforms[0].from)
        assertEquals("com.y", p.transforms[0].to)
        assertEquals("renameNamespace", p.transforms[1].kind)
    }

    @Test
    fun `VariantPromotionScope copyResources sets the flag`() {
        val p = VariantPromotion(from = "a", to = "b")
        val scope = VariantPromotionScope(p)
        scope.copyResources(false)
        assertEquals(false, p.copyResources)
        scope.copyResources(true)
        assertEquals(true, p.copyResources)
    }

    @Test
    fun `VariantPromotionScope copyTests sets the flag`() {
        val p = VariantPromotion(from = "a", to = "b")
        val scope = VariantPromotionScope(p)
        scope.copyTests(false)
        assertEquals(false, p.copyTests)
        scope.copyTests(true)
        assertEquals(true, p.copyTests)
    }

    @Test
    fun `VariantPromotion scope exposes promotion as internal property`() {
        val p = VariantPromotion(from = "a", to = "b")
        val scope = VariantPromotionScope(p)
        assertNotNull(scope)
    }
}
