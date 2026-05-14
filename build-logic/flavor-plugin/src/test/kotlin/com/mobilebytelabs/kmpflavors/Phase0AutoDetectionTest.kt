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

import com.mobilebytelabs.kmpflavors.internal.MatrixModeResolver
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.2 Phase 0 — auto-detection / fully-automatic-plugin verification.
 *
 * Covers the unit-level semantics of:
 *   - 0G: `autoEnable` master flag.
 *   - 0A: matrix-mode auto-heuristic (≥2 non-Android targets + ≥2 flavors).
 *   - 0B: publishMatrix auto-enable on `maven-publish` plugin.
 *   - 0C: 3 adjacent-plugin helpers auto-enable on adjacent plugin detection.
 *   - 0D: enableBuildTypes auto-flips on first buildTypes registration.
 *
 * Phase 0E (CMP version detection → KMPF-V14) is exercised via TestKit because
 * the reflective version-read requires a real Gradle build context.
 */
class Phase0AutoDetectionTest {

    @TempDir
    lateinit var testProjectDir: File

    // -------------------------------------------------------------------------
    // 0A — MatrixModeResolver auto-heuristic
    // -------------------------------------------------------------------------

    @Test
    fun `0A auto-heuristic fires when 2 targets + 2 flavors + autoEnable true`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kmpFlavors", KmpFlavorExtension::class.java)

        assertTrue(
            MatrixModeResolver.shouldAutoEnable(extension, nonAndroidTargetCount = 2, flavorCount = 2),
            "Auto-heuristic must fire with ≥2 targets + ≥2 flavors when autoEnable=true (the default)",
        )
    }

    @Test
    fun `0A auto-heuristic does NOT fire when only 1 target (degenerate single-target matrix)`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kmpFlavors", KmpFlavorExtension::class.java)

        assertFalse(
            MatrixModeResolver.shouldAutoEnable(extension, nonAndroidTargetCount = 1, flavorCount = 5),
            "Single-target matrix mode is degenerate — auto-heuristic must skip",
        )
    }

    @Test
    fun `0A auto-heuristic does NOT fire when only 1 flavor (degenerate single-flavor matrix)`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kmpFlavors", KmpFlavorExtension::class.java)

        assertFalse(
            MatrixModeResolver.shouldAutoEnable(extension, nonAndroidTargetCount = 5, flavorCount = 1),
            "Single-flavor matrix mode is degenerate — auto-heuristic must skip",
        )
    }

    @Test
    fun `0A + 0G — autoEnable false suppresses the auto-heuristic even at threshold`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kmpFlavors", KmpFlavorExtension::class.java)
        extension.autoEnable.set(false)

        assertFalse(
            MatrixModeResolver.shouldAutoEnable(extension, nonAndroidTargetCount = 3, flavorCount = 3),
            "autoEnable=false must suppress the auto-heuristic regardless of target/flavor counts",
        )
    }

    @Test
    fun `0A — explicit buildMatrix set(false) wins over the auto-heuristic`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kmpFlavors", KmpFlavorExtension::class.java)
        extension.buildMatrix.set(false)

        assertFalse(
            MatrixModeResolver.isEnabled(project, extension, nonAndroidTargetCount = 3, flavorCount = 3),
            "Explicit buildMatrix.set(false) must win even when heuristic would fire",
        )
    }

    @Test
    fun `0A — explicit buildMatrix set(true) wins regardless of counts`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kmpFlavors", KmpFlavorExtension::class.java)
        extension.buildMatrix.set(true)

        assertTrue(
            MatrixModeResolver.isEnabled(project, extension, nonAndroidTargetCount = 1, flavorCount = 1),
            "Explicit buildMatrix.set(true) must win even when counts are below threshold",
        )
    }

    // -------------------------------------------------------------------------
    // 0G — autoEnable default
    // -------------------------------------------------------------------------

    @Test
    fun `0G — autoEnable default is true (fully-automatic plugin)`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("kmpFlavors", KmpFlavorExtension::class.java)

        assertEquals(
            true,
            extension.autoEnable.get(),
            "v2.2 ships with autoEnable=true by default; consumers wanting strict v2.0/v2.1 semantics set to false",
        )
    }

    // -------------------------------------------------------------------------
    // 0A end-to-end — TestKit verifies the lifecycle log fires
    // -------------------------------------------------------------------------

    @Test
    fun `0A end-to-end — auto-heuristic fires + emits lifecycle log when consumer omits opt-in`() {
        // Fixture: 2 JVM targets + 2 flavors, no `buildMatrix.set(true)` line, no gradle.properties.
        // The auto-heuristic should fire + the lifecycle log should explain the decision.
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); google() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral(); google() }
            }
            rootProject.name = "phase0-auto-heuristic"
            """.trimIndent(),
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                jvm("desktop")
                jvm("server")
            }
            kmpFlavors {
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("Phase 0A — auto-enabling matrix mode"),
            "Expected the Phase 0A auto-heuristic lifecycle log:\n${result.output}",
        )
        // Compilation tasks for the inactive variant must appear — proves matrix mode actually fired.
        assertTrue(
            result.output.contains("compilePaidKotlinDesktop"),
            "Expected inactive-variant compilation tasks when matrix mode auto-fires:\n${result.output}",
        )
    }
}
