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
 * W3.3 — RFC §3 Q18-C aggregate variant tasks.
 *
 * Tasks shipped:
 *   - `assembleAll{Target}Variants` per non-Android target — depends on
 *     the target's `main` compilation (active variant) + each
 *     inactive variant's per-variant compilation. CI matrix jobs
 *     shard by target via these.
 *   - `assembleAllVariants` — super-aggregate walking every per-target
 *     aggregate. Developer-convenience entry point.
 *
 * Both live in the `kmpFlavors variants` task group per Q9 ergonomics.
 */
class AggregateVariantTasksTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "aggregate-tasks-test"
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

    private val nonFailedOutcomes = setOf(
        TaskOutcome.SUCCESS,
        TaskOutcome.UP_TO_DATE,
        TaskOutcome.NO_SOURCE,
        TaskOutcome.FROM_CACHE,
    )

    @Test
    fun `per-target aggregate runs both active main and inactive per-variant compilations`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("assembleAllDesktopVariants", "--stacktrace")
            .withPluginClasspath()
            .build()

        // Aggregate is a no-input/output DefaultTask, so UP_TO_DATE is the
        // expected outcome — anything except FAILED / SKIPPED counts as success.
        assertTrue(
            result.task(":assembleAllDesktopVariants")?.outcome in nonFailedOutcomes,
            "Aggregate must complete without failure:\n${result.output}",
        )
        val active = result.task(":compileKotlinDesktop")
        val paid = result.task(":compilePaidKotlinDesktop")
        assertTrue(
            active != null && active.outcome != TaskOutcome.SKIPPED,
            "compileKotlinDesktop must be in the task graph:\n${result.output}",
        )
        assertTrue(
            paid != null && paid.outcome != TaskOutcome.SKIPPED,
            "compilePaidKotlinDesktop must be in the task graph:\n${result.output}",
        )
    }

    @Test
    fun `super-aggregate assembleAllVariants delegates to per-target aggregates`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("assembleAllVariants", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(result.task(":assembleAllVariants")?.outcome in nonFailedOutcomes)
        assertTrue(result.task(":assembleAllDesktopVariants")?.outcome in nonFailedOutcomes)
    }

    @Test
    fun `aggregate tasks live in the kmpFlavors variants task group`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all", "--group=kmpFlavors variants")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("assembleAllDesktopVariants"),
            "Expected assembleAllDesktopVariants under 'kmpFlavors variants' group:\n${result.output}",
        )
        assertTrue(
            result.output.contains("assembleAllVariants"),
            "Expected assembleAllVariants under 'kmpFlavors variants' group:\n${result.output}",
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // v2.5 — AC 12: aggregate tasks auto-register for the new target families
    // (wasmJs / watchOS / tvOS / linuxX64 / mingwX64).
    //
    // The aggregate-task naming logic in AggregateTasksRegistrar.kt is
    // `assembleAll${target.targetCap}Variants` — a pure function over the
    // detected target list. Since PlatformDetector has registered all 9 new
    // targets since v1.1.0, the aggregate names are derived automatically.
    //
    // This test pins the contract via a TestKit project that declares
    // linuxX64() (no native toolchain needed for configuration-time task
    // listing) and asserts the expected aggregate task is registered.
    //
    // watchOS / tvOS aggregate task registration is verified end-to-end in
    // samples/multi-target-multi-variant via sample-target-coverage.yml CI.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-5 AC 12 - linuxX64 target registers assembleAllLinuxX64Variants aggregate`() {
        // Override the default setup() with a build script that declares linuxX64().
        // KGP configures native targets at config time without requiring the toolchain;
        // compilation needs the toolchain, but `tasks --all` doesn't.
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
                linuxX64()
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all", "--group=kmpFlavors variants")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("assembleAllLinuxX64Variants"),
            "Expected assembleAllLinuxX64Variants under 'kmpFlavors variants' group:\n${result.output}",
        )
        assertTrue(
            result.output.contains("assembleAllDesktopVariants"),
            "Expected assembleAllDesktopVariants to coexist with linuxX64 aggregate:\n${result.output}",
        )
    }

    @Test
    fun `v2-5 AC 12 - mingwX64 target registers assembleAllMingwX64Variants aggregate`() {
        // Same pattern as linuxX64 — mingwX64 is the Windows-native sibling.
        // Native target configuration doesn't require the toolchain.
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
                mingwX64()
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all", "--group=kmpFlavors variants")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("assembleAllMingwX64Variants"),
            "Expected assembleAllMingwX64Variants under 'kmpFlavors variants' group:\n${result.output}",
        )
    }

    @Test
    fun `v2-5 AC 12 - wasmJs target registers assembleAllWasmJsVariants aggregate`() {
        // wasmJs is detected as a 'web' target family; aggregate name follows the same
        // pattern as iOS/Desktop targets. Test pins the wasmJs path through the registrar.
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
                @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                wasmJs { browser() }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all", "--group=kmpFlavors variants")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("assembleAllWasmJsVariants"),
            "Expected assembleAllWasmJsVariants under 'kmpFlavors variants' group:\n${result.output}",
        )
    }
}
