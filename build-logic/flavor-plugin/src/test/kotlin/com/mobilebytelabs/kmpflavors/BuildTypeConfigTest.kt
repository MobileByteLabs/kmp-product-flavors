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

class BuildTypeConfigTest {

    private fun newBuildType(name: String): BuildTypeConfig {
        val project = ProjectBuilder.builder().build()
        return project.objects.newInstance(BuildTypeConfig::class.java, name)
    }

    @Test
    fun `getName returns the constructor name`() {
        val bt = newBuildType("custom")
        assertEquals("custom", bt.name)
    }

    @Test
    fun `isDefault convention is false`() {
        assertFalse(newBuildType("debug").isDefault.get())
    }

    @Test
    fun `isDebuggable defaults to true for debug name`() {
        assertTrue(newBuildType("debug").isDebuggable.get())
        assertTrue(newBuildType("DEBUG").isDebuggable.get())
    }

    @Test
    fun `isDebuggable defaults to false for non-debug names`() {
        assertFalse(newBuildType("release").isDebuggable.get())
        assertFalse(newBuildType("staging").isDebuggable.get())
    }

    @Test
    fun `isMinifyEnabled defaults to true for release name only`() {
        assertTrue(newBuildType("release").isMinifyEnabled.get())
        assertTrue(newBuildType("RELEASE").isMinifyEnabled.get())
        assertFalse(newBuildType("debug").isMinifyEnabled.get())
        assertFalse(newBuildType("staging").isMinifyEnabled.get())
    }

    @Test
    fun `buildConfigField adds entry to map`() {
        val bt = newBuildType("debug")
        bt.buildConfigField("Boolean", "DEBUG_MODE", "true")
        val map = bt.buildConfigFields.get()
        assertEquals(1, map.size)
        val field = map["DEBUG_MODE"]!!
        assertEquals("Boolean", field.type)
        assertEquals("DEBUG_MODE", field.name)
        assertEquals("true", field.value)
    }

    @Test
    fun `multiple buildConfigFields accumulate`() {
        val bt = newBuildType("debug")
        bt.buildConfigField("Boolean", "DEBUG", "true")
        bt.buildConfigField("String", "TAG", "\"DEBUG\"")
        val map = bt.buildConfigFields.get()
        assertEquals(setOf("DEBUG", "TAG"), map.keys)
    }

    @Test
    fun `applicationIdSuffix bundleIdSuffix versionNameSuffix start unset`() {
        val bt = newBuildType("debug")
        assertNull(bt.applicationIdSuffix.orNull)
        assertNull(bt.bundleIdSuffix.orNull)
        assertNull(bt.versionNameSuffix.orNull)
    }

    @Test
    fun `dependency adds entry to buildTypeDependencies`() {
        val bt = newBuildType("debug")
        bt.dependency("implementation", "com.example:lib:1.0")
        val deps = bt.buildTypeDependencies.get()
        assertEquals(1, deps.size)
        assertEquals("implementation", deps[0].configuration)
        assertEquals("com.example:lib:1.0", deps[0].notation)
    }

    @Test
    fun `matchingFallbacks vararg accumulates`() {
        val bt = newBuildType("debug")
        bt.matchingFallbacks("release", "internal")
        bt.matchingFallbacks("test")
        assertEquals(listOf("release", "internal", "test"), bt.matchingFallbacks.get())
    }

    @Test
    fun `signingConfig is unset by default`() {
        assertNull(newBuildType("debug").signingConfig.orNull)
    }
}
