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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * W3.5 — RFC §3 Q8 build-time SLO acceptance gate.
 *
 * SLO from the RFC: matrix-mode build time must be ≤ 2× single-variant
 * build time on a 4-variant module. The RFC drafting probe (2026-05-13)
 * measured 1.01× on the 2-compilation D1 spike — comfortably under.
 *
 * This test codifies the probe so the SLO can't silently regress.
 *
 * The CI threshold is intentionally **looser than the RFC SLO** (3×
 * instead of 2×) because TestKit timings include Gradle startup +
 * configuration overhead which dominates on tiny samples, AND because
 * CI runners are slow/noisy. The RFC's 2× SLO is for a realistic 4-
 * variant module with real consumer code; this test's purpose is to
 * catch order-of-magnitude regressions (e.g., per-variant compilation
 * accidentally going O(N²) — at 5× it would catch that).
 *
 * The real-app benchmark lands in W4 as part of `samples/matrix-mode/`.
 */
class BuildTimeBenchmarkTest {

    @TempDir
    lateinit var testProjectDir: File

    private val ratioCeiling: Double = 3.0

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "build-time-benchmark"
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
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )
    }

    private fun timeRun(vararg args: String): Long {
        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(*args, "--rerun-tasks", "--no-build-cache")
            .withPluginClasspath()
        val start = System.nanoTime()
        runner.build()
        return System.nanoTime() - start
    }

    @Test
    fun `matrix-mode build time stays within the Q8 ratio ceiling`() {
        // Warm both code paths once so the comparison isn't biased by
        // first-run Gradle/Kotlin daemon spin-up.
        timeRun("compileKotlinDesktop")
        timeRun("assembleAllDesktopVariants")

        val baselineNanos = timeRun("compileKotlinDesktop")
        val matrixNanos = timeRun("assembleAllDesktopVariants")

        val baselineMs = baselineNanos / 1_000_000.0
        val matrixMs = matrixNanos / 1_000_000.0
        val ratio = matrixMs / baselineMs

        println(
            "[Q8 benchmark] baseline=${"%.0f".format(baselineMs)}ms " +
                "matrix=${"%.0f".format(matrixMs)}ms " +
                "ratio=${"%.2f".format(ratio)}× " +
                "(CI ceiling=$ratioCeiling×; RFC §3 Q8 production SLO=2×)",
        )

        assertTrue(
            ratio <= ratioCeiling,
            "Q8 build-time ratio regressed: matrix/baseline = ${"%.2f".format(ratio)}× " +
                "(CI ceiling=$ratioCeiling×, production SLO=2×). " +
                "matrix=${"%.0f".format(matrixMs)}ms, baseline=${"%.0f".format(baselineMs)}ms.",
        )
    }
}
