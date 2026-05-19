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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Regression test for [issue #99](https://github.com/MobileByteLabs/kmp-product-flavors/issues/99):
 *
 * > Matrix mode: inactive-variant compilations don't see target-level source sets,
 * > breaking expect/actual in commonMain.
 *
 * The bug: `CompilationRegistrar.register()` wires inactive-variant
 * `defaultSourceSet` to per-flavor source sets only (`commonFree`, `commonProd`).
 * Those chain to `commonMain` via standard KMP wiring. But the variant's
 * `defaultSourceSet` never includes the target's `<target>Main` source set
 * (e.g. `desktopMain`), so `expect` declarations in `commonMain` cannot resolve
 * to their `actual` in `<target>Main` from the inactive-variant compilation's
 * perspective.
 *
 * Real-world reproducer: `openMF/kmp-project-template` `core:database` module
 * with `expect val platformModule: Module` in commonMain + actuals in
 * `desktopMain` / `iosMain` / etc. Every inactive-variant compilation
 * (`compileDemoReleaseKotlinDesktop`, etc.) fails with "Expected platformModule
 * has no actual declaration in module <commonMain> for JVM".
 *
 * The fix: `CompilationRegistrar` also wires the target's `main` compilation's
 * `defaultSourceSet` as a `dependsOn` parent of each inactive-variant's
 * `defaultSourceSet`. `TestCompilationRegistrar` parallels this for the `test`
 * compilation.
 */
class MatrixModeExpectActualTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeSettings(name: String = "expect-actual-test") {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            rootProject.name = "$name"
            """.trimIndent(),
        )
    }

    /**
     * The headline scenario from issue #99: matrix mode + `expect`/`actual` in
     * commonMain/desktopMain → every inactive-variant compilation must compile clean.
     */
    @Test
    fun `inactive-variant compilations resolve expect actual declarations from target main source sets`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
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

        // Reproduce the issue: expect in commonMain, actual in desktopMain.
        // Both `free` (active) and `paid` (inactive) compilations must see the actual.
        val commonMainDir = File(projectDir, "src/commonMain/kotlin/sample").apply { mkdirs() }
        File(commonMainDir, "Platform.kt").writeText(
            """
            package sample
            expect val platformName: String
            expect fun describe(): String
            """.trimIndent(),
        )
        val desktopMainDir = File(projectDir, "src/desktopMain/kotlin/sample").apply { mkdirs() }
        File(desktopMainDir, "PlatformJvm.kt").writeText(
            """
            package sample
            actual val platformName: String = "JVM"
            actual fun describe(): String = "Running on " + platformName
            """.trimIndent(),
        )

        // Compile the INACTIVE variant — this is the failure mode in the issue.
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("compilePaidKotlinDesktop", "--no-configuration-cache")
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":compilePaidKotlinDesktop")!!.outcome,
            "Inactive-variant compilation must succeed even when commonMain holds expect declarations. " +
                "Pre-fix this failed with 'Expected platformName has no actual declaration in module <commonMain> for JVM'.",
        )
        assertFalse(
            result.output.contains("has no actual declaration"),
            "Output must not contain the expect/actual mismatch error. Got:\n${result.output}",
        )
    }

    /**
     * The same fix must apply to the test-compilation registrar — inactive-variant
     * test compilations must also see the target's `<target>Test` source set so
     * expect-shaped test fixtures resolve.
     */
    @Test
    fun `inactive-variant test compilations resolve expect actual test fixtures from target test source sets`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
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
                sourceSets {
                    val commonTest by getting {
                        dependencies {
                            implementation(kotlin("test"))
                        }
                    }
                }
            }
            """.trimIndent(),
        )

        // expect/actual in test source sets.
        val commonTestDir = File(projectDir, "src/commonTest/kotlin/sample").apply { mkdirs() }
        File(commonTestDir, "TestFixture.kt").writeText(
            """
            package sample
            expect fun testHelper(): String
            """.trimIndent(),
        )
        val desktopTestDir = File(projectDir, "src/desktopTest/kotlin/sample").apply { mkdirs() }
        File(desktopTestDir, "TestFixtureJvm.kt").writeText(
            """
            package sample
            actual fun testHelper(): String = "jvm-test"
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("compilePaidTestKotlinDesktop", "--no-configuration-cache")
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":compilePaidTestKotlinDesktop")!!.outcome,
            "Inactive-variant test compilation must see the target's <target>Test source set so " +
                "expect-shaped test fixtures resolve.",
        )
        assertFalse(
            result.output.contains("has no actual declaration"),
            "Output must not contain the expect/actual mismatch error. Got:\n${result.output}",
        )
    }
}
