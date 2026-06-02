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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KmpFlavorVariantTest {

    private fun newVariant(name: String = "freeDev"): KmpFlavorVariant =
        ProjectBuilder.builder().build().objects.newInstance(KmpFlavorVariant::class.java, name)

    @Test
    fun `getName returns the constructor argument`() {
        assertEquals("freeDev", newVariant("freeDev").name)
    }

    @Test
    fun `dependencies scope is non-null and starts empty`() {
        val v = newVariant()
        assertNotNull(v.dependencies)
        assertTrue(v.dependencies.excludes.isEmpty())
    }

    @Test
    fun `dependencies action mutates the shared scope`() {
        val v = newVariant()
        v.dependencies(Action<VariantDependenciesScope> { exclude("com.example", "pkg") })
        assertEquals(1, v.dependencies.excludes.size)
        assertEquals("com.example", v.dependencies.excludes[0].group)
    }

    @Test
    fun `flavors defaults to empty list`() {
        assertEquals(emptyList<String>(), newVariant().flavors)
    }

    @Test
    fun `buildType defaults to null`() {
        assertEquals(null, newVariant().buildType)
    }

    @Test
    fun `targets and compilations default to empty`() {
        val v = newVariant()
        assertEquals(emptySet<Any>(), v.targets)
        assertEquals(emptyMap<Any, Any>(), v.compilations)
        assertEquals(emptyList<Any>(), v.intermediateSourceSets)
    }

    @Test
    fun `internal setters are exercised`() {
        val v = newVariant()
        v.flavors = listOf("free", "dev")
        v.buildType = "debug"
        v.targets = emptySet()
        v.compilations = emptyMap()
        v.intermediateSourceSets = emptyList()
        assertEquals(listOf("free", "dev"), v.flavors)
        assertEquals("debug", v.buildType)
    }
}
