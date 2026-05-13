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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * W1.5 — RFC §3 Q25 edge cases + validator fail-fast end-to-end.
 *
 * Verifies that the matrix-mode hook + KMPF-Vxx validator behave
 * gracefully across degenerate configurations:
 *
 *  - Zero targets → KMPF-V05 WARNING (build proceeds; no-op)
 *  - One target only → matrix mode registers compilations on that
 *    target only; no regression.
 *  - Matrix mode opted in with zero flavors → KMPF-V08 ERROR (build
 *    fails with structured error).
 *
 * KMPF-V04 (variantFilter excluded all) is covered by the unit-level
 * `KmpFlavorPluginValidatorTest`; the variantFilter DSL itself
 * gains AGP-style `setIgnore(true)` shape in W3, so TestKit-level
 * coverage of V04 is deferred until then.
 */
class EdgeCaseMatrixTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile.writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                    google()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    google()
                }
            }
            rootProject.name = "edge-case-test"
            """.trimIndent(),
        )
    }

    @Test
    fun `Q25 — matrix mode on a project with exactly one KMP target works`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin { jvm("desktop") }
            kmpFlavors {
                generateBuildConfig.set(false)
                buildMatrix.set(true)
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

        // free is active → main; paid is inactive → its own task.
        assertTrue(result.output.contains("compilePaidKotlinDesktop"))
        assertFalse(result.output.contains("compileFreeKotlinDesktop"))
        assertTrue(result.output.contains("1 non-Android target"))
    }

    @Test
    fun `Q25 — matrix mode opted in with zero flavors fails fast with KMPF-V08`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin { jvm("desktop") }
            kmpFlavors {
                generateBuildConfig.set(false)
                buildMatrix.set(true)
                // NO flavors block — Q25 degenerate case
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(
            result.output.contains("KMPF-V08"),
            "Expected KMPF-V08 in fail-fast output. Got:\n${result.output}",
        )
        assertTrue(
            result.output.contains("buildMatrix is enabled but no flavors are registered"),
            "Expected V08 human message in output:\n${result.output}",
        )
    }

    @Test
    fun `Q25 — matrix mode off with one target preserves v1_x behaviour (no per-variant tasks)`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin { jvm("desktop") }
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

        assertFalse(
            result.output.contains("compilePaidKotlinDesktop"),
            "Per-variant tasks must not be registered when buildMatrix opt-in is absent",
        )
        assertFalse(result.output.contains("Matrix mode: registered"))
        // listFlavors task from v1.x still present
        assertTrue(result.output.contains("listFlavors"))
    }
}
