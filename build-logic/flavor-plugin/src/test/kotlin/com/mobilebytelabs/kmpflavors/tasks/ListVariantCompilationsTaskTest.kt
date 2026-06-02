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

class ListVariantCompilationsTaskTest {

    private val originalOut: PrintStream = System.out
    private val captured = ByteArrayOutputStream()

    @BeforeEach fun redirect() {
        System.setOut(PrintStream(captured))
    }

    @AfterEach fun restore() {
        System.setOut(originalOut)
    }

    private fun newTask(): ListVariantCompilationsTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.register("listVariantCompilations", ListVariantCompilationsTask::class.java).get()
    }

    @Test
    fun `prints no-variants message when allVariantNames empty`() {
        val task = newTask()
        task.allVariantNames.set(emptyList())
        task.allTargetNames.set(listOf("desktopJvm"))
        task.compilationByVariantTarget.set(emptyMap())
        task.activeVariantName.set("")
        task.list()
        assertTrue(captured.toString().contains("no variants resolved on this module"))
    }

    @Test
    fun `prints no-targets message when allTargetNames empty`() {
        val task = newTask()
        task.allVariantNames.set(listOf("freeDev"))
        task.allTargetNames.set(emptyList())
        task.compilationByVariantTarget.set(emptyMap())
        task.activeVariantName.set("freeDev")
        task.list()
        assertTrue(captured.toString().contains("no non-Android KMP targets on this module"))
    }

    @Test
    fun `prints markdown table for variants and targets`() {
        val task = newTask()
        task.allVariantNames.set(listOf("freeDev", "paidProd"))
        task.allTargetNames.set(listOf("desktopJvm", "iosArm64"))
        task.compilationByVariantTarget.set(
            mapOf(
                "freeDev::desktopJvm" to "main",
                "freeDev::iosArm64" to "main",
                "paidProd::desktopJvm" to "paidProd",
                "paidProd::iosArm64" to "",
            ),
        )
        task.activeVariantName.set("freeDev")
        task.list()
        val out = captured.toString()
        assertTrue(out.contains("2 variant(s) × 2 target(s)"))
        assertTrue(out.contains("| Variant"))
        assertTrue(out.contains("ACTIVE"))
        assertTrue(out.contains("inactive"))
        // Empty compilation cell renders as em-dash
        assertTrue(out.contains("—"))
    }

    @Test
    fun `header column widths adapt to longest variant name`() {
        val task = newTask()
        task.allVariantNames.set(listOf("a", "longerVariantName"))
        task.allTargetNames.set(listOf("desktopJvm"))
        task.compilationByVariantTarget.set(
            mapOf(
                "a::desktopJvm" to "main",
                "longerVariantName::desktopJvm" to "longerVariantName",
            ),
        )
        task.activeVariantName.set("a")
        task.list()
        val out = captured.toString()
        // longer name appears in output, table didn't truncate.
        assertTrue(out.contains("longerVariantName"))
    }

    @Test
    fun `task group and description are set`() {
        val task = newTask()
        assertEquals("kmp flavors", task.group)
        assertTrue(task.description!!.contains("variant × target"))
    }
}
