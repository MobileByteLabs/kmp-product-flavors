/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.BuildTypeConfig
import com.mobilebytelabs.kmpflavors.FlavorConfig
import com.mobilebytelabs.kmpflavors.FlavorDimension
import com.mobilebytelabs.kmpflavors.FlavorVariant
import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlavorVariantResolverExtraTest {

    private fun project() = ProjectBuilder.builder().build()
    private fun newFlavor(name: String, dim: String? = null, isDefault: Boolean = false): FlavorConfig {
        val f = project().objects.newInstance(FlavorConfig::class.java, name)
        if (dim != null) f.dimension.set(dim)
        f.isDefault.set(isDefault)
        return f
    }
    private fun newDim(name: String, priority: Int = 0): FlavorDimension {
        val d = project().objects.newInstance(FlavorDimension::class.java, name)
        d.priority.set(priority)
        return d
    }
    private fun newBuildType(name: String, isDefault: Boolean = false): BuildTypeConfig {
        val bt = project().objects.newInstance(BuildTypeConfig::class.java, name)
        bt.isDefault.set(isDefault)
        return bt
    }

    @Test
    fun `empty flavors returns empty list`() {
        val res = FlavorVariantResolver.resolveAllVariants(emptyList(), emptyList())
        assertEquals(emptyList<FlavorVariant>(), res)
    }

    @Test
    fun `no dimensions yields one variant per flavor`() {
        val res = FlavorVariantResolver.resolveAllVariants(
            dimensions = emptyList(),
            flavors = listOf(newFlavor("free"), newFlavor("paid")),
        )
        assertEquals(listOf("free", "paid"), res.map { it.name })
    }

    @Test
    fun `dimensions cartesian product produces expected variant names`() {
        val res = FlavorVariantResolver.resolveAllVariants(
            dimensions = listOf(newDim("tier", priority = 0), newDim("env", priority = 1)),
            flavors = listOf(
                newFlavor("free", "tier"),
                newFlavor("paid", "tier"),
                newFlavor("dev", "env"),
                newFlavor("prod", "env"),
            ),
        )
        assertEquals(setOf("freeDev", "freeProd", "paidDev", "paidProd"), res.map { it.name }.toSet())
    }

    @Test
    fun `dimension with no flavors yields empty list (V03 path)`() {
        val res = FlavorVariantResolver.resolveAllVariants(
            dimensions = listOf(newDim("tier"), newDim("emptyDim")),
            flavors = listOf(newFlavor("free", "tier")),
        )
        assertEquals(emptyList<FlavorVariant>(), res)
    }

    @Test
    fun `enableBuildTypes expands the matrix`() {
        val res = FlavorVariantResolver.resolveAllVariants(
            dimensions = listOf(newDim("tier")),
            flavors = listOf(newFlavor("free", "tier")),
            buildTypes = listOf(newBuildType("debug"), newBuildType("release")),
            enableBuildTypes = true,
        )
        assertEquals(setOf("freeDebug", "freeRelease"), res.map { it.name }.toSet())
    }

    @Test
    fun `enableBuildTypes false leaves matrix flavor-only`() {
        val res = FlavorVariantResolver.resolveAllVariants(
            dimensions = listOf(newDim("tier")),
            flavors = listOf(newFlavor("free", "tier")),
            buildTypes = listOf(newBuildType("debug")),
            enableBuildTypes = false,
        )
        assertEquals(setOf("free"), res.map { it.name }.toSet())
    }

    @Test
    fun `excludeTargets surfaces on FlavorVariant via filter`() {
        val res = FlavorVariantResolver.resolveAllVariants(
            dimensions = emptyList(),
            flavors = listOf(newFlavor("free"), newFlavor("paid")),
            variantFilters = listOf(
                Action {
                    if (variantName == "free") excludeTargets("watchosArm64")
                },
            ),
            availableTargets = setOf("watchosArm64", "iosArm64"),
        )
        val freeVariant = res.first { it.name == "free" }
        assertTrue("watchosArm64" in freeVariant.excludedTargets)
        val paidVariant = res.first { it.name == "paid" }
        assertEquals(emptySet<String>(), paidVariant.excludedTargets)
    }

    @Test
    fun `resolveDefaultVariant picks isDefault flavor`() {
        val res = FlavorVariantResolver.resolveDefaultVariant(
            dimensions = emptyList(),
            flavors = listOf(newFlavor("free"), newFlavor("paid", isDefault = true)),
        )
        assertEquals("paid", res?.name)
    }

    @Test
    fun `resolveDefaultVariant falls back to first flavor when no isDefault`() {
        val res = FlavorVariantResolver.resolveDefaultVariant(
            dimensions = emptyList(),
            flavors = listOf(newFlavor("free"), newFlavor("paid")),
        )
        assertEquals("free", res?.name)
    }

    @Test
    fun `resolveDefaultVariant with dimensions picks default per dimension`() {
        val res = FlavorVariantResolver.resolveDefaultVariant(
            dimensions = listOf(newDim("tier"), newDim("env")),
            flavors = listOf(
                newFlavor("free", "tier"),
                newFlavor("paid", "tier", isDefault = true),
                newFlavor("dev", "env", isDefault = true),
                newFlavor("prod", "env"),
            ),
        )
        assertEquals("paidDev", res?.name)
    }

    @Test
    fun `resolveDefaultVariant returns null when flavors empty`() {
        assertNull(FlavorVariantResolver.resolveDefaultVariant(emptyList(), emptyList()))
    }

    @Test
    fun `resolveDefaultVariant returns null when a dimension has no flavor`() {
        val res = FlavorVariantResolver.resolveDefaultVariant(
            dimensions = listOf(newDim("tier"), newDim("env")),
            flavors = listOf(newFlavor("free", "tier")),
        )
        assertNull(res)
    }

    @Test
    fun `resolveDefaultVariant appends default buildType when enabled`() {
        val res = FlavorVariantResolver.resolveDefaultVariant(
            dimensions = emptyList(),
            flavors = listOf(newFlavor("free")),
            buildTypes = listOf(newBuildType("debug", isDefault = true), newBuildType("release")),
            enableBuildTypes = true,
        )
        assertEquals("freeDebug", res?.name)
    }

    @Test
    fun `resolveDefaultVariant falls back to first buildType when no isDefault`() {
        val res = FlavorVariantResolver.resolveDefaultVariant(
            dimensions = emptyList(),
            flavors = listOf(newFlavor("free")),
            buildTypes = listOf(newBuildType("debug"), newBuildType("release")),
            enableBuildTypes = true,
        )
        assertEquals("freeDebug", res?.name)
    }

    @Test
    fun `resolveVariantByName is case-insensitive`() {
        val variants = listOf(FlavorVariant("freeDev", emptyList()), FlavorVariant("paidProd", emptyList()))
        assertEquals("freeDev", FlavorVariantResolver.resolveVariantByName("FREEDEV", variants)?.name)
        assertNotNull(FlavorVariantResolver.resolveVariantByName("freeDev", variants))
        assertNull(FlavorVariantResolver.resolveVariantByName("nonexistent", variants))
    }

    @Test
    fun `cartesianProduct singleton variant for single flavor list`() {
        val res = FlavorVariantResolver.resolveAllVariants(
            dimensions = listOf(newDim("tier")),
            flavors = listOf(newFlavor("free", "tier")),
        )
        assertEquals(listOf("free"), res.map { it.name })
    }
}
