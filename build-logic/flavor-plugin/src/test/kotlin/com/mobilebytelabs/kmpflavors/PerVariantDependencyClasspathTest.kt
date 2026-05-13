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
 * RFC §3 Q17 acceptance — per-variant dependencies resolve through the
 * KMP source-set hierarchy.
 *
 * In matrix mode, a dependency declared only on `commonPaid` must:
 *   1. Be visible to the paid variant's compilation
 *      (`compilePaidKotlinDesktop`).
 *   2. NOT be visible to the active variant's compilation
 *      (`compileKotlinDesktop` for free here).
 *
 * The mechanism: `variant.defaultSourceSet.dependsOn(commonPaid)` plus
 * `commonPaid.dependsOn(commonMain)` (both wired by W2.2). KGP propagates
 * dependencies along the dependsOn chain into the variant's compileClasspath
 * configuration.
 *
 * Uses kotlinx-coroutines-core because it's small, widely cached on
 * Maven Central, and provides recognizable symbols (`kotlinx.coroutines.delay`).
 */
class PerVariantDependencyClasspathTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "per-variant-deps-test"
            """.trimIndent(),
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            // kmpFlavors block FIRST — registers flavors so the plugin eagerly
            // creates the `commonFree` / `commonPaid` source sets before the
            // `kotlin { sourceSets { ... } }` block below references them.
            kmpFlavors {
                generateBuildConfig.set(false)
                buildMatrix.set(true)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }

            kotlin {
                jvm("desktop")
                sourceSets {
                    val commonPaid by getting {
                        dependencies {
                            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                        }
                    }
                }
            }
            """.trimIndent(),
        )
    }

    private fun writePaidCode() {
        File(testProjectDir, "src/commonPaid/kotlin").apply { mkdirs() }
            .let { File(it, "PaidThing.kt") }
            .writeText(
                """
                package com.example.deps

                import kotlinx.coroutines.delay

                suspend fun paidThing() = delay(1)
                """.trimIndent(),
            )
    }

    @Test
    fun `inactive variant sees per-flavor dependency declared on its commonFlavor source set`() {
        writePaidCode()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compilePaidKotlinDesktop", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":compilePaidKotlinDesktop")?.outcome,
            "paid variant must resolve kotlinx-coroutines-core via commonPaid.dependsOn(commonMain):\n${result.output}",
        )
    }

    @Test
    fun `active variant compilation does NOT see paid's per-flavor dependency`() {
        // Same project but leak the paid-only API into the free path.
        File(testProjectDir, "src/commonFree/kotlin").apply { mkdirs() }
            .let { File(it, "FreeThing.kt") }
            .writeText(
                """
                package com.example.deps

                import kotlinx.coroutines.delay   // declared only on commonPaid; this must fail

                suspend fun freeThing() = delay(1)
                """.trimIndent(),
            )
        writePaidCode()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compileKotlinDesktop", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(
            result.output.contains("Unresolved reference") &&
                result.output.contains("kotlinx") ||
                result.output.contains("delay"),
            "Expected commonFree → unresolved kotlinx.coroutines.delay because the dep was declared only on commonPaid:\n${result.output}",
        )
    }
}
