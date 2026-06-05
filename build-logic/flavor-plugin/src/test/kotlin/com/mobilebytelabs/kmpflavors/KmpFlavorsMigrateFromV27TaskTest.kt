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

package com.mobilebytelabs.kmpflavors

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.8 Wave B2 — TestKit smoke tests for [com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsMigrateFromV27Task].
 *
 * Each test copies the v2.7-shaped `before/` fixture into a fresh temp project dir,
 * applies the plugin via TestKit (withPluginClasspath), runs the task, and asserts post-state.
 */
class KmpFlavorsMigrateFromV27TaskTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        copyFixtureDir("v27-migration-fixtures/before", projectDir)

        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); google() }
            }
            rootProject.name = "migrate-test"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors { }
            """.trimIndent(),
        )
    }

    @Test
    fun `dry-run prints diff but does not mutate files`() {
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("kmpFlavorsMigrateFromV27")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":kmpFlavorsMigrateFromV27")?.outcome)
        assertTrue(
            result.output.contains("DRY-RUN"),
            "Expected DRY-RUN in output:\n${result.output}",
        )
        assertTrue(
            File(projectDir, "cmp-android/src/androidMain/kotlin/AppFlavor.kt").exists(),
            "AppFlavor.kt should NOT be deleted in dry-run",
        )
        assertTrue(
            File(projectDir, "build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt")
                .readText().contains("fun configureFlavors("),
            "KMPFlavorsConventionPlugin.kt should NOT be rewritten in dry-run",
        )
    }

    @Test
    fun `apply deletes AppFlavor_kt and rewrites KMPFlavorsConventionPlugin`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("kmpFlavorsMigrateFromV27", "--apply")
            .withPluginClasspath()
            .build()

        assertFalse(
            File(projectDir, "cmp-android/src/androidMain/kotlin/AppFlavor.kt").exists(),
            "AppFlavor.kt must be deleted after --apply",
        )

        val conventionFile = File(
            projectDir,
            "build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt",
        )
        assertFalse(
            conventionFile.readText().contains("fun configureFlavors("),
            "configureFlavors function must be removed after --apply",
        )
        assertFalse(
            conventionFile.readText().contains("v2.7"),
            "v2.7 comment must be removed after --apply",
        )
    }

    @Test
    fun `no-op when project already has no v2_7 telltales`() {
        File(projectDir, "cmp-android/src/androidMain/kotlin/AppFlavor.kt").delete()
        val conventionFile = File(
            projectDir,
            "build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt",
        )
        conventionFile.writeText(loadFixture("v27-migration-fixtures/after/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt"))

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("kmpFlavorsMigrateFromV27")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":kmpFlavorsMigrateFromV27")?.outcome)
        assertTrue(
            result.output.contains("No v2.7 telltales detected"),
            "Expected no-op message:\n${result.output}",
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun copyFixtureDir(resourcePath: String, targetDir: File) {
        val resourceUrl = javaClass.classLoader.getResource(resourcePath)
            ?: error("Fixture directory not found: $resourcePath")
        val sourceDir = File(resourceUrl.toURI())
        sourceDir.walkTopDown().forEach { src ->
            val relative = src.relativeTo(sourceDir)
            val dest = File(targetDir, relative.path)
            if (src.isDirectory) dest.mkdirs() else src.copyTo(dest, overwrite = true)
        }
    }

    private fun loadFixture(resourcePath: String): String = javaClass.classLoader.getResourceAsStream(resourcePath)
        ?.bufferedReader()?.readText()
        ?: error("Fixture not found: $resourcePath")
}
