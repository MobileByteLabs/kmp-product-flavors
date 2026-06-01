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

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.6 Phase 3 — Snapshot tests for [GenerateAnalyticsTagsTask] codegen output.
 *
 * Fixtures at `src/test/resources/analytics-snapshots/{name}.kt.txt`. Tests cover:
 *  - minimal-tags: no consumer-declared customTag — only VARIANT_NAME + BUILD_TYPE
 *  - with-custom-tags: 2 consumer-declared tags (alphabetised in emission)
 *  - 3d-multi-tag: 3 consumer-declared tags (alphabetised, longer variantName)
 */
class GenerateAnalyticsTagsTaskSnapshotTest {

    @TempDir
    lateinit var outputDir: File

    private fun newTask(): GenerateAnalyticsTagsTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.create("snapshotTestTask", GenerateAnalyticsTagsTask::class.java).apply {
            packageName.set("com.example.analytics")
            outputDirectory.set(outputDir)
        }
    }

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("analytics-snapshots/$name.kt.txt")
            ?: error("snapshot fixture not found: analytics-snapshots/$name.kt.txt")
        return stream.bufferedReader().readText()
    }

    private fun readGenerated(): String {
        val file = File(outputDir, "com/example/analytics/AnalyticsTags.kt")
        assertTrue(file.exists(), "expected generated file at ${file.absolutePath}")
        return file.readText()
    }

    @Test
    fun `minimal tags - no custom keys emits VARIANT_NAME + BUILD_TYPE only`() {
        val task = newTask()
        task.variantName.set("freeDebug")
        task.buildTypeName.set("debug")
        task.customTagValues.set(emptyMap())

        task.generate()

        assertEquals(fixture("minimal-tags"), readGenerated())
    }

    @Test
    fun `analytics with 2 custom tags emits matching AnalyticsTags-kt`() {
        val task = newTask()
        task.variantName.set("freeDev")
        task.buildTypeName.set("debug")
        task.customTagValues.set(mapOf("environment" to "dev", "tier" to "free"))

        task.generate()

        assertEquals(fixture("with-custom-tags"), readGenerated())
    }

    @Test
    fun `3D variant with 3 custom tags emits alphabetised tag constants`() {
        val task = newTask()
        task.variantName.set("paidProdTablet")
        task.buildTypeName.set("release")
        task.customTagValues.set(
            mapOf(
                "tier" to "paid",
                "environment" to "prod",
                "form" to "tablet",
            ),
        )

        task.generate()

        assertEquals(fixture("3d-multi-tag"), readGenerated())
    }
}
