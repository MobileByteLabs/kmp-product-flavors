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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateAnalyticsTagsTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): GenerateAnalyticsTagsTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("genAnalyticsTags", GenerateAnalyticsTagsTask::class.java).get()
        task.outputDirectory.set(tempDir)
        return task
    }

    @Test
    fun `generates AnalyticsTags_kt with constants and attachTo method`() {
        val task = newTask()
        task.packageName.set("com.example")
        task.variantName.set("freeDev")
        task.buildTypeName.set("debug")
        task.customTagValues.set(mapOf("environment" to "dev", "tier" to "free"))
        task.generate()

        val file = File(tempDir, "com/example/AnalyticsTags.kt")
        assertTrue(file.exists())
        val text = file.readText()
        assertTrue(text.contains("package com.example"))
        assertTrue(text.contains("object AnalyticsTags"))
        assertTrue(text.contains("const val VARIANT_NAME: String = \"freeDev\""))
        assertTrue(text.contains("const val BUILD_TYPE: String = \"debug\""))
        assertTrue(text.contains("const val ENVIRONMENT: String = \"dev\""))
        assertTrue(text.contains("const val TIER: String = \"free\""))
        assertTrue(text.contains("fun attachTo(target: Any)"))
        assertTrue(text.contains("method.invoke(target, \"environment\", ENVIRONMENT)"))
        assertTrue(text.contains("method.invoke(target, \"tier\", TIER)"))
    }

    @Test
    fun `generates AnalyticsTags_kt with no custom tags`() {
        val task = newTask()
        task.packageName.set("com.example")
        task.variantName.set("free")
        task.buildTypeName.set("")
        task.customTagValues.set(emptyMap())
        task.generate()
        val text = File(tempDir, "com/example/AnalyticsTags.kt").readText()
        assertTrue(text.contains("VARIANT_NAME: String = \"free\""))
        assertTrue(text.contains("BUILD_TYPE: String = \"\""))
        // Only the two built-ins, no custom invokes.
        val invokeCount = "method.invoke".toRegex().findAll(text).count()
        assertTrue(invokeCount == 2)
    }

    @Test
    fun `custom tags are sorted alphabetically in output`() {
        val task = newTask()
        task.packageName.set("p")
        task.variantName.set("v")
        task.buildTypeName.set("b")
        task.customTagValues.set(mapOf("zeta" to "1", "alpha" to "2", "middle" to "3"))
        task.generate()
        val text = File(tempDir, "p/AnalyticsTags.kt").readText()
        val alphaIdx = text.indexOf("const val ALPHA")
        val middleIdx = text.indexOf("const val MIDDLE")
        val zetaIdx = text.indexOf("const val ZETA")
        assertTrue(alphaIdx > 0 && middleIdx > alphaIdx && zetaIdx > middleIdx)
    }

    @Test
    fun `package name with dots maps to subdirectories`() {
        val task = newTask()
        task.packageName.set("com.example.deep.nested")
        task.variantName.set("free")
        task.buildTypeName.set("")
        task.customTagValues.set(emptyMap())
        task.generate()
        assertTrue(File(tempDir, "com/example/deep/nested/AnalyticsTags.kt").exists())
    }
}
