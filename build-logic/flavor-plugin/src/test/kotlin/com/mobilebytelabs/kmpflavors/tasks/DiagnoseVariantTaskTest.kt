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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class DiagnoseVariantTaskTest {

    private val originalOut: PrintStream = System.out
    private val captured = ByteArrayOutputStream()

    @BeforeEach fun redirect() { System.setOut(PrintStream(captured)) }
    @AfterEach fun restore() { System.setOut(originalOut) }

    private fun newTask(): DiagnoseVariantTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("diagnoseVariant", DiagnoseVariantTask::class.java).get()
        task.flavorsByVariant.set(
            mapOf(
                "freeDev" to listOf("free", "dev"),
                "paidProd" to listOf("paid", "prod"),
            ),
        )
        task.buildTypeByVariant.set(mapOf("freeDev" to "debug", "paidProd" to ""))
        task.sourceSetsByVariant.set(
            mapOf(
                "freeDev" to listOf("commonMain", "freeMain", "devMain"),
                "paidProd" to listOf("commonMain"),
            ),
        )
        task.targetsByVariant.set(
            mapOf(
                "freeDev" to listOf("desktopJvm", "iosArm64"),
                "paidProd" to listOf("desktopJvm"),
            ),
        )
        task.buildConfigFieldsByVariant.set(
            mapOf(
                "freeDev" to mapOf("DEBUG_MODE" to "Boolean::true", "URL" to "String::\"https://x\""),
                "paidProd" to emptyMap(),
            ),
        )
        task.activeVariantName.set("freeDev")
        task.variantFilterCount.set(2)
        return task
    }

    @Test
    fun `defaults to active variant when variantToDiagnose is unset`() {
        val task = newTask()
        task.diagnose()
        val out = captured.toString()
        assertTrue(out.contains("KMPF diagnoseVariant: freeDev (ACTIVE)"))
        assertTrue(out.contains("DEBUG_MODE : Boolean::true"))
        assertTrue(out.contains("Variant filters   : 2 considered"))
    }

    @Test
    fun `explicit variantToDiagnose overrides active`() {
        val task = newTask()
        task.variantToDiagnose.set("paidProd")
        task.diagnose()
        val out = captured.toString()
        assertTrue(out.contains("KMPF diagnoseVariant: paidProd"))
        assertTrue(!out.contains("KMPF diagnoseVariant: paidProd (ACTIVE)"))
    }

    @Test
    fun `unknown variant throws IllegalArgumentException`() {
        val task = newTask()
        task.variantToDiagnose.set("nonExistent")
        val ex = assertThrows(IllegalArgumentException::class.java) { task.diagnose() }
        assertTrue(ex.message!!.contains("Unknown variant 'nonExistent'"))
        assertTrue(ex.message!!.contains("freeDev"))
        assertTrue(ex.message!!.contains("paidProd"))
    }

    @Test
    fun `human render handles empty source sets and empty buildConfig`() {
        val task = newTask()
        task.variantToDiagnose.set("paidProd")
        task.diagnose()
        val out = captured.toString()
        assertTrue(out.contains("Build type        : (none)"))
        assertTrue(out.contains("(no source sets — variant has no registered compilation on any target)") ||
            out.contains("commonMain"))
        assertTrue(out.contains("(none — no buildConfigField(...) declarations contribute to this variant)"))
    }

    @Test
    fun `json output renders a compact json object`() {
        val task = newTask()
        task.jsonOutput.set(true)
        task.diagnose()
        val out = captured.toString().trim()
        assertTrue(out.startsWith("{"))
        assertTrue(out.endsWith("}"))
        assertTrue(out.contains("\"variant\":\"freeDev\""))
        assertTrue(out.contains("\"active\":true"))
        assertTrue(out.contains("\"buildType\":\"debug\""))
        assertTrue(out.contains("\"variantFilterCount\":2"))
        assertTrue(out.contains("\"targets\":[\"desktopJvm\",\"iosArm64\"]"))
    }

    @Test
    fun `json buildType is null when empty`() {
        val task = newTask()
        task.variantToDiagnose.set("paidProd")
        task.jsonOutput.set(true)
        task.diagnose()
        val out = captured.toString().trim()
        assertTrue(out.contains("\"buildType\":null"))
    }

    @Test
    fun `json escapes special characters in fields`() {
        val task = newTask()
        task.buildConfigFieldsByVariant.set(
            mapOf("freeDev" to mapOf("MSG" to "String::\"hello\nworld\""))
        )
        task.jsonOutput.set(true)
        task.diagnose()
        val out = captured.toString().trim()
        // Newlines and quotes should be escaped, not literal.
        assertTrue(out.contains("\\\""))
        assertTrue(out.contains("\\n"))
    }

    @Test
    fun `task group and description are set`() {
        val task = newTask()
        assertEquals("kmp flavors", task.group)
        assertTrue(task.description!!.contains("BuildConfig"))
    }
}
