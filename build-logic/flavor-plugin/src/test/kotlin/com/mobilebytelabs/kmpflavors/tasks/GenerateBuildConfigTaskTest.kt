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

import com.mobilebytelabs.kmpflavors.BuildConfigField
import com.mobilebytelabs.kmpflavors.CustomFieldDeclaration
import com.mobilebytelabs.kmpflavors.NetworkConfigSpec
import com.mobilebytelabs.kmpflavors.PerTargetFieldDeclaration
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateBuildConfigTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): GenerateBuildConfigTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("genBuildKonfig", GenerateBuildConfigTask::class.java).get()
        task.outputDirectory.set(tempDir)
        task.packageName.set("com.example")
        task.className.set("BuildKonfig")
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "paid", "dev", "prod"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.allBuildTypeNames.set(emptySet())
        task.activeBuildTypeName.set("")
        task.buildConfigFields.set(emptyMap())
        return task
    }

    @Test
    fun `generates IS_FLAVOR constants for every flavor`() {
        val task = newTask()
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("const val VARIANT_NAME: String = \"freeDev\""))
        assertTrue(text.contains("const val IS_FREE: Boolean = true"))
        assertTrue(text.contains("const val IS_PAID: Boolean = false"))
        assertTrue(text.contains("const val IS_DEV: Boolean = true"))
        assertTrue(text.contains("const val IS_PROD: Boolean = false"))
    }

    @Test
    fun `buildType block emits when allBuildTypeNames present`() {
        val task = newTask()
        task.allBuildTypeNames.set(setOf("debug", "release"))
        task.activeBuildTypeName.set("debug")
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("const val BUILD_TYPE: String = \"debug\""))
        assertTrue(text.contains("const val IS_DEBUG: Boolean = true"))
        assertTrue(text.contains("const val IS_RELEASE: Boolean = false"))
    }

    @Test
    fun `custom buildConfigFields appended sorted by name`() {
        val task = newTask()
        task.buildConfigFields.set(
            mapOf(
                "BASE_URL" to BuildConfigField("String", "BASE_URL", "\"https://x\""),
                "PREMIUM" to BuildConfigField("Boolean", "PREMIUM", "false"),
            ),
        )
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("const val BASE_URL: String = \"https://x\""))
        assertTrue(text.contains("const val PREMIUM: Boolean = false"))
    }

    @Test
    fun `dimensionEnumSpecs emit sealed class with active val`() {
        val task = newTask()
        task.dimensionEnumSpecs.set(
            listOf(
                DimensionEnumSpec(
                    dimensionName = "tier",
                    flavorNames = listOf("free", "paid"),
                    activeFlavorName = "free",
                ),
            ),
        )
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("sealed class Tier {"))
        assertTrue(text.contains("object Free : Tier()"))
        assertTrue(text.contains("object Paid : Tier()"))
        assertTrue(text.contains("val tier: Tier = Tier.Free"))
    }

    @Test
    fun `dimensionEnumSpecs omit val when active flavor null`() {
        val task = newTask()
        task.dimensionEnumSpecs.set(
            listOf(
                DimensionEnumSpec("env", listOf("dev", "prod"), activeFlavorName = null),
            ),
        )
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("sealed class Env"))
        assertTrue(!text.contains("val env: Env"))
    }

    @Test
    fun `customField specs emit raw type and value`() {
        val task = newTask()
        task.customFieldSpecs.set(
            listOf(CustomFieldDeclaration("scopes", "List<String>", "listOf(\"read\", \"write\")")),
        )
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("val scopes: List<String> = listOf(\"read\", \"write\")"))
    }

    @Test
    fun `secret IDs emit placeholder constants with SCREAMING_SNAKE`() {
        val task = newTask()
        task.buildKonfigSecretIds.set(listOf("api-key", "auth.token"))
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("const val API_KEY: String = \"<unresolved:see-docs-SECRETS_INTEGRATION>\""))
        assertTrue(text.contains("const val AUTH_TOKEN: String = \"<unresolved:see-docs-SECRETS_INTEGRATION>\""))
    }

    @Test
    fun `perTarget specs emit nested object blocks grouped by target`() {
        val task = newTask()
        task.perTargetFieldSpecs.set(
            listOf(
                PerTargetFieldDeclaration("BUNDLE", "String", "\".dev\"", "iosMain"),
                PerTargetFieldDeclaration("FLAG", "Boolean", "true", "iosMain"),
                PerTargetFieldDeclaration("RES_DIR", "String", "\".jvm\"", "jvmMain"),
            ),
        )
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("object PerTarget"))
        assertTrue(text.contains("object IosMain"))
        assertTrue(text.contains("object JvmMain"))
        assertTrue(text.contains("const val BUNDLE: String = \".dev\""))
        assertTrue(text.contains("const val FLAG: Boolean = true"))
    }

    @Test
    fun `network spec emits Network object with active URL`() {
        val task = newTask()
        val spec = NetworkConfigSpec(
            baseUrls = mapOf("free" to "https://api.free.example.com", "paid" to "https://api.paid.example.com"),
            timeoutSeconds = 45,
        )
        task.networkConfigSpec.set(spec)
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("object Network"))
        assertTrue(text.contains("const val BASE_URL: String = \"https://api.free.example.com\""))
        assertTrue(text.contains("const val TIMEOUT_SECONDS: Int = 45"))
    }

    @Test
    fun `network spec emits sentinel placeholder when no active flavor matches`() {
        val task = newTask()
        // activeFlavorNames are {free, dev}; baseUrls only has "paid" key — no match.
        val spec = NetworkConfigSpec(baseUrls = mapOf("paid" to "https://paid.x"), timeoutSeconds = 10)
        task.networkConfigSpec.set(spec)
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("<no baseUrl mapped for active variant>"))
    }

    @Test
    fun `header comments include variant and active flavors`() {
        val task = newTask()
        task.generate()
        val text = File(tempDir, "com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("DO NOT EDIT"))
        assertTrue(text.contains("Active variant: freeDev"))
        assertTrue(text.contains("Active flavors:"))
    }

    @Test
    fun `DimensionEnumSpec equality and serializable`() {
        val a = DimensionEnumSpec("d", listOf("x", "y"), "x")
        val b = DimensionEnumSpec("d", listOf("x", "y"), "x")
        org.junit.jupiter.api.Assertions.assertEquals(a, b)
        org.junit.jupiter.api.Assertions.assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a is java.io.Serializable)
    }
}
