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
 * W3.4 — RFC §3 Q20-A AGP-style `variantFilter { setIgnore(true) }`.
 *
 * Verifies the canonical example from the RFC works end-to-end:
 *
 * ```kotlin
 * kmpFlavors {
 *     variantFilter {
 *         if (flavors.contains("paid") && buildType == "staging") {
 *             setIgnore(true)
 *         }
 *     }
 * }
 * ```
 *
 * Filtered variants must:
 *   1. Not appear as a per-variant compilation task in the task graph.
 *   2. Not appear in the active-variant resolution (already covered by
 *      v1.x `exclude()` semantics; this test re-verifies via the
 *      AGP-shaped DSL).
 */
class VariantFilterDslTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "variant-filter-dsl-test"
            """.trimIndent(),
        )
    }

    @Test
    fun `setIgnore(true) filters the matching variant out of matrix mode`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                buildMatrix.set(true)
                generateBuildConfig.set(false)
                enableBuildTypes.set(true)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
                buildTypes {
                    register("debug") { isDefault.set(true) }
                    register("staging")
                }
                variantFilter {
                    if (flavors.any { it.name == "paid" } && buildType == "staging") {
                        setIgnore(true)
                    }
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // Active variant is freeDebug (both isDefault set), inactives are:
        //   freeStaging, paidDebug
        // paidStaging would also be inactive, but the filter excludes it.
        assertTrue(
            result.output.contains("compileFreeStagingKotlinDesktop"),
            "freeStaging should compile (not filtered):\n${result.output}",
        )
        assertTrue(
            result.output.contains("compilePaidDebugKotlinDesktop"),
            "paidDebug should compile (not filtered):\n${result.output}",
        )
        assertFalse(
            result.output.contains("compilePaidStagingKotlinDesktop"),
            "paidStaging MUST be filtered out by `variantFilter { setIgnore(true) }`:\n${result.output}",
        )

        // Telemetry: matrix mode counts 2 inactive variants (freeStaging + paidDebug),
        // NOT 3, because paidStaging was filtered.
        assertTrue(
            result.output.contains("registered 2 inactive-variant compilations"),
            "Expected exactly 2 inactive-variant compilations after filter:\n${result.output}",
        )
    }

    @Test
    fun `exclude() (v1_x synonym) still works`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                buildMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
                variantFilter {
                    if (variantName == "paid") exclude()
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertFalse(
            result.output.contains("compilePaidKotlinDesktop"),
            "paid variant must be filtered out by exclude():\n${result.output}",
        )
        assertTrue(
            result.output.contains("registered 0 inactive-variant"),
            "Expected zero inactive variants after exclude():\n${result.output}",
        )
    }
}
