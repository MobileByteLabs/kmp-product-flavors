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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlavorConfigAndDimensionTest {

    private fun project() = ProjectBuilder.builder().build()

    private fun newFlavor(name: String): FlavorConfig =
        project().objects.newInstance(FlavorConfig::class.java, name)

    private fun newDimension(name: String): FlavorDimension =
        project().objects.newInstance(FlavorDimension::class.java, name)

    @Test
    fun `FlavorConfig getName matches constructor arg`() {
        assertEquals("free", newFlavor("free").name)
    }

    @Test
    fun `FlavorConfig isDefault defaults to false`() {
        assertFalse(newFlavor("free").isDefault.get())
    }

    @Test
    fun `FlavorConfig optional properties start unset`() {
        val f = newFlavor("free")
        assertNull(f.dimension.orNull)
        assertNull(f.applicationIdSuffix.orNull)
        assertNull(f.bundleIdSuffix.orNull)
        assertNull(f.desktopWindowTitleSuffix.orNull)
        assertNull(f.webTitleSuffix.orNull)
        assertNull(f.versionNameSuffix.orNull)
        assertNull(f.signingConfig.orNull)
    }

    @Test
    fun `FlavorConfig buildConfigField adds entry`() {
        val f = newFlavor("free")
        f.buildConfigField("Boolean", "PREMIUM", "false")
        val field = f.buildConfigFields.get()["PREMIUM"]!!
        assertEquals("Boolean", field.type)
        assertEquals("PREMIUM", field.name)
        assertEquals("false", field.value)
    }

    @Test
    fun `FlavorConfig dependency adds to flavorDependencies`() {
        val f = newFlavor("free")
        f.dependency("implementation", "com.example:lib:1.0")
        val deps = f.flavorDependencies.get()
        assertEquals(1, deps.size)
        assertEquals("implementation", deps[0].configuration)
        assertEquals("com.example:lib:1.0", deps[0].notation)
    }

    @Test
    fun `FlavorConfig matchingFallbacks accumulates`() {
        val f = newFlavor("paid")
        f.matchingFallbacks("free")
        f.matchingFallbacks("internal", "alpha")
        assertEquals(listOf("free", "internal", "alpha"), f.matchingFallbacks.get())
    }

    @Test
    fun `FlavorConfig extras can be set via map property`() {
        val f = newFlavor("free")
        f.extras.put("key", "value")
        f.extras.put("other", "v2")
        assertEquals(mapOf("key" to "value", "other" to "v2"), f.extras.get())
    }

    @Test
    fun `BuildConfigField equality is structural`() {
        val a = BuildConfigField("String", "URL", "\"x\"")
        val b = BuildConfigField("String", "URL", "\"x\"")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `FlavorDependency equality is structural`() {
        val a = FlavorDependency("implementation", "x:1")
        val b = FlavorDependency("implementation", "x:1")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `FlavorDimension getName matches constructor arg`() {
        assertEquals("tier", newDimension("tier").name)
    }

    @Test
    fun `FlavorDimension priority convention is zero`() {
        assertEquals(0, newDimension("tier").priority.get())
    }

    @Test
    fun `FlavorDimension priority can be set`() {
        val d = newDimension("env")
        d.priority.set(5)
        assertEquals(5, d.priority.get())
    }

    @Test
    fun `BuildConfigField is Serializable via constants`() {
        // Confirm Serializable interface inheritance.
        assertTrue(BuildConfigField("String", "X", "\"y\"") is java.io.Serializable)
    }

    @Test
    fun `FlavorDependency is Serializable via constants`() {
        assertTrue(FlavorDependency("impl", "x:1") is java.io.Serializable)
    }
}
