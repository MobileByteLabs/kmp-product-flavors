/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.tasks

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidateFlavorsTaskTest {

    private fun newTask(): ValidateFlavorsTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.register("validateFlavors", ValidateFlavorsTask::class.java).get()
    }

    private fun ValidateFlavorsTask.preset(
        dims: Set<String> = setOf("tier"),
        flavorDims: Map<String, String> = mapOf("free" to "tier", "paid" to "tier"),
        flavorDefaults: Map<String, Boolean> = mapOf("free" to true, "paid" to false),
        variants: Set<String> = setOf("free", "paid"),
        active: String? = "free",
        all: List<String> = listOf("free", "paid"),
    ) {
        dimensionNames.set(dims)
        this.flavorDimensions.set(flavorDims)
        this.flavorDefaults.set(flavorDefaults)
        validVariantNames.set(variants)
        activeVariantName.set(active)
        allFlavorNames.set(all)
    }

    @Test
    fun `valid configuration passes`() {
        val task = newTask()
        task.preset()
        task.validate()  // no throw
    }

    @Test
    fun `duplicate flavor names throw`() {
        val task = newTask()
        task.preset(all = listOf("free", "free", "paid"))
        val ex = assertThrows(GradleException::class.java) { task.validate() }
        assertTrue(ex.message!!.contains("Duplicate flavor names"))
        assertTrue(ex.message!!.contains("free"))
    }

    @Test
    fun `invalid Kotlin identifier throws`() {
        val task = newTask()
        task.preset(
            flavorDims = mapOf("free" to "tier", "1invalid" to "tier"),
            all = listOf("free", "1invalid"),
        )
        val ex = assertThrows(GradleException::class.java) { task.validate() }
        assertTrue(ex.message!!.contains("Invalid flavor names"))
    }

    @Test
    fun `flavor with no dimension throws when dimensions defined`() {
        val task = newTask()
        task.preset(flavorDims = mapOf("free" to ""))
        val ex = assertThrows(GradleException::class.java) { task.validate() }
        assertTrue(ex.message!!.contains("has no dimension"))
    }

    @Test
    fun `flavor referencing unknown dimension throws`() {
        val task = newTask()
        task.preset(flavorDims = mapOf("free" to "unknown"), all = listOf("free"))
        val ex = assertThrows(GradleException::class.java) { task.validate() }
        assertTrue(ex.message!!.contains("references unknown dimension 'unknown'"))
    }

    @Test
    fun `dimension with no flavors throws`() {
        val task = newTask()
        task.preset(
            dims = setOf("tier", "ghostDim"),
            flavorDims = mapOf("free" to "tier", "paid" to "tier"),
        )
        val ex = assertThrows(GradleException::class.java) { task.validate() }
        assertTrue(ex.message!!.contains("Dimension 'ghostDim' has no flavors"))
    }

    @Test
    fun `multiple defaults per dimension emits warning but passes`() {
        val task = newTask()
        task.preset(
            flavorDefaults = mapOf("free" to true, "paid" to true),
        )
        task.validate() // warnings only — no throw
    }

    @Test
    fun `unknown active variant throws`() {
        val task = newTask()
        task.preset(active = "freeBananaSplit")
        val ex = assertThrows(GradleException::class.java) { task.validate() }
        assertTrue(ex.message!!.contains("Active variant 'freeBananaSplit' is not valid"))
    }

    @Test
    fun `valid identifier with underscore and digits passes`() {
        val task = newTask()
        task.preset(
            flavorDims = mapOf("free_v2" to "tier", "paid3" to "tier"),
            flavorDefaults = mapOf("free_v2" to true, "paid3" to false),
            variants = setOf("free_v2", "paid3"),
            active = "free_v2",
            all = listOf("free_v2", "paid3"),
        )
        task.validate()
    }

    @Test
    fun `validate passes when no dimensions defined and no active variant`() {
        val task = newTask()
        task.preset(
            dims = emptySet(),
            flavorDims = mapOf("free" to ""),
            flavorDefaults = mapOf("free" to false),
            variants = setOf("free"),
            active = null,
            all = listOf("free"),
        )
        task.validate()
    }

    @Test
    fun `aggregated error message numbers each error`() {
        val task = newTask()
        task.preset(
            flavorDims = mapOf("free" to "unknown", "paid" to "unknown"),
            all = listOf("free", "paid", "free"),
        )
        val ex = assertThrows(GradleException::class.java) { task.validate() }
        assertTrue(ex.message!!.contains("1. "))
        assertTrue(ex.message!!.contains("2. "))
    }
}
