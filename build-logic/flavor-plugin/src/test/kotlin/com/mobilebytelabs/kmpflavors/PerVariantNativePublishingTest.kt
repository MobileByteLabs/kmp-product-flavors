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
 * v2.1 Phase 5 — TestKit smoke verification for per-variant native publishing
 * (iOS klib + JS-family classifier-tagged Maven publications).
 *
 * The tests verify task / publication REGISTRATION only. They do NOT invoke
 * `xcodebuild`, `npm publish`, or the real classifier-tagged artifact build —
 * those require platform-specific runners (Apple SDK for iOS, npm CLI for JS)
 * and consumer-side credentials, both out of scope for the plugin's TestKit.
 */
class PerVariantNativePublishingTest {

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
            rootProject.name = "per-variant-native-publishing"
            """.trimIndent(),
        )
    }

    @Test
    fun `iOS configurator registers per-variant Zip tasks and MavenPublications for each (variant x iOS target) pair`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                `maven-publish`
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                iosX64()
                iosArm64()
                iosSimulatorArm64()
            }
            kmpFlavors {
                buildMatrix.set(true)
                publishMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // Inactive variant "paid" × 3 iOS targets → 3 Zip tasks + 3 publications.
        listOf("IosX64", "IosArm64", "IosSimulatorArm64").forEach { targetCap ->
            assertTrue(
                result.output.contains("zipPaidKotlin$targetCap"),
                "Expected zipPaidKotlin$targetCap registered:\n${result.output}",
            )
            assertTrue(
                result.output.contains("publishVariantPaid${targetCap}IosPublicationToMavenLocal"),
                "Expected publishVariantPaid${targetCap}IosPublicationToMavenLocal task:\n${result.output}",
            )
        }
        assertTrue(
            result.output.contains("publishMatrix iOS: registered"),
            "Expected per-variant iOS publishing telemetry:\n${result.output}",
        )
    }

    @Test
    fun `JS-family configurator registers per-variant tasks and publications for js(IR) target`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                `maven-publish`
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                js(IR) { nodejs() }
            }
            kmpFlavors {
                buildMatrix.set(true)
                publishMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("zipPaidKotlinJs"),
            "Expected zipPaidKotlinJs registered for js(IR) target:\n${result.output}",
        )
        assertTrue(
            result.output.contains("publishVariantPaidJsJsPublicationToMavenLocal"),
            "Expected publishVariantPaidJsJsPublicationToMavenLocal task:\n${result.output}",
        )
        assertTrue(
            result.output.contains("publishMatrix JS-family: registered"),
            "Expected per-variant JS-family publishing telemetry:\n${result.output}",
        )
    }

    @Test
    fun `JS-family configurator registers per-variant tasks for wasmJs target alongside js`() {
        buildFile.writeText(
            """
            import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
            plugins {
                kotlin("multiplatform") version "2.2.21"
                `maven-publish`
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                @OptIn(ExperimentalWasmDsl::class)
                wasmJs { nodejs() }
            }
            kmpFlavors {
                buildMatrix.set(true)
                publishMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("zipPaidKotlinWasmJs"),
            "Expected zipPaidKotlinWasmJs registered for wasmJs target:\n${result.output}",
        )
        assertTrue(
            result.output.contains("publishVariantPaidWasmJsJsPublicationToMavenLocal"),
            "Expected publishVariantPaidWasmJsJsPublicationToMavenLocal task:\n${result.output}",
        )
    }

    @Test
    fun `configurators are no-ops when publishMatrix is off even if iOS+JS targets exist`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                `maven-publish`
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                iosX64()
                js(IR) { nodejs() }
            }
            kmpFlavors {
                autoEnable.set(false)
                buildMatrix.set(true)
                // publishMatrix unset; autoEnable=false suppresses Phase 0B auto-enable so
                // the test exercises strict v2.0/v2.1 opt-in semantics.
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertTrue(
            !result.output.contains("zipPaidKotlinIosX64"),
            "No per-variant iOS Zip should register when publishMatrix is off:\n${result.output}",
        )
        assertTrue(
            !result.output.contains("zipPaidKotlinJs"),
            "No per-variant JS Zip should register when publishMatrix is off:\n${result.output}",
        )
    }
}
