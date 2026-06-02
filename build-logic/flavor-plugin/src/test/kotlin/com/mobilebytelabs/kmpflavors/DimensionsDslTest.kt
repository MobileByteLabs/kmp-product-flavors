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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DimensionsDslTest {

    private fun newExtension(): KmpFlavorExtension =
        ProjectBuilder.builder().build().objects.newInstance(KmpFlavorExtension::class.java)

    @Test
    fun `dimension registers the dimension name`() {
        val ext = newExtension()
        val dsl = DimensionsDsl(ext)
        dsl.dimension("tier")
        assertTrue(ext.flavorDimensions.names.contains("tier"))
    }

    @Test
    fun `dimension marks dimensionsDslUsed = true`() {
        val ext = newExtension()
        assertEquals(false, ext.dimensionsDslUsed)
        DimensionsDsl(ext).dimension("tier")
        assertEquals(true, ext.dimensionsDslUsed)
    }

    @Test
    fun `dimension scope flavor registers flavor and wires dimension`() {
        val ext = newExtension()
        DimensionsDsl(ext).dimension(
            "tier",
            Action<DimensionScope> {
                flavor("free")
                flavor("paid")
            },
        )
        val free = ext.flavors.getByName("free")
        val paid = ext.flavors.getByName("paid")
        assertEquals("tier", free.dimension.get())
        assertEquals("tier", paid.dimension.get())
    }

    @Test
    fun `getDimensionName returns the scope name`() {
        val ext = newExtension()
        var captured: String? = null
        DimensionsDsl(ext).dimension("env", Action<DimensionScope> { captured = getDimensionName() })
        assertEquals("env", captured)
    }

    @Test
    fun `flavor block configures the registered flavor`() {
        val ext = newExtension()
        DimensionsDsl(ext).dimension(
            "tier",
            Action<DimensionScope> {
                flavor(
                    "free",
                    Action<FlavorConfig> {
                        isDefault.set(true)
                        buildConfigField("Boolean", "PREMIUM", "false")
                    },
                )
            },
        )
        val free = ext.flavors.getByName("free")
        assertEquals(true, free.isDefault.get())
        assertEquals("false", free.buildConfigFields.get()["PREMIUM"]!!.value)
    }

    @Test
    fun `dimension can be called multiple times for distinct names`() {
        val ext = newExtension()
        val dsl = DimensionsDsl(ext)
        dsl.dimension("tier") { flavor("free") }
        dsl.dimension("env") { flavor("dev") }
        assertTrue(ext.flavorDimensions.names.containsAll(listOf("tier", "env")))
        assertTrue(ext.flavors.names.containsAll(listOf("free", "dev")))
    }

    @Test
    fun `extension dimensions function delegates to DimensionsDsl`() {
        val ext = newExtension()
        ext.dimensions {
            dimension("tier") {
                flavor("free")
                flavor("paid")
            }
        }
        assertTrue(ext.flavorDimensions.names.contains("tier"))
        assertTrue(ext.flavors.names.containsAll(listOf("free", "paid")))
        assertEquals(true, ext.dimensionsDslUsed)
    }
}
