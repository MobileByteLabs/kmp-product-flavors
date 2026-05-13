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
 * W1.3 — end-to-end TestKit verification that the v2.0 matrix-mode hook
 * in `KmpFlavorPlugin.apply()` actually registers per-variant compilations
 * on a real KMP JVM target when `buildMatrix` is opted in, AND that the
 * v1.x active-variant-only behaviour is preserved when matrix mode is off.
 *
 * RFC §3 Q1-B: KGP names the per-variant tasks `compile{Variant}Kotlin{Target}`
 * automatically when `compilations.create(variantName)` is called.
 *
 * RFC §1.1 Zero-Touch Adoption: the build files in these tests are
 * byte-identical between the on and off paths EXCEPT for the single
 * `buildMatrix.set(true)` line in the convention area — no per-module DSL.
 */
class MatrixModeJvmRegistrationTest {

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
            rootProject.name = "matrix-mode-test"
            """.trimIndent(),
        )
    }

    private fun writeBuildFile(matrixModeLine: String) {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin { jvm("desktop") }
            kmpFlavors {
                generateBuildConfig.set(false)
                $matrixModeLine
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `matrix mode ON registers per-variant compilation tasks on the JVM target`() {
        writeBuildFile(matrixModeLine = "buildMatrix.set(true)")

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // KGP emits compile{Variant}Kotlin{Target} for each compilation.create(variantName)
        assertTrue(
            result.output.contains("compileFreeKotlinDesktop"),
            "Expected compileFreeKotlinDesktop task in output:\n${result.output}",
        )
        assertTrue(
            result.output.contains("compilePaidKotlinDesktop"),
            "Expected compilePaidKotlinDesktop task in output:\n${result.output}",
        )
        // Telemetry line from KmpFlavorPlugin (RFC §3 Q13)
        assertTrue(
            result.output.contains("Matrix mode: registered"),
            "Expected matrix-mode lifecycle log in output:\n${result.output}",
        )
    }

    @Test
    fun `matrix mode OFF (default) does NOT register per-variant tasks`() {
        writeBuildFile(matrixModeLine = "") // no buildMatrix opt-in

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertFalse(
            result.output.contains("compileFreeKotlinDesktop"),
            "compileFreeKotlinDesktop must NOT be present when matrix mode is off (v1.x preserved):\n${result.output}",
        )
        assertFalse(
            result.output.contains("compilePaidKotlinDesktop"),
            "compilePaidKotlinDesktop must NOT be present when matrix mode is off:\n${result.output}",
        )
        assertFalse(
            result.output.contains("Matrix mode: registered"),
            "Matrix-mode telemetry must NOT fire when opt-in is absent:\n${result.output}",
        )
    }

    @Test
    fun `matrix mode opt-in via gradle property kmpFlavors_buildMatrix=true registers per-variant tasks`() {
        writeBuildFile(matrixModeLine = "") // no extension opt-in

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all", "-PkmpFlavors.buildMatrix=true")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("compileFreeKotlinDesktop"),
            "gradle property opt-in must register tasks (RFC §3 Q16-C hybrid):\n${result.output}",
        )
        assertTrue(result.output.contains("compilePaidKotlinDesktop"))
    }
}
