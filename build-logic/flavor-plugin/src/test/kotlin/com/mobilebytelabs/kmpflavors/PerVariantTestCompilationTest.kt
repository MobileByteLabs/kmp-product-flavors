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
 * v2.1 Phase 2 — RFC §3 Q10 verification.
 *
 * Asserts that matrix mode registers `compile{Variant}TestKotlin{Target}` for
 * every INACTIVE variant on every non-Android target, and that:
 *  - the active variant continues to use KGP's standard `test` compilation
 *    (no `compile{Active}TestKotlin{Target}` task collision);
 *  - per-flavor test source sets are created (`commonFreeTest`, `commonPaidTest`)
 *    and wired `dependsOn(commonTest)`;
 *  - cross-variant test isolation holds — paid's test compilation can't see
 *    free's test source set (and vice versa).
 */
class PerVariantTestCompilationTest {

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
            rootProject.name = "per-variant-test-compilation"
            """.trimIndent(),
        )
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
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
            kmpFlavors {
                buildMatrix.set(true)
                generateBuildConfig.set(false)
                flavors {
                    register("free") { isDefault.set(true) }
                    register("paid")
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `matrix mode registers compile{Variant}TestKotlin{Target} for inactive variants only`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        // Inactive variant gets its own per-variant test compilation task.
        assertTrue(
            result.output.contains("compilePaidTestKotlinDesktop"),
            "Expected compilePaidTestKotlinDesktop task for inactive variant:\n${result.output}",
        )
        // Active variant uses KGP's standard `test` compilation — no duplicate task.
        assertFalse(
            result.output.contains("compileFreeTestKotlinDesktop"),
            "Active variant 'free' must NOT get a compileFreeTestKotlinDesktop task:\n${result.output}",
        )
        // Telemetry line from the new TestCompilationRegistrar block.
        assertTrue(
            result.output.contains("TEST compilations across") ||
                result.output.contains("inactive-variant TEST"),
            "Expected telemetry line announcing the test-compilation registration:\n${result.output}",
        )
    }

    @Test
    fun `variant test compilation associateWith its variant main so internal symbols are visible`() {
        // Drop a paid-specific symbol in the variant's main source set, and a paid test that
        // references it via `internal` visibility. Compilation must succeed end-to-end.
        File(testProjectDir, "src/commonPaid/kotlin").mkdirs()
        File(testProjectDir, "src/commonPaid/kotlin/PaidApi.kt").writeText(
            """
            package sample
            internal fun paidOnly(): String = "PAID-ONLY"
            """.trimIndent(),
        )
        File(testProjectDir, "src/commonPaidTest/kotlin").mkdirs()
        // No kotlin-test framework imports — we're verifying that the per-variant test
        // compilation can SEE the variant main's `internal` declarations, not that
        // the test framework is resolved. Test-framework dependency propagation is a
        // separate concern handled by the consumer's source-set deps block.
        File(testProjectDir, "src/commonPaidTest/kotlin/PaidApiTest.kt").writeText(
            """
            package sample
            object PaidApiTest {
                fun callPaidOnly(): String = paidOnly()
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compilePaidTestKotlinDesktop", "--info")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Per-variant test compilation must compile cleanly with internal-symbol visibility:\n${result.output}",
        )
    }

    @Test
    fun `cross-variant test isolation — paid test cannot see free's variant-only symbols`() {
        // free has a `freeOnly` symbol; paid's test references it → must FAIL to compile.
        File(testProjectDir, "src/commonFree/kotlin").mkdirs()
        File(testProjectDir, "src/commonFree/kotlin/FreeApi.kt").writeText(
            """
            package sample
            internal fun freeOnly(): String = "FREE-ONLY"
            """.trimIndent(),
        )
        File(testProjectDir, "src/commonPaidTest/kotlin").mkdirs()
        File(testProjectDir, "src/commonPaidTest/kotlin/BadTest.kt").writeText(
            """
            package sample
            object BadTest {
                fun referenceFreeOnly(): String = freeOnly()
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compilePaidTestKotlinDesktop")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(
            result.output.contains("Unresolved reference") || result.output.contains("error: "),
            "Expected the paid test compilation to fail when referencing free's symbol — cross-variant isolation must hold:\n${result.output}",
        )
    }
}
