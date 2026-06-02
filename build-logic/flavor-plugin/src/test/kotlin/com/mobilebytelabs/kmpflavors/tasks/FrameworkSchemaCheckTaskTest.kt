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

class FrameworkSchemaCheckTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): FrameworkSchemaCheckTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("frameworkSchemaCheck", FrameworkSchemaCheckTask::class.java).get()
        task.outputMarker.set(File(tempDir, "marker.txt"))
        return task
    }

    @Test
    fun `no secrets declared writes OK no-op marker`() {
        val task = newTask()
        task.declaredSecretIds.set(emptyList())
        task.check()
        val out = task.outputMarker.get().asFile.readText()
        assertEquals("OK: no secrets declared (task was a no-op)\n", out)
    }

    @Test
    fun `secrets declared without manifest emits KMPF-V26 manifest-missing`() {
        val task = newTask()
        task.declaredSecretIds.set(listOf("API_KEY"))
        // No manifest file set.
        task.check()
        val out = task.outputMarker.get().asFile.readText()
        assertTrue(out.contains("WARN: KMPF-V26 manifest-missing"))
        assertTrue(out.contains("'API_KEY'"))
    }

    @Test
    fun `manifest with schema 2_0 triggers schema-v20-fallback`() {
        val task = newTask()
        val manifest = File(tempDir, "secrets-manifest.yaml")
        manifest.writeText("""schema_version: "2.0"""")
        task.declaredSecretIds.set(listOf("API_KEY"))
        task.secretsManifestFile.set(manifest)
        task.check()
        val out = task.outputMarker.get().asFile.readText()
        assertTrue(out.contains("WARN: KMPF-V26 schema-v20-fallback"))
        assertTrue(out.contains("schema_version='2.0'"))
    }

    @Test
    fun `manifest with schema 2_1 writes OK`() {
        val task = newTask()
        val manifest = File(tempDir, "secrets-manifest.yaml")
        manifest.writeText("""schema_version: "2.1"""")
        task.declaredSecretIds.set(listOf("API_KEY", "AUTH_TOKEN"))
        task.secretsManifestFile.set(manifest)
        task.check()
        val out = task.outputMarker.get().asFile.readText()
        assertTrue(out.startsWith("OK"))
        assertTrue(out.contains("schema_version='2.1'"))
        assertTrue(out.contains("API_KEY"))
        assertTrue(out.contains("AUTH_TOKEN"))
    }

    @Test
    fun `manifest with unknown schema version triggers fallback`() {
        val task = newTask()
        val manifest = File(tempDir, "secrets-manifest.yaml")
        manifest.writeText("# only a comment line\n")
        task.declaredSecretIds.set(listOf("X"))
        task.secretsManifestFile.set(manifest)
        task.check()
        val out = task.outputMarker.get().asFile.readText()
        assertTrue(out.contains("schema-v20-fallback"))
    }

    @Test
    fun `task group and description set`() {
        val task = newTask()
        assertEquals("kmp flavors", task.group)
        assertTrue(task.description!!.contains("schema is v2.1+"))
    }

    @Test
    fun `manifest with schema 3_0 passes through OK path`() {
        val task = newTask()
        val manifest = File(tempDir, "secrets-manifest.yaml")
        manifest.writeText("""schema_version: "3.0"""")
        task.declaredSecretIds.set(listOf("API_KEY"))
        task.secretsManifestFile.set(manifest)
        task.check()
        val out = task.outputMarker.get().asFile.readText()
        assertTrue(out.startsWith("OK"))
    }
}
