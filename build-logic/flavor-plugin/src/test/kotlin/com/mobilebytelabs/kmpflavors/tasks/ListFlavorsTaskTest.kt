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

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ListFlavorsTaskTest {

    private val originalOut: PrintStream = System.out
    private val captured = ByteArrayOutputStream()

    @BeforeEach fun redirect() {
        System.setOut(PrintStream(captured))
    }

    @AfterEach fun restore() {
        System.setOut(originalOut)
    }

    private fun newTask(): ListFlavorsTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.register("listFlavors", ListFlavorsTask::class.java).get()
    }

    @Test
    fun `prints empty state when no variants configured`() {
        val task = newTask()
        task.variants.set(emptyMap())
        task.dimensions.set(emptyMap())
        task.platforms.set(emptyList())
        task.list()
        val out = captured.toString()
        assertTrue(out.contains("KMP Flavor Variants"))
        assertTrue(out.contains("Dimensions: (none)"))
        assertTrue(out.contains("Platforms: (none detected)"))
        assertTrue(out.contains("No variants configured"))
    }

    @Test
    fun `prints dimensions sorted by priority`() {
        val task = newTask()
        task.variants.set(mapOf("freeDev" to listOf("free", "dev")))
        task.dimensions.set(mapOf("env" to 1, "tier" to 0))
        task.platforms.set(emptyList())
        task.list()
        val out = captured.toString()
        val tierIdx = out.indexOf("• tier (priority: 0)")
        val envIdx = out.indexOf("• env (priority: 1)")
        assertTrue(tierIdx >= 0 && envIdx > tierIdx)
    }

    @Test
    fun `prints platforms with comma separator`() {
        val task = newTask()
        task.variants.set(mapOf("freeDev" to listOf("free", "dev")))
        task.dimensions.set(emptyMap())
        task.platforms.set(listOf("desktop", "iosArm64", "wasmJs"))
        task.list()
        assertTrue(captured.toString().contains("Platforms: desktop, iosArm64, wasmJs"))
    }

    @Test
    fun `marks active variant with arrow`() {
        val task = newTask()
        task.variants.set(
            mapOf(
                "freeDev" to listOf("free", "dev"),
                "paidProd" to listOf("paid", "prod"),
            ),
        )
        task.activeVariant.set("freeDev")
        task.dimensions.set(emptyMap())
        task.platforms.set(emptyList())
        task.list()
        val out = captured.toString()
        val freeLine = out.lines().first { it.contains("freeDev") }
        val paidLine = out.lines().first { it.contains("paidProd") }
        assertTrue(freeLine.contains("← ACTIVE"))
        assertTrue(!paidLine.contains("ACTIVE"))
    }

    @Test
    fun `sorts variants alphabetically by name`() {
        val task = newTask()
        task.variants.set(
            mapOf(
                "zebra" to listOf("z"),
                "alpha" to listOf("a"),
                "middle" to listOf("m"),
            ),
        )
        task.dimensions.set(emptyMap())
        task.platforms.set(emptyList())
        task.list()
        val out = captured.toString()
        val a = out.indexOf("alpha")
        val m = out.indexOf("middle")
        val z = out.indexOf("zebra")
        assertTrue(a > 0 && m > a && z > m)
    }

    @Test
    fun `task group and description are set`() {
        val task = newTask()
        assertEquals("kmp flavors", task.group)
        assertEquals("Lists all flavor variants", task.description)
    }

    @Test
    fun `pads variant names to consistent width`() {
        val task = newTask()
        task.variants.set(
            mapOf(
                "short" to listOf("a"),
                "veryLongVariantName" to listOf("b"),
            ),
        )
        task.dimensions.set(emptyMap())
        task.platforms.set(emptyList())
        task.list()
        // No exception during padding logic — verify both names appear.
        val out = captured.toString()
        assertTrue(out.contains("short"))
        assertTrue(out.contains("veryLongVariantName"))
    }
}
