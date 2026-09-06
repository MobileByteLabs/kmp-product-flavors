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
 * v2.1 Phase 1 — RFC §3 Q22 verification.
 *
 * `./gradlew :module:diagnoseVariant --variant freeDev` prints the resolved
 * source-set tree, target list, BuildConfig fields, and active filter count
 * for ONE variant. JSON output via `--json` for CI consumption.
 */
class DiagnoseVariantTaskTest {

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
            rootProject.name = "diagnose-variant-test"
            """.trimIndent(),
        )
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin { jvm("desktop") }
            kmpFlavors {
                buildMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") {
                        isDefault.set(true)
                        buildConfigField("Boolean", "IS_PREMIUM", "false")
                    }
                    register("paid") {
                        buildConfigField("Boolean", "IS_PREMIUM", "true")
                        buildConfigField("String", "TIER", "\"gold\"")
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `diagnoseVariant prints the variant's source-set tree and BuildConfig fields by default human-readable`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("diagnoseVariant", "--variant", "paid")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("KMPF diagnoseVariant: paid"),
            "Expected header line for variant 'paid':\n${result.output}",
        )
        assertTrue(
            result.output.contains("Flavors           : paid"),
            "Expected the variant's flavor list:\n${result.output}",
        )
        assertTrue(
            result.output.contains("Targets           : desktop"),
            "Expected desktop target listed:\n${result.output}",
        )
        // Source-set closure must include commonPaid (the per-flavor source set v1.x SourceSetConfigurator
        // creates + matrix mode wires inactive variants to depend on).
        assertTrue(
            result.output.contains("commonPaid"),
            "Expected commonPaid in the source-set tree:\n${result.output}",
        )
        // BuildConfig fields rendered as "name : type::value"
        assertTrue(
            result.output.contains("IS_PREMIUM"),
            "Expected IS_PREMIUM in BuildConfig fields:\n${result.output}",
        )
        assertTrue(
            result.output.contains("TIER"),
            "Expected TIER in BuildConfig fields:\n${result.output}",
        )
    }

    @Test
    fun `diagnoseVariant with --variant pointing at the active variant tags it ACTIVE`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("diagnoseVariant", "--variant", "free")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("KMPF diagnoseVariant: free (ACTIVE)"),
            "Expected ACTIVE tag on the active variant header:\n${result.output}",
        )
    }

    @Test
    fun `diagnoseVariant without --variant defaults to the active variant`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("diagnoseVariant")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("KMPF diagnoseVariant: free (ACTIVE)"),
            "Expected the active variant when --variant is omitted:\n${result.output}",
        )
    }

    @Test
    fun `diagnoseVariant --json emits a parseable JSON object for the variant`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("diagnoseVariant", "--variant", "paid", "--json")
            .withPluginClasspath()
            .build()

        // We don't pull in a JSON dependency; sanity-check structural keys + variant echo.
        val jsonLine = result.output.lines().firstOrNull { it.trimStart().startsWith("{") && it.contains("\"variant\":\"paid\"") }
        assertTrue(
            jsonLine != null,
            "Expected a JSON object echoing 'variant' key for 'paid':\n${result.output}",
        )
        val line = jsonLine!!
        assertTrue(line.contains("\"flavors\":[\"paid\"]"))
        assertTrue(line.contains("\"active\":false"))
        assertTrue(line.contains("\"targets\":[\"desktop\"]"))
        // v2.9: a matrix variant compiles through its OWN `{variant}VariantMain` source set,
        // which carries `src/commonPaid/` as a srcDir. Variants no longer dependsOn the
        // shared `commonPaid` NODE — that is what produced KGP's "Invalid Source Set
        // Dependency Across Trees" warning, since one node then sat in several trees.
        assertTrue(line.contains("\"sourceSets\":["))
        assertTrue(
            line.contains("paidVariantMain") || line.contains("commonPaid"),
            "Expected the variant's own source set in the diagnostic:\n$line",
        )
        assertTrue(line.contains("\"buildConfigFields\":{") && line.contains("IS_PREMIUM"))
    }

    @Test
    fun `diagnoseVariant fails fast when --variant names an unknown variant`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("diagnoseVariant", "--variant", "ghost")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(
            result.output.contains("Unknown variant 'ghost'"),
            "Expected unknown-variant error in output:\n${result.output}",
        )
        assertTrue(
            result.output.contains("Registered variants: [") && result.output.contains("free") && result.output.contains("paid"),
            "Expected error to list registered variants:\n${result.output}",
        )
    }
}
