/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.mobilebytelabs.kmpflavors.tasks

import com.mobilebytelabs.kmpflavors.KoinModuleSpec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.6 Phase 3 — Snapshot tests for [GenerateKoinModulesTask] codegen output.
 *
 * Same pattern as [BuildKonfigCodegenSnapshotTest]: fixtures live at
 * `src/test/resources/koin-snapshots/{name}.kt.txt`; tests diff the generated
 * file against the committed fixture. Codegen is deterministic string-template
 * so fixtures are authoritative.
 *
 * **What's covered:**
 *  - 2D single-variantModule actual val emission (one module, one flavor)
 *  - 3D multi-variantModule actual val emission (two modules, one flavor)
 *  - commonMain `expect val` + `flavorDependentModules()` aggregator for both
 *    1-module and 2-module specs
 */
class GenerateKoinModulesTaskSnapshotTest {

    @TempDir
    lateinit var outputDir: File

    private fun newTask(): GenerateKoinModulesTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.create("snapshotTestTask", GenerateKoinModulesTask::class.java).apply {
            packageName.set("com.example.di")
            outputDirectory.set(outputDir)
        }
    }

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("koin-snapshots/$name.kt.txt")
            ?: error("snapshot fixture not found: koin-snapshots/$name.kt.txt")
        return stream.bufferedReader().readText()
    }

    private fun readGenerated(relativePath: String): String {
        val file = File(outputDir, relativePath)
        assertTrue(file.exists(), "expected generated file at ${file.absolutePath}")
        return file.readText()
    }

    @Test
    fun `2D variantModule emits matching actual val + aggregator`() {
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

        assertEquals(
            fixture("2d-network-actual"),
            readGenerated("com/example/di/NetworkKoinActual.kt"),
        )
        assertEquals(
            fixture("2d-aggregator"),
            readGenerated("com/example/di/FlavorDependentModules.kt"),
        )
    }

    @Test
    fun `3D multi-variantModule emits matching actual val + 2-module aggregator`() {
        val task = newTask()
        task.variantName.set("freeDevPhone")
        task.moduleSpecs.set(
            listOf(
                KoinModuleSpec(
                    moduleName = "network",
                    variantBindings = mapOf("free" to "    single { FreeNetworkFactory() }"),
                ),
                KoinModuleSpec(
                    moduleName = "analytics",
                    variantBindings = mapOf(
                        "free" to "    single { FreeAnalyticsHelper() }\n    bind<AnalyticsHelper>()",
                    ),
                ),
            ),
        )

        task.generate()

        assertEquals(
            fixture("2d-network-actual"),
            readGenerated("com/example/di/NetworkKoinActual.kt"),
        )
        assertEquals(
            fixture("3d-analytics-actual"),
            readGenerated("com/example/di/AnalyticsKoinActual.kt"),
        )
        assertEquals(
            fixture("3d-aggregator"),
            readGenerated("com/example/di/FlavorDependentModules.kt"),
        )
    }
}
