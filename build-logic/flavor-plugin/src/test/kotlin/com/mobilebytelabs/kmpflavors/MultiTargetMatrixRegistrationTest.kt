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
 * W3.1 — RFC §3 Q4 end-to-end verification that matrix mode registers
 * per-variant compilations on every non-Android KMP target, not just JVM.
 *
 * The CompilationRegistrar is target-type-agnostic — it walks
 * `target.compilations` via the abstract [org.jetbrains.kotlin.gradle.plugin.KotlinTarget]
 * API. This test confirms that property holds for `iosSimulatorArm64`
 * (a `KotlinNativeTarget`), `js(IR)` (a `KotlinJsIrTarget`), and
 * `wasmJs()` (a `KotlinWasmJsTarget`).
 *
 * The Q4 mini-spike during RFC drafting (2026-05-13) verified iOS
 * task registration at the KGP level. This test codifies that
 * verification + extends it to JS and WasmJs.
 *
 * We only inspect the task graph via `./gradlew tasks --all` —
 * actually running the iOS native compilation would require
 * downloading the Kotlin/Native distribution (~hundreds of MB) which
 * is heavy + slow for unit testing. Compilation success on real
 * iOS code is exercised by the W4 `samples/matrix-mode/` sample app.
 */
class MultiTargetMatrixRegistrationTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "multi-target-test"
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
            kotlin {
                jvm("desktop")
                iosSimulatorArm64()
                js(IR) { browser() }
                @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                wasmJs { browser() }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `matrix mode registers per-variant compilations on JVM, iOS, JS, and WasmJs targets`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // free is the active variant -> uses each target's `main` compilation
        // (compileKotlinDesktop, compileKotlinIosSimulatorArm64, etc.)
        // paid is inactive -> matrix mode adds compile{Paid}Kotlin{Target}
        // tasks on every non-Android target.

        val expectedPerVariantTasks = listOf(
            "compilePaidKotlinDesktop",
            "compilePaidKotlinIosSimulatorArm64",
            "compilePaidKotlinJs",
            "compilePaidKotlinWasmJs",
        )
        expectedPerVariantTasks.forEach { task ->
            assertTrue(
                result.output.contains(task),
                "Expected '$task' in 'tasks --all' output (matrix-mode registers per-variant " +
                    "compilations on every non-Android target). Got:\n${result.output}",
            )
        }

        // Telemetry should mention 4 non-Android targets (excludes android JVM target,
        // which we don't declare here; and excludes synthetic metadata target).
        assertTrue(
            result.output.contains("across 4 non-Android target"),
            "Expected matrix-mode telemetry to count 4 non-Android targets " +
                "(desktop, iosSimulatorArm64, js, wasmJs):\n${result.output}",
        )
    }
}
