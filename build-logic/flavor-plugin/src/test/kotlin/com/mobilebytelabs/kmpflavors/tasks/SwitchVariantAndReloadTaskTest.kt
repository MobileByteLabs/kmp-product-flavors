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

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SwitchVariantAndReloadTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): SwitchVariantAndReloadTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        return project.tasks.register("switchVariantAndReload", SwitchVariantAndReloadTask::class.java).get()
    }

    @Test
    fun `missing --to throws Gradle exception`() {
        val task = newTask()
        task.knownVariants.set(listOf("free", "paid"))
        // targetVariant intentionally unset.
        val ex = assertThrows(GradleException::class.java) { task.switch() }
        assertTrue(ex.message!!.contains("Missing --to"))
    }

    @Test
    fun `unknown variant in knownVariants throws`() {
        val task = newTask()
        task.targetVariant.set("nonExistent")
        task.knownVariants.set(listOf("free", "paid"))
        val ex = assertThrows(GradleException::class.java) { task.switch() }
        assertTrue(ex.message!!.contains("Unknown variant 'nonExistent'"))
        assertTrue(ex.message!!.contains("free, paid"))
    }

    @Test
    fun `empty knownVariants permits any target (passthrough)`() {
        val task = newTask()
        task.targetVariant.set("anything")
        task.knownVariants.set(emptyList())
        task.switch()
        // Lock file written with chosen target.
        val lock = File(task.project.layout.buildDirectory.get().asFile, "kmpFlavor.lock")
        assertTrue(lock.exists())
        assertTrue(lock.readText().contains("kmpFlavor=anything"))
    }

    @Test
    fun `valid switch writes kmpFlavor lock file`() {
        val task = newTask()
        task.targetVariant.set("paidStaging")
        task.knownVariants.set(listOf("free", "paidStaging"))
        task.switch()
        val lock = File(task.project.layout.buildDirectory.get().asFile, "kmpFlavor.lock")
        assertTrue(lock.exists())
        val content = lock.readText()
        assertTrue(content.contains("kmpFlavor=paidStaging"))
        assertTrue(content.contains("Written by switchVariantAndReload"))
    }

    @Test
    fun `task group and description set`() {
        val task = newTask()
        assertEquals("kmp flavors", task.group)
        assertTrue(task.description!!.contains("Switch the active"))
    }
}
