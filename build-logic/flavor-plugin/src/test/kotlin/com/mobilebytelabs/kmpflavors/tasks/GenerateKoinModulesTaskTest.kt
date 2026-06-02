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

import com.mobilebytelabs.kmpflavors.KoinModuleSpec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateKoinModulesTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): GenerateKoinModulesTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("genKoin", GenerateKoinModulesTask::class.java).get()
        task.outputDirectory.set(tempDir)
        task.packageName.set("com.example")
        return task
    }

    @Test
    fun `empty specs short-circuits without writing files`() {
        val task = newTask()
        task.variantName.set("free")
        task.moduleSpecs.set(emptyList())
        task.generate()
        val pkgDir = File(tempDir, "com/example")
        assertFalse(pkgDir.exists())
    }

    @Test
    fun `single module spec writes actual val + aggregator`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.moduleSpecs.set(
            listOf(
                KoinModuleSpec(
                    moduleName = "network",
                    variantBindings = mapOf("free" to "    single { FreeNetworkFactory() }"),
                ),
            ),
        )
        task.generate()
        val actual = File(tempDir, "com/example/NetworkKoinActual.kt")
        val agg = File(tempDir, "com/example/FlavorDependentModules.kt")
        assertTrue(actual.exists())
        assertTrue(agg.exists())
        val actualText = actual.readText()
        assertTrue(actualText.contains("actual val networkModule: Module = module {"))
        assertTrue(actualText.contains("FreeNetworkFactory()"))
        val aggText = agg.readText()
        assertTrue(aggText.contains("expect val networkModule: Module"))
        assertTrue(aggText.contains("fun flavorDependentModules(): List<Module>"))
    }

    @Test
    fun `multiple modules join aggregator list with commas`() {
        val task = newTask()
        task.variantName.set("free")
        task.moduleSpecs.set(
            listOf(
                KoinModuleSpec("network", mapOf("free" to "single { N() }")),
                KoinModuleSpec("analytics", mapOf("free" to "single { A() }")),
                KoinModuleSpec("storage", mapOf("free" to "single { S() }")),
            ),
        )
        task.generate()
        val aggText = File(tempDir, "com/example/FlavorDependentModules.kt").readText()
        assertTrue(aggText.contains("networkModule,"))
        assertTrue(aggText.contains("analyticsModule,"))
        // Last entry has no trailing comma.
        assertTrue(aggText.contains("storageModule\n)"))
    }

    @Test
    fun `flavor with no binding for primary flavor is skipped`() {
        val task = newTask()
        task.variantName.set("paidProd")
        task.moduleSpecs.set(
            listOf(
                KoinModuleSpec("network", mapOf("free" to "single { F() }")), // no `paid` binding
            ),
        )
        task.generate()
        val actual = File(tempDir, "com/example/NetworkKoinActual.kt")
        assertFalse(actual.exists())
        // Aggregator still written.
        val agg = File(tempDir, "com/example/FlavorDependentModules.kt")
        assertTrue(agg.exists())
    }

    @Test
    fun `primary flavor extracted from lowercase prefix of variant name`() {
        val task = newTask()
        task.variantName.set("freeBetaStaging")
        task.moduleSpecs.set(
            listOf(
                KoinModuleSpec("net", mapOf("free" to "single { X() }")),
            ),
        )
        task.generate()
        val actual = File(tempDir, "com/example/NetKoinActual.kt")
        assertTrue(actual.exists())
        assertTrue(actual.readText().contains("X()"))
    }

    @Test
    fun `binding body without trailing newline still closes cleanly`() {
        val task = newTask()
        task.variantName.set("free")
        task.moduleSpecs.set(
            listOf(
                KoinModuleSpec("net", mapOf("free" to "single { X() }")), // no trailing \n
            ),
        )
        task.generate()
        val text = File(tempDir, "com/example/NetKoinActual.kt").readText()
        // `}` lands on its own line — no trailing-newline duplication.
        assertTrue(text.endsWith("}\n"))
    }
}
