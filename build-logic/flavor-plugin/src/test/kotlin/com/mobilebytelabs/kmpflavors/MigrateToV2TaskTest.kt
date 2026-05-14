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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * W5.2 — RFC §3 Q26 `./gradlew kmpFlavorsMigrateToV2` migration assistant.
 *
 * Verifies the task:
 *   - Is registered on every project that applies the plugin (gated only
 *     by `flavors.isNotEmpty()`).
 *   - Emits a human-readable Markdown report by default.
 *   - Emits a single-line JSON object when `--json` is passed.
 *   - Recommends the matrix-mode opt-in when matrix mode is OFF and at
 *     least one flavor is registered.
 *   - Reports READY when matrix mode is already ON.
 *   - Is read-only: never modifies the project (no failing
 *     "modifies project" assertions are needed because the task simply
 *     prints — but we verify outcome is SUCCESS, not failures from
 *     side-effects).
 */
class MigrateToV2TaskTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "migrate-task-test"
            """.trimIndent(),
        )
    }

    private fun writeBuild(buildMatrix: Boolean) {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                generateBuildConfig.set(false)
                ${if (buildMatrix) "buildMatrix.set(true)" else ""}
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )
    }

    @Test
    fun `matrix mode OFF — task recommends the one-line opt-in`() {
        writeBuild(buildMatrix = false)

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("kmpFlavorsMigrateToV2")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":kmpFlavorsMigrateToV2")?.outcome)
        assertTrue(result.output.contains("# kmpFlavorsMigrateToV2 report"))
        assertTrue(result.output.contains("Matrix mode enabled | false"))
        assertTrue(
            result.output.contains("OPT-IN: matrix mode is OFF"),
            "Expected OPT-IN recommendation:\n${result.output}",
        )
        assertTrue(
            result.output.contains("kmpFlavors.buildMatrix=true"),
            "Expected concrete gradle.properties hint:\n${result.output}",
        )
    }

    @Test
    fun `matrix mode ON — task reports READY`() {
        writeBuild(buildMatrix = true)

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("kmpFlavorsMigrateToV2")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":kmpFlavorsMigrateToV2")?.outcome)
        assertTrue(
            result.output.contains("READY: matrix mode is ON"),
            "Expected READY status:\n${result.output}",
        )
        assertTrue(
            result.output.contains("assembleAllVariants"),
            "Expected next-step task hint:\n${result.output}",
        )
    }

    @Test
    fun `--json flag emits a single-line JSON object`() {
        writeBuild(buildMatrix = true)

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("kmpFlavorsMigrateToV2", "--json")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":kmpFlavorsMigrateToV2")?.outcome)
        val jsonLine = result.output.lines().firstOrNull { it.trim().startsWith("{") }
        assertTrue(
            jsonLine != null && jsonLine.contains("\"matrixModeEnabled\":true") &&
                jsonLine.contains("\"ready\":true") &&
                jsonLine.contains("\"flavors\":2"),
            "Expected one-line JSON with matrixModeEnabled=true, ready=true, flavors=2:\n${result.output}",
        )
    }
}
