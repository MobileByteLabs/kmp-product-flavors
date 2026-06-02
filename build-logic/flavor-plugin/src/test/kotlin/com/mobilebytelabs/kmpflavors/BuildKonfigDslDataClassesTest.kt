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

/**
 * Coverage for the BuildKonfigDsl data classes + PerTargetScope direct field
 * access — complements BuildKonfigDslTest (which exercises the DSL methods).
 */
class BuildKonfigDslDataClassesTest {

    @Test
    fun `CustomFieldDeclaration equality is structural`() {
        val a = CustomFieldDeclaration("x", "String", "\"v\"")
        val b = CustomFieldDeclaration("x", "String", "\"v\"")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CustomFieldDeclaration is Serializable`() {
        val c = CustomFieldDeclaration("scopes", "List<String>", "listOf(\"a\",\"b\")")
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(c) }
        val restored = ObjectInputStream(ByteArrayInputStream(bos.toByteArray())).readObject() as CustomFieldDeclaration
        assertEquals(c, restored)
    }

    @Test
    fun `PerTargetFieldDeclaration equality is structural`() {
        val a = PerTargetFieldDeclaration("KEY", "String", "\"v\"", "iosMain")
        val b = PerTargetFieldDeclaration("KEY", "String", "\"v\"", "iosMain")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `PerTargetFieldDeclaration distinguishes by targetName`() {
        val a = PerTargetFieldDeclaration("K", "Boolean", "true", "iosMain")
        val b = PerTargetFieldDeclaration("K", "Boolean", "true", "desktopMain")
        assertNotEquals(a, b)
    }

    @Test
    fun `PerTargetFieldDeclaration is Serializable`() {
        val p = PerTargetFieldDeclaration("X", "Int", "42", "iosMain")
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(p) }
        val restored = ObjectInputStream(ByteArrayInputStream(bos.toByteArray())).readObject() as PerTargetFieldDeclaration
        assertEquals(p, restored)
    }

    @Test
    fun `PerTargetScope targetName is exposed via public field`() {
        val scope = PerTargetScope("iosMain")
        assertEquals("iosMain", scope.targetName)
    }

    @Test
    fun `PerTargetScope field appends to fields with target name`() {
        val scope = PerTargetScope("iosMain")
        scope.field("BUNDLE", "String", "\"a\"")
        scope.field("FLAG", "Boolean", "true")
        assertEquals(2, scope.fields.size)
        assertTrue(scope.fields.all { it.targetName == "iosMain" })
        assertEquals("BUNDLE", scope.fields[0].name)
        assertEquals("FLAG", scope.fields[1].name)
    }

    @Test
    fun `PerTargetScope field rejects blank name`() {
        val scope = PerTargetScope("iosMain")
        val ex = runCatching { scope.field("", "String", "\"x\"") }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `Serializable types implement the marker interface`() {
        assertTrue(CustomFieldDeclaration("a", "b", "c") is java.io.Serializable)
        assertTrue(PerTargetFieldDeclaration("a", "b", "c", "d") is java.io.Serializable)
    }
}
