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

class ListActiveVariantTaskTest {

    private val originalOut: PrintStream = System.out
    private val captured = ByteArrayOutputStream()

    @BeforeEach fun redirect() {
        System.setOut(PrintStream(captured))
    }

    @AfterEach fun restore() {
        System.setOut(originalOut)
    }

    private fun newTask(): ListActiveVariantTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.register("listActiveVariant", ListActiveVariantTask::class.java).get()
    }

    @Test
    fun `prints active variant and full list`() {
        val task = newTask()
        task.activeVariantName.set("freeDev")
        task.allVariantNames.set(listOf("freeDev", "paidProd", "freeStaging"))
        task.list()
        val out = captured.toString()
        assertTrue(out.contains("KMP Flavors — active variant"))
        assertTrue(out.contains("Active : freeDev"))
        assertTrue(out.contains("All    : freeDev, paidProd, freeStaging"))
        assertTrue(out.contains("-PkmpFlavor=<variantName>"))
        // Example points at the first non-active variant.
        assertTrue(out.contains("-PkmpFlavor=paidProd"))
    }

    @Test
    fun `handles single variant case without crashing on missing example`() {
        val task = newTask()
        task.activeVariantName.set("solo")
        task.allVariantNames.set(listOf("solo"))
        task.list()
        val out = captured.toString()
        assertTrue(out.contains("Active : solo"))
        assertTrue(out.contains("All    : solo"))
        // No example line because no non-active variant exists.
        assertTrue(!out.contains("-PkmpFlavor=solo"))
    }

    @Test
    fun `handles zero variants gracefully`() {
        val task = newTask()
        task.activeVariantName.set("")
        task.allVariantNames.set(emptyList())
        task.list()
        val out = captured.toString()
        assertTrue(out.contains("(no variants registered)"))
    }

    @Test
    fun `task group and description set`() {
        val task = newTask()
        assertEquals("kmp flavors", task.group)
        assertTrue(task.description!!.contains("Option B"))
    }
}
