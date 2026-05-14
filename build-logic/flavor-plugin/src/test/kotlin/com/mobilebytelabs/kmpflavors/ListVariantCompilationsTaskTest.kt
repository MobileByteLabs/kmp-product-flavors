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
 * v2.1 Phase 1 — RFC §3 Q13 verification.
 *
 * `./gradlew :module:listVariantCompilations` prints the full variant × target
 * compilation matrix as a Markdown table. The fixture below exercises a
 * 4-variant × 2-target shape so we can assert the rendered grid covers
 * every cell.
 */
class ListVariantCompilationsTaskTest {

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
            rootProject.name = "list-variant-compilations-test"
            """.trimIndent(),
        )
        // 2 dimensions × 2 flavors each = 4 variants on 2 JVM targets.
        buildFile.writeText(
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
                buildMatrix.set(true)
                generateBuildConfig.set(false)
                flavorDimensions {
                    register("tier") { priority.set(0) }
                    register("env")  { priority.set(1) }
                }
                flavors {
                    register("free") {
                        dimension.set("tier")
                        isDefault.set(true)
                    }
                    register("paid") {
                        dimension.set("tier")
                    }
                    register("dev") {
                        dimension.set("env")
                        isDefault.set(true)
                    }
                    register("prod") {
                        dimension.set("env")
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `listVariantCompilations renders a Markdown table with 4 variant rows and 2 target columns`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("listVariantCompilations")
            .withPluginClasspath()
            .build()

        // Header line announces the matrix shape.
        assertTrue(
            result.output.contains("KMPF listVariantCompilations: 4 variant(s) × 2 target(s)"),
            "Expected matrix-shape header line:\n${result.output}",
        )

        // Each variant appears in its own row.
        listOf("freeDev", "freeProd", "paidDev", "paidProd").forEach { variant ->
            assertTrue(
                result.output.contains(variant),
                "Expected '$variant' row in the table:\n${result.output}",
            )
        }
        // Both target columns present in the header.
        assertTrue(result.output.contains("desktop"))
        assertTrue(result.output.contains("server"))

        // Active variant (freeDev) tagged ACTIVE; one inactive variant tagged inactive.
        assertTrue(
            result.output.contains("ACTIVE"),
            "Expected ACTIVE status for the active variant:\n${result.output}",
        )
        assertTrue(
            result.output.contains("inactive"),
            "Expected at least one 'inactive' status row:\n${result.output}",
        )

        // Active variant compiles through `main` on each target.
        assertTrue(
            result.output.contains("main"),
            "Expected 'main' compilation cell for the active variant:\n${result.output}",
        )
        // Inactive variants compile through a variant-named compilation on each target.
        assertTrue(
            result.output.contains("paidDev") || result.output.contains("paidProd"),
            "Expected per-variant compilation name cells:\n${result.output}",
        )
    }

    @Test
    fun `listVariantCompilations prints empty-state message when matrix mode is off and no targets configured`() {
        // Drop the matrix-mode opt-in AND drop all KMP targets.
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kmpFlavors {
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // The task should NOT register on a non-KMP module because configurePlugin
        // returns early when `kotlin.multiplatform` isn't applied.
        assertTrue(
            !result.output.contains("listVariantCompilations"),
            "listVariantCompilations should not register on a JVM-only project:\n${result.output}",
        )
    }
}
