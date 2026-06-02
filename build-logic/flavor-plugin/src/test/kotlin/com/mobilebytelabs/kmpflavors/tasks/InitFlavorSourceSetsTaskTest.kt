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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class InitFlavorSourceSetsTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): InitFlavorSourceSetsTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("kmpFlavorInit", InitFlavorSourceSetsTask::class.java).get()
        task.sourceDirectory.set(File(tempDir, "src"))
        return task
    }

    @Test
    fun `conventions are set in init`() {
        val task = newTask()
        assertTrue(task.createIntermediates.get())
        assertTrue(task.createGitKeep.get())
        assertFalse(task.createExampleFiles.get())
        assertTrue(task.createReadmePerSourceSet.get())
        assertTrue(task.generateSampleCode.get())
        assertEquals("BuildKonfig", task.buildConfigClassName.get())
    }

    @Test
    fun `init creates per-flavor common and platform directories`() {
        val task = newTask()
        task.flavorNames.set(setOf("free", "paid"))
        task.platformPrefixes.set(setOf("android", "ios"))
        task.intermediatePrefixes.set(emptySet())
        task.generateSampleCode.set(false)
        task.init()

        val srcDir = File(tempDir, "src")
        assertTrue(File(srcDir, "commonFree/kotlin").exists())
        assertTrue(File(srcDir, "commonPaid/kotlin").exists())
        assertTrue(File(srcDir, "androidFree/kotlin").exists())
        assertTrue(File(srcDir, "iosPaid/kotlin").exists())
    }

    @Test
    fun `intermediates emit when createIntermediates true`() {
        val task = newTask()
        task.flavorNames.set(setOf("free"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(setOf("web", "native"))
        task.generateSampleCode.set(false)
        task.init()

        assertTrue(File(tempDir, "src/webFree/kotlin").exists())
        assertTrue(File(tempDir, "src/nativeFree/kotlin").exists())
    }

    @Test
    fun `gitkeep is created when enabled`() {
        val task = newTask()
        task.flavorNames.set(setOf("free"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        task.createGitKeep.set(true)
        task.generateSampleCode.set(false)
        task.init()
        assertTrue(File(tempDir, "src/commonFree/kotlin/.gitkeep").exists())
        assertTrue(File(tempDir, "src/commonFree/resources/.gitkeep").exists())
    }

    @Test
    fun `gitkeep is not created when disabled`() {
        val task = newTask()
        task.flavorNames.set(setOf("free"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        task.createGitKeep.set(false)
        task.generateSampleCode.set(false)
        task.init()
        assertFalse(File(tempDir, "src/commonFree/kotlin/.gitkeep").exists())
    }

    @Test
    fun `README is written when enabled`() {
        val task = newTask()
        task.flavorNames.set(setOf("free"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        task.createReadmePerSourceSet.set(true)
        task.generateSampleCode.set(false)
        task.init()
        val readme = File(tempDir, "src/commonFree/README.md")
        assertTrue(readme.exists())
        val text = readme.readText()
        assertTrue(text.contains("# `commonFree/`"))
        assertTrue(text.contains("flavor-specific"))
    }

    @Test
    fun `example file written when enabled with package`() {
        val task = newTask()
        task.flavorNames.set(setOf("free"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        task.createExampleFiles.set(true)
        task.examplePackage.set("com.example")
        task.generateSampleCode.set(false)
        task.init()
        val expected = File(tempDir, "src/commonFree/kotlin/com/example/CommonFreeExample.kt")
        assertTrue(expected.exists())
        val text = expected.readText()
        assertTrue(text.contains("object CommonFreeExample"))
        assertTrue(text.contains("Hello from commonFree!"))
    }

    @Test
    fun `Sample_kt written to commonMain when generateSampleCode and examplePackage set`() {
        val task = newTask()
        task.flavorNames.set(setOf("free", "paid"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        task.examplePackage.set("com.example")
        task.init()
        val sample = File(tempDir, "src/commonMain/kotlin/com/example/Sample.kt")
        assertTrue(sample.exists())
        val text = sample.readText()
        assertTrue(text.contains("package com.example"))
        assertTrue(text.contains("object Sample"))
        assertTrue(text.contains("BuildKonfig.VARIANT_NAME"))
    }

    @Test
    fun `Sample_kt skipped when examplePackage unset`() {
        val task = newTask()
        task.flavorNames.set(setOf("free"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        // examplePackage unset.
        task.init()
        val sample = File(tempDir, "src/commonMain/kotlin/com/example/Sample.kt")
        assertFalse(sample.exists())
    }

    @Test
    fun `Sample_kt skipped when generateSampleCode false`() {
        val task = newTask()
        task.flavorNames.set(setOf("free"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        task.examplePackage.set("com.example")
        task.generateSampleCode.set(false)
        task.init()
        val sample = File(tempDir, "src/commonMain/kotlin/com/example/Sample.kt")
        assertFalse(sample.exists())
    }

    @Test
    fun `Sample_kt idempotent — second run does not overwrite`() {
        val task = newTask()
        task.flavorNames.set(setOf("free"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        task.examplePackage.set("com.example")
        task.init()
        val sample = File(tempDir, "src/commonMain/kotlin/com/example/Sample.kt")
        val original = sample.readText()
        // Mutate the file content to detect overwrite.
        sample.writeText("// HAND-EDITED")
        task.init()
        assertEquals("// HAND-EDITED", sample.readText())
        // Sanity — confirm original was not blank.
        assertTrue(original.isNotEmpty())
    }

    @Test
    fun `Test suffix triggers test classification in README`() {
        val task = newTask()
        task.flavorNames.set(setOf("freeTest"))
        task.platformPrefixes.set(emptySet())
        task.intermediatePrefixes.set(emptySet())
        task.createReadmePerSourceSet.set(true)
        task.generateSampleCode.set(false)
        task.init()
        // Source set `commonFreeTest` (commonMain prefix + flavor name "freeTest" → capitalised)
        // — verify that the README classification is the "Test" branch for *Test suffix.
        // Branch: classifySourceSet/describePurpose include "Tests that exercise behaviour" etc.
        val readme = File(tempDir, "src/commonFreeTest/README.md")
        assertTrue(readme.exists())
        val text = readme.readText()
        assertTrue(text.contains("Tests that exercise behaviour"))
    }
}
