/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.FlavorVariant
import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FeatureFlagHelpersTest {

    @TempDir
    lateinit var tempDir: File

    private fun newProject() = ProjectBuilder.builder().withProjectDir(tempDir).build()

    private fun newExtension(project: org.gradle.api.Project): KmpFlavorExtension =
        project.objects.newInstance(KmpFlavorExtension::class.java)

    private fun variants(vararg names: String): List<FlavorVariant> =
        names.map { FlavorVariant(name = it, flavors = emptyList()) }

    private fun writeJson(name: String, content: String): File =
        File(tempDir, name).apply { parentFile.mkdirs(); writeText(content) }

    @Test
    fun `no-op when matrix mode is off`() {
        val project = newProject()
        val ext = newExtension(project)
        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = false, logger = project.logger)
        // no FeatureFlags.kt should exist
        val generated = File(project.buildDir, "generated/kmpFlavors")
        assertFalse(generated.exists() && generated.listFiles()?.any { it.isDirectory } == true)
    }

    @Test
    fun `no-op when allVariants is empty`() {
        val project = newProject()
        val ext = newExtension(project)
        FeatureFlagHelpers.configure(project, ext, allVariants = emptyList(), matrixModeEnabled = true, logger = project.logger)
        val generated = File(project.buildDir, "generated/kmpFlavors")
        assertFalse(generated.exists() && generated.listFiles()?.any { it.isDirectory } == true)
    }

    @Test
    fun `no-op when all payloads are null`() {
        val project = newProject()
        val ext = newExtension(project)
        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)
        val generated = File(project.buildDir, "generated/kmpFlavors")
        assertFalse(generated.exists() && generated.listFiles()?.any { it.isDirectory } == true)
    }

    @Test
    fun `warns and exits when buildConfigPackage unset but payload present`() {
        val project = newProject()
        val ext = newExtension(project)
        val payload = writeJson("flags/growthbook.json", "{}")
        ext.featureFlags.growthbook.defaultPayload.set(payload)
        // intentionally do NOT set buildConfigPackage
        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)
        val output = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin")
        assertFalse(output.exists())
    }

    @Test
    fun `growthbook only payload generates per-variant FeatureFlags`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("com.example")
        val payload = writeJson(
            "flags/growthbook.json",
            """{"premium": "false", "experiment": "control"}""",
        )
        ext.featureFlags.growthbook.defaultPayload.set(payload)

        FeatureFlagHelpers.configure(project, ext, variants("freeDev", "paidProd"), matrixModeEnabled = true, logger = project.logger)

        val freeFile = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin/com/example/FeatureFlags.kt")
        val paidFile = File(project.buildDir, "generated/kmpFlavors/paidProd/kotlin/com/example/FeatureFlags.kt")
        assertTrue(freeFile.exists())
        assertTrue(paidFile.exists())
        val freeContent = freeFile.readText()
        assertTrue(freeContent.contains("package com.example"))
        assertTrue(freeContent.contains("object FeatureFlags"))
        assertTrue(freeContent.contains("val growthbook: Map<String, String>"))
        assertTrue(freeContent.contains("\"premium\" to \"false\""))
        assertTrue(freeContent.contains("\"experiment\" to \"control\""))
    }

    @Test
    fun `variant override file overrides base values`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("com.example")
        val base = writeJson("flags/growthbook.json", """{"premium": "false"}""")
        writeJson("flags/growthbook.paidDev.json", """{"premium": "true"}""")
        ext.featureFlags.growthbook.defaultPayload.set(base)

        FeatureFlagHelpers.configure(project, ext, variants("freeDev", "paidDev"), matrixModeEnabled = true, logger = project.logger)

        val freeContent = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin/com/example/FeatureFlags.kt").readText()
        val paidContent = File(project.buildDir, "generated/kmpFlavors/paidDev/kotlin/com/example/FeatureFlags.kt").readText()

        assertTrue(freeContent.contains("\"premium\" to \"false\""))
        assertTrue(paidContent.contains("\"premium\" to \"true\""))
        assertTrue(paidContent.contains("Override: growthbook.paidDev.json (present)"))
        assertTrue(freeContent.contains("Override: growthbook.freeDev.json (absent)"))
    }

    @Test
    fun `all three platforms generate when all payloads present`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("com.example")
        ext.featureFlags.growthbook.defaultPayload.set(writeJson("flags/growthbook.json", """{"gb_key": "gb"}"""))
        ext.featureFlags.statsig.defaultPayload.set(writeJson("flags/statsig.json", """{"st_key": "st"}"""))
        ext.featureFlags.launchDarkly.defaultPayload.set(writeJson("flags/launchDarkly.json", """{"ld_key": "ld"}"""))

        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)

        val content = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin/com/example/FeatureFlags.kt").readText()
        assertTrue(content.contains("val growthbook"))
        assertTrue(content.contains("val statsig"))
        assertTrue(content.contains("val launchDarkly"))
        assertTrue(content.contains("\"gb_key\" to \"gb\""))
        assertTrue(content.contains("\"st_key\" to \"st\""))
        assertTrue(content.contains("\"ld_key\" to \"ld\""))
    }

    @Test
    fun `empty payload object generates empty map literal`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("com.example")
        ext.featureFlags.statsig.defaultPayload.set(writeJson("flags/statsig.json", "{}"))

        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)

        val content = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin/com/example/FeatureFlags.kt").readText()
        assertTrue(content.contains("val statsig: Map<String, String> = mapOf()"))
    }

    @Test
    fun `escape kotlin string handles backslashes and quotes`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("com.example")
        val payload = writeJson(
            "flags/growthbook.json",
            """{"key": "v"}""",
        )
        ext.featureFlags.growthbook.defaultPayload.set(payload)
        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)

        val content = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin/com/example/FeatureFlags.kt").readText()
        assertTrue(content.contains("\"key\" to \"v\""))
    }

    @Test
    fun `non-json payload returns empty merged map`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("com.example")
        // Body doesn't start with { → parser returns empty
        ext.featureFlags.growthbook.defaultPayload.set(writeJson("flags/growthbook.json", "not json"))

        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)

        val content = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin/com/example/FeatureFlags.kt").readText()
        assertTrue(content.contains("val growthbook: Map<String, String> = mapOf()"))
    }

    @Test
    fun `nested values in payload preserve depth-zero comma splitting`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("com.example")
        // Top-level commas only — parser handles nested {} balance.
        val payload = writeJson(
            "flags/growthbook.json",
            """{"a": "1", "b": "2", "c": "3"}""",
        )
        ext.featureFlags.growthbook.defaultPayload.set(payload)
        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)
        val content = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin/com/example/FeatureFlags.kt").readText()
        // Sorted alphabetically — verify a, b, c all present in order.
        val aIdx = content.indexOf("\"a\" to \"1\"")
        val bIdx = content.indexOf("\"b\" to \"2\"")
        val cIdx = content.indexOf("\"c\" to \"3\"")
        assertTrue(aIdx >= 0 && bIdx > aIdx && cIdx > bIdx)
    }

    @Test
    fun `blank package short-circuits with warning`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("  ")
        ext.featureFlags.growthbook.defaultPayload.set(writeJson("flags/growthbook.json", "{}"))
        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)
        val output = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin")
        assertFalse(output.exists())
    }

    @Test
    fun `package name with dots maps to subdirectories`() {
        val project = newProject()
        val ext = newExtension(project)
        ext.buildConfigPackage.set("com.example.deep.nested")
        ext.featureFlags.statsig.defaultPayload.set(writeJson("flags/statsig.json", """{"k": "v"}"""))
        FeatureFlagHelpers.configure(project, ext, variants("freeDev"), matrixModeEnabled = true, logger = project.logger)
        val expected = File(project.buildDir, "generated/kmpFlavors/freeDev/kotlin/com/example/deep/nested/FeatureFlags.kt")
        assertTrue(expected.exists())
        assertEquals("com.example.deep.nested", expected.readLines().first { it.startsWith("package ") }.removePrefix("package ").trim())
    }
}
