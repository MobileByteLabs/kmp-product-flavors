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

    private val NON_FAILED_OUTCOMES = setOf(
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
            result.task(":assembleAllDesktopVariants")?.outcome in NON_FAILED_OUTCOMES,
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

        assertTrue(result.task(":assembleAllVariants")?.outcome in NON_FAILED_OUTCOMES)
        assertTrue(result.task(":assembleAllDesktopVariants")?.outcome in NON_FAILED_OUTCOMES)
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
}
