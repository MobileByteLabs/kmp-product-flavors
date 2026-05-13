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
 * RFC §3 Q3-A acceptance — one GenerateBuildConfigTask per variant in
 * matrix mode (W2.4).
 *
 * The active variant continues to use v1.x's single
 * `generateFlavorBuildConfig` task. Inactive variants each get their
 * own `generate{Variant}BuildConfig` task whose output lives under
 * `build/generated/kmpFlavors/{variantName}/kotlin/`.
 *
 * Verifies that:
 *   1. The per-variant tasks are registered with the expected names.
 *   2. Running the task produces a `BuildKonfig.kt` with the
 *      variant-specific `buildConfigField` values (different per variant).
 *   3. Task is in the `kmpFlavors variants` task group (RFC §3 Q9 ergonomics).
 */
class PerVariantBuildConfigTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "per-variant-buildconfig-test"
            """.trimIndent(),
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                buildMatrix.set(true)
                generateBuildConfig.set(true)
                buildConfigPackage.set("com.example.test")
                buildConfigClassName.set("BuildKonfig")
                flavors {
                    register("free") {
                        isDefault.set(true)
                        buildConfigField("Boolean", "IS_PREMIUM", "false")
                        buildConfigField("Int", "MAX_ITEMS", "10")
                    }
                    register("paid") {
                        buildConfigField("Boolean", "IS_PREMIUM", "true")
                        buildConfigField("Int", "MAX_ITEMS", "1000")
                    }
                }
            }
            kotlin {
                jvm("desktop")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `inactive variant's generate task is registered with expected name and group`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all", "--group=kmpFlavors variants")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("generatePaidBuildConfig"),
            "Expected generatePaidBuildConfig task under 'kmpFlavors variants' group:\n${result.output}",
        )
    }

    @Test
    fun `generatePaidBuildConfig produces a BuildKonfig with the paid variant fields`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generatePaidBuildConfig", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":generatePaidBuildConfig")?.outcome,
            "generatePaidBuildConfig must succeed:\n${result.output}",
        )

        val generated = File(
            testProjectDir,
            "build/generated/kmpFlavors/paid/kotlin/com/example/test/BuildKonfig.kt",
        )
        assertTrue(generated.exists(), "Expected generated file at: ${generated.path}")
        val content = generated.readText()
        assertTrue(content.contains("VARIANT_NAME"), "Expected VARIANT_NAME constant:\n$content")
        assertTrue(content.contains("\"paid\""), "Expected VARIANT_NAME = \"paid\":\n$content")
        assertTrue(content.contains("IS_PREMIUM"), "Expected IS_PREMIUM field:\n$content")
        assertTrue(content.contains("MAX_ITEMS"), "Expected MAX_ITEMS field:\n$content")
        // IS_PREMIUM should be true for paid variant (consumer set buildConfigField "Boolean", "IS_PREMIUM", "true")
        assertTrue(
            content.contains("IS_PREMIUM: Boolean = true"),
            "Expected IS_PREMIUM=true in paid variant:\n$content",
        )
        assertTrue(
            content.contains("MAX_ITEMS: Int = 1000"),
            "Expected MAX_ITEMS=1000 in paid variant:\n$content",
        )
    }

    @Test
    fun `active variant continues to use the v1_x generateFlavorBuildConfig task`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateFlavorBuildConfig", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":generateFlavorBuildConfig")?.outcome,
            "Active variant codegen must run via v1.x task:\n${result.output}",
        )

        val activeGenerated = File(
            testProjectDir,
            "build/generated/kmpFlavors/commonMain/kotlin/com/example/test/BuildKonfig.kt",
        )
        assertTrue(activeGenerated.exists(), "Expected active-variant file at: ${activeGenerated.path}")
        val content = activeGenerated.readText()
        // Active variant is `free` — IS_PREMIUM=false, MAX_ITEMS=10
        assertTrue(
            content.contains("IS_PREMIUM: Boolean = false"),
            "Expected IS_PREMIUM=false in free (active) variant:\n$content",
        )
    }
}
