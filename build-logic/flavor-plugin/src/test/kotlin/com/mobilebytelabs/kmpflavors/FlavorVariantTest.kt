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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlavorVariantTest {

    private val project = ProjectBuilder.builder().build()

    private fun flavor(name: String, fields: Map<String, BuildConfigField> = emptyMap(), extras: Map<String, String> = emptyMap()): FlavorConfig {
        val cfg = project.objects.newInstance(FlavorConfig::class.java, name)
        cfg.buildConfigFields.set(fields)
        cfg.extras.set(extras)
        return cfg
    }

    private fun buildType(name: String, fields: Map<String, BuildConfigField> = emptyMap()): BuildTypeConfig {
        val bt = project.objects.newInstance(BuildTypeConfig::class.java, name)
        bt.buildConfigFields.set(fields)
        return bt
    }

    @Test
    fun `flavorNames lists per-flavor names in declared order`() {
        val v = FlavorVariant(
            name = "freeDev",
            flavors = listOf(flavor("free"), flavor("dev")),
        )
        assertEquals(listOf("free", "dev"), v.flavorNames)
    }

    @Test
    fun `mergedBuildConfigFields preserves later flavor's value on key collision`() {
        val v = FlavorVariant(
            name = "freeDev",
            flavors = listOf(
                flavor("free", mapOf("MODE" to BuildConfigField("String", "MODE", "\"free\""))),
                flavor("dev", mapOf("MODE" to BuildConfigField("String", "MODE", "\"dev\""))),
            ),
        )
        assertEquals("\"dev\"", v.mergedBuildConfigFields["MODE"]?.value)
    }

    @Test
    fun `mergedBuildConfigFields applies buildType layer last so it wins collisions`() {
        val v = FlavorVariant(
            name = "freeDevDebug",
            flavors = listOf(flavor("free", mapOf("LOGGING" to BuildConfigField("Boolean", "LOGGING", "false")))),
            buildType = buildType("debug", mapOf("LOGGING" to BuildConfigField("Boolean", "LOGGING", "true"))),
        )
        assertEquals("true", v.mergedBuildConfigFields["LOGGING"]?.value)
    }

    @Test
    fun `mergedExtras concatenates flavor extras`() {
        val v = FlavorVariant(
            name = "freeDev",
            flavors = listOf(
                flavor("free", extras = mapOf("region" to "us")),
                flavor("dev", extras = mapOf("env" to "dev")),
            ),
        )
        assertEquals(mapOf("region" to "us", "env" to "dev"), v.mergedExtras)
    }

    @Test
    fun `mergedExtras key collision resolves in favor of later flavor`() {
        val v = FlavorVariant(
            name = "freeDev",
            flavors = listOf(
                flavor("free", extras = mapOf("k" to "free")),
                flavor("dev", extras = mapOf("k" to "dev")),
            ),
        )
        assertEquals("dev", v.mergedExtras["k"])
    }

    @Test
    fun `excludedTargets defaults to empty set`() {
        val v = FlavorVariant(name = "free", flavors = listOf(flavor("free")))
        assertTrue(v.excludedTargets.isEmpty())
    }

    @Test
    fun `buildType default is null`() {
        val v = FlavorVariant(name = "free", flavors = listOf(flavor("free")))
        assertNull(v.buildType)
    }

    @Test
    fun `copy preserves identity but allows excludedTargets mutation`() {
        val v = FlavorVariant(name = "free", flavors = listOf(flavor("free")))
        val updated = v.copy(excludedTargets = setOf("watchosArm64", "tvosArm64"))
        assertEquals(v.name, updated.name)
        assertEquals(v.flavors, updated.flavors)
        assertEquals(setOf("watchosArm64", "tvosArm64"), updated.excludedTargets)
    }
}
