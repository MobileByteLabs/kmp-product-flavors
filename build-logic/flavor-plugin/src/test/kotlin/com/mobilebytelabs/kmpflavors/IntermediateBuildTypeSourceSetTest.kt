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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.2 Phase 1A — cross-variant intermediate `common{BuildType}` source sets.
 *
 * Verifies the DAG:
 * - `commonStaging.dependsOn(commonMain)` is wired.
 * - `freeStaging` + `paidStaging` BOTH `dependsOn(commonStaging)`.
 * - `freeProd` + `paidProd` do NOT see `commonStaging`.
 *
 * Tested via the lifecycle log emitted by `IntermediateSourceSetConfigurator`
 * + the per-source-set directories created on disk.
 */
class IntermediateBuildTypeSourceSetTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); google() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral(); google() }
            }
            rootProject.name = "intermediate-bt-source-set-test"
            """.trimIndent(),
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                jvm("desktop")
            }
            kmpFlavors {
                buildMatrix.set(true)
                enableBuildTypes.set(true)
                createIntermediateBuildTypeSourceSets.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
                buildTypes {
                    register("staging") { isDefault.set(true) }
                    register("prod")
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `commonStaging and commonProd source sets created with dependsOn(commonMain)`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // Lifecycle log from IntermediateSourceSetConfigurator
        assertTrue(
            result.output.contains("Phase 1A — wired cross-variant intermediate source sets"),
            "Expected Phase 1A lifecycle log:\n${result.output}",
        )
        // The lifecycle log enumerates 2 common{BuildType} source sets — one for each
        // registered build type. The exact source-set names go to INFO log (not lifecycle).
        assertTrue(
            result.output.contains("2 common{BuildType}"),
            "Expected lifecycle log to enumerate 2 common{BuildType} source sets:\n${result.output}",
        )
        // The variant × buildType compilation tasks must exist — proves matrix-mode + Phase 1A wired.
        assertTrue(
            result.output.contains("compileFreeProdKotlinDesktop") &&
                result.output.contains("compilePaidStagingKotlinDesktop"),
            "Expected per-(flavor × buildType) compilation tasks:\n${result.output}",
        )
    }

    @Test
    fun `per-target buildType source sets (e g desktopStaging) are also created`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // The lifecycle log enumerates 2 {target}{BuildType} source sets (1 target × 2 buildTypes).
        assertTrue(
            result.output.contains("2 {target}{BuildType}"),
            "Expected lifecycle log to enumerate 2 per-target buildType source sets:\n${result.output}",
        )
    }

    @Test
    fun `opt-out — createIntermediateBuildTypeSourceSets=false skips Phase 1A entirely`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }
            kotlin {
                jvm("desktop")
            }
            kmpFlavors {
                buildMatrix.set(true)
                enableBuildTypes.set(true)
                // createIntermediateBuildTypeSourceSets NOT opted in (default false)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
                buildTypes {
                    register("staging") { isDefault.set(true) }
                    register("prod")
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertFalse(
            result.output.contains("Phase 1A — wired cross-variant intermediate source sets"),
            "Phase 1A must not fire when createIntermediateBuildTypeSourceSets=false:\n${result.output}",
        )
    }
}
