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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateVariantRunConfigurationsTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(projectPath: String = ":composeApp"): GenerateVariantRunConfigurationsTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("generateVariantRunConfigurations", GenerateVariantRunConfigurationsTask::class.java).get()
        task.outputDirectory.set(File(tempDir, ".run"))
        task.projectName.set("myApp")
        task.projectPath.set(projectPath)
        return task
    }

    @Test
    fun `no variants emits no files`() {
        val task = newTask()
        task.variantNames.set(emptyList())
        task.targetNames.set(listOf("desktopJvm"))
        task.activeVariantName.set("")
        task.generate()
        assertEquals(0, File(tempDir, ".run").listFiles()?.size ?: 0)
    }

    @Test
    fun `no targets emits no files`() {
        val task = newTask()
        task.variantNames.set(listOf("freeDev"))
        task.targetNames.set(emptyList())
        task.activeVariantName.set("freeDev")
        task.generate()
        assertEquals(0, File(tempDir, ".run").listFiles()?.size ?: 0)
    }

    @Test
    fun `emits one xml per variant×target pair`() {
        val task = newTask()
        task.variantNames.set(listOf("freeDev", "paidProd"))
        task.targetNames.set(listOf("desktopJvm", "iosArm64"))
        task.activeVariantName.set("freeDev")
        task.generate()
        val files = File(tempDir, ".run").listFiles()!!.map { it.name }.toSet()
        assertEquals(
            setOf(
                "myApp_freeDev_desktopJvm.run.xml",
                "myApp_freeDev_iosArm64.run.xml",
                "myApp_paidProd_desktopJvm.run.xml",
                "myApp_paidProd_iosArm64.run.xml",
            ),
            files,
        )
    }

    @Test
    fun `active variant uses standard compileKotlin task name`() {
        val task = newTask()
        task.variantNames.set(listOf("freeDev"))
        task.targetNames.set(listOf("desktopJvm"))
        task.activeVariantName.set("freeDev")
        task.generate()
        val content = File(tempDir, ".run/myApp_freeDev_desktopJvm.run.xml").readText()
        assertTrue(content.contains(":composeApp:compileKotlinDesktopJvm"))
        assertTrue(content.contains("-PkmpFlavor=freeDev"))
    }

    @Test
    fun `inactive variant uses compileVariantKotlinTarget task name`() {
        val task = newTask()
        task.variantNames.set(listOf("freeDev", "paidProd"))
        task.targetNames.set(listOf("desktopJvm"))
        task.activeVariantName.set("freeDev")
        task.generate()
        val content = File(tempDir, ".run/myApp_paidProd_desktopJvm.run.xml").readText()
        assertTrue(content.contains(":composeApp:compilePaidProdKotlinDesktopJvm"))
    }

    @Test
    fun `root project handles colon-only projectPath cleanly`() {
        val task = newTask(projectPath = ":")
        task.variantNames.set(listOf("freeDev"))
        task.targetNames.set(listOf("desktopJvm"))
        task.activeVariantName.set("freeDev")
        task.generate()
        val content = File(tempDir, ".run/myApp_freeDev_desktopJvm.run.xml").readText()
        assertTrue(content.contains(":compileKotlinDesktopJvm"))
        assertTrue(!content.contains("::"))
    }
}
