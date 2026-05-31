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
 * RFC §3 Q7 acceptance gate — matrix mode survives Gradle's configuration
 * cache.
 *
 * The plan's provisional answer required ≥95% cache hit rate on the
 * second invocation. This test exercises the strongest version: 100%
 * hit on the second run. If KGP or one of the plugin's internal
 * objects acquires a non-cacheable reference (e.g., raw `project`,
 * `Task.getProject()`, `afterEvaluate` quirks), this test fails.
 *
 * The probe is exactly what the RFC's drafting session ran by hand
 * during 2026-05-13:
 *   1. rm .gradle/configuration-cache (clean slate)
 *   2. ./gradlew {task} --configuration-cache --rerun-tasks  -> "stored"
 *   3. ./gradlew {task} --configuration-cache --rerun-tasks  -> "reused"
 *
 * Uses `generatePaidBuildConfig` from W2.4 because it exercises the
 * full matrix-mode chain (CompilationRegistrar + per-flavor source
 * sets + GenerateBuildConfigTasksRegistrar + lazy Provider wiring).
 */
class ConfigCacheCompatibilityTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "config-cache-test"
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
                buildConfigPackage.set("com.example.cc")
                buildConfigClassName.set("BuildKonfig")
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )
    }

    @Test
    fun `matrix mode round-trip survives configuration cache (Q7 acceptance gate)`() {
        // Run 1 — cold cache. Expect: "Configuration cache entry stored".
        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()

        val cold = runner
            .withArguments(
                "generatePaidBuildConfig",
                "--configuration-cache",
                "--rerun-tasks",
            )
            .build()
        assertTrue(
            cold.output.contains("Configuration cache entry stored"),
            "Cold run must store a configuration cache entry. Got:\n${cold.output}",
        )

        // Run 2 — warm cache. Expect: "Configuration cache entry reused" or
        // "Reusing configuration cache." (Gradle 9 phrasing). This is the
        // ≥95% hit-rate SLO; the strongest version is 100% / single command.
        val warm = runner
            .withArguments(
                "generatePaidBuildConfig",
                "--configuration-cache",
                "--rerun-tasks",
            )
            .build()
        assertTrue(
            warm.output.contains("Configuration cache entry reused") ||
                warm.output.contains("Reusing configuration cache"),
            "Warm run must reuse the configuration cache entry. Got:\n${warm.output}",
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // v2.5 Phase 4 — AC 26: configuration-cache compatibility for all new
    // Gradle tasks added in v2.5 (FrameworkSchemaCheckTask, expanded
    // GenerateBuildConfigTask with v2.5 BuildKonfig DSL inputs).
    //
    // The new task inputs all use serializable types (CustomFieldDeclaration,
    // PerTargetFieldDeclaration, DimensionEnumSpec, plain String lists). No
    // raw `project` references in @Input getters. This test pins the contract
    // by exercising a project that uses the v2.5 buildKonfig {} block with all
    // four DSL features.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `v2-5 AC 26 - buildKonfig DSL round-trip survives configuration cache`() {
        // Override the default setup with a build script that exercises the v2.5
        // buildKonfig {} block + dimensions {} sugar.
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                buildMatrix.set(true)
                generateBuildConfig.set(true)
                buildConfigPackage.set("com.example.cc25")
                buildConfigClassName.set("BuildKonfig")
                dimensions {
                    dimension("tier") {
                        flavor("free") { isDefault.set(true) }
                        flavor("paid")
                    }
                }
                buildKonfig {
                    enum("tier")
                    customField("scopes", "List<String>", "listOf(\"read\", \"write\")")
                    perTarget("desktopMain") {
                        field("DESKTOP_HOME", "String", "\"/tmp\"")
                    }
                }
            }
            kotlin { jvm("desktop") }
            """.trimIndent(),
        )

        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()

        // Cold run — populate the cache.
        val cold = runner
            .withArguments(
                "generatePaidBuildConfig",
                "--configuration-cache",
                "--rerun-tasks",
            )
            .build()
        assertTrue(
            cold.output.contains("Configuration cache entry stored"),
            "v2.5 buildKonfig {} cold run must store a configuration cache entry. Got:\n${cold.output}",
        )

        // Warm run — reuse the cache.
        val warm = runner
            .withArguments(
                "generatePaidBuildConfig",
                "--configuration-cache",
                "--rerun-tasks",
            )
            .build()
        assertTrue(
            warm.output.contains("Configuration cache entry reused") ||
                warm.output.contains("Reusing configuration cache"),
            "v2.5 buildKonfig {} warm run must reuse the configuration cache entry. Got:\n${warm.output}",
        )
    }
}
