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

class GenerateRunConfigurationsTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): GenerateRunConfigurationsTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("generateRunConfigurations", GenerateRunConfigurationsTask::class.java).get()
        task.outputDirectory.set(File(tempDir, ".run"))
        task.projectName.set("myApp")
        task.projectPath.set(":composeApp")
        return task
    }

    @Test
    fun `generates one run xml per variant plus a list config`() {
        val task = newTask()
        task.variants.set(
            mapOf(
                "freeDev" to listOf("free", "dev"),
                "paidProd" to listOf("paid", "prod"),
            ),
        )
        task.activeVariant.set("freeDev")
        task.generate()

        val outDir = File(tempDir, ".run")
        assertTrue(File(outDir, "myApp_freeDev.run.xml").exists())
        assertTrue(File(outDir, "myApp_paidProd.run.xml").exists())
        assertTrue(File(outDir, "myApp_listFlavors.run.xml").exists())
    }

    @Test
    fun `xml content contains script parameter with kmpFlavor flag`() {
        val task = newTask()
        task.variants.set(mapOf("freeDev" to listOf("free", "dev")))
        task.activeVariant.set("freeDev")
        task.generate()

        val content = File(tempDir, ".run/myApp_freeDev.run.xml").readText()
        assertTrue(content.contains("name=\"myApp [freeDev]\""))
        assertTrue(content.contains(":composeApp:assemble -PkmpFlavor=freeDev"))
        assertTrue(content.contains("type=\"GradleRunConfiguration\""))
    }

    @Test
    fun `list flavors config omits the kmpFlavor flag`() {
        val task = newTask()
        task.variants.set(mapOf("freeDev" to listOf("free")))
        task.activeVariant.set("freeDev")
        task.generate()

        val content = File(tempDir, ".run/myApp_listFlavors.run.xml").readText()
        assertTrue(content.contains(":composeApp:listFlavors"))
        // No -PkmpFlavor flag on the listFlavors config.
        assertTrue(!content.contains("-PkmpFlavor"))
    }

    @Test
    fun `gradleTasks convention defaults to assemble`() {
        val task = newTask()
        assertEquals(listOf("assemble"), task.gradleTasks.get())
    }

    @Test
    fun `custom gradleTasks list propagates to script parameters`() {
        val task = newTask()
        task.gradleTasks.set(listOf("build", "test"))
        task.variants.set(mapOf("freeDev" to listOf("free")))
        task.activeVariant.set("freeDev")
        task.generate()
        val content = File(tempDir, ".run/myApp_freeDev.run.xml").readText()
        assertTrue(content.contains(":composeApp:build :composeApp:test -PkmpFlavor=freeDev"))
    }
}
